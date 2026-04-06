package ru.kinoko.kinchat.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import ru.kinoko.kinchat.dto.ws.WsIncomingEnvelope
import ru.kinoko.kinchat.dto.ws.WsMessageSendPayload
import ru.kinoko.kinchat.exception.ForbiddenException
import ru.kinoko.kinchat.exception.NotFoundException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.service.ChatService
import ru.kinoko.kinchat.service.RealtimeService
import ru.kinoko.kinchat.service.UserService
import ru.kinoko.kinchat.service.WsTicketService
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

@Component
@Suppress("TooManyFunctions")
class KinchatWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val wsTicketService: WsTicketService,
    private val chatService: ChatService,
    private val realtimeService: RealtimeService,
    private val userService: UserService,
) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.info(
            "Websocket connection attempt sessionId={} path={}",
            session.id,
            session.uri?.path,
        )
        val ticket = extractTicket(session) ?: run {
            log.info(
                "Websocket connection rejected because ticket is missing or invalid sessionId={}",
                session.id,
            )
            closeSession(session, CloseStatus.POLICY_VIOLATION, "Missing or invalid ws ticket")
            return
        }

        val userId = wsTicketService.consumeTicket(ticket) ?: run {
            log.info(
                "Websocket connection rejected because ticket is expired or already used sessionId={}",
                session.id,
            )
            closeSession(session, CloseStatus.POLICY_VIOLATION, "Expired or already used ws ticket")
            return
        }

        val login = runCatching { userService.getPublicUserProjectionById(userId).login }
            .getOrElse { exception ->
                log.error(
                    "Websocket connection failed while resolving user sessionId={} userId={}",
                    session.id,
                    userId,
                    exception,
                )
                closeSession(session, CloseStatus.SERVER_ERROR, "Failed to initialize websocket session")
                return
            }

        session.attributes[SESSION_USER_ID_ATTRIBUTE] = userId
        realtimeService.registerSession(userId, session)
        realtimeService.sendConnectionReady(session, login)
        log.info(
            "Websocket connection established sessionId={} userId={} login={}",
            session.id,
            userId,
            login,
        )
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        log.info(
            "Websocket text frame received sessionId={} payloadLength={}",
            session.id,
            message.payload.length,
        )
        val envelope = parseEnvelope(session, message.payload) ?: return
        log.info(
            "Websocket event received sessionId={} event={} requestId={}",
            session.id,
            envelope.event,
            envelope.requestId,
        )

        when (envelope.event) {
            MESSAGE_SEND_EVENT -> handleMessageSend(session, envelope)
            PING_EVENT -> {
                log.info("Websocket ping received sessionId={}", session.id)
                realtimeService.sendPong(session)
            }
            null, "" -> {
                log.info("Websocket event rejected because event name is empty sessionId={}", session.id)
                sendProtocolError(session, envelope.requestId, VALIDATION_ERROR_CODE, "Event is required")
            }
            else -> {
                log.info(
                    "Websocket event rejected because event={} is unsupported sessionId={}",
                    envelope.event,
                    session.id,
                )
                sendProtocolError(
                    session,
                    envelope.requestId,
                    VALIDATION_ERROR_CODE,
                    "Unsupported event ${envelope.event}",
                )
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        log.info(
            "Websocket connection closed sessionId={} code={} reason={}",
            session.id,
            status.code,
            status.reason,
        )
        realtimeService.unregisterSession(session)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.info(
            "Websocket transport error sessionId={} open={} cause={}",
            session.id,
            session.isOpen,
            exception.javaClass.simpleName,
        )
        if (!session.isOpen) {
            realtimeService.unregisterSession(session)
        }
    }

    private fun handleMessageSend(session: WebSocketSession, envelope: WsIncomingEnvelope) {
        val command = resolveMessageSendCommand(session, envelope) ?: return
        log.info(
            "Processing websocket message.send sessionId={} userId={} chatId={} clientMessageId={} textLength={}",
            session.id,
            command.userId,
            command.chatId,
            command.clientMessageId,
            command.text.length,
        )
        try {
            val result = chatService.sendTextMessage(
                userId = command.userId,
                chatId = command.chatId,
                clientMessageId = command.clientMessageId,
                text = command.text,
            )
            log.info(
                "Websocket message.send processed sessionId={} chatId={} messageId={} created={}",
                session.id,
                command.chatId,
                result.message.messageId,
                result.created,
            )
            if (!result.created) {
                realtimeService.sendMessageCreated(session, result.message)
            }
        } catch (exception: ValidationException) {
            handleMessageSendValidationError(session, envelope.requestId, command, exception)
        } catch (_: ForbiddenException) {
            log.info(
                "Websocket message.send rejected because chat access is forbidden sessionId={} chatId={}",
                session.id,
                command.chatId,
            )
            sendProtocolError(session, envelope.requestId, FORBIDDEN_CHAT_ACCESS_CODE, "Chat access denied")
        } catch (_: NotFoundException) {
            log.info(
                "Websocket message.send rejected because chat was not found sessionId={} chatId={}",
                session.id,
                command.chatId,
            )
            sendProtocolError(session, envelope.requestId, FORBIDDEN_CHAT_ACCESS_CODE, "Chat access denied")
        } catch (exception: Exception) {
            log.error(
                "Websocket message.send failed with internal error sessionId={} chatId={}",
                session.id,
                command.chatId,
                exception,
            )
            sendProtocolError(session, envelope.requestId, INTERNAL_ERROR_CODE, "Internal server error")
        }
    }

    @Suppress("ReturnCount")
    private fun resolveMessageSendCommand(
        session: WebSocketSession,
        envelope: WsIncomingEnvelope,
    ): MessageSendCommand? {
        val userId = resolveSessionUserId(session, envelope.requestId) ?: return null
        val payload = parseMessageSendPayload(session, envelope) ?: return null
        val chatId = payload.chatId ?: run {
            log.info("Websocket message.send rejected because chatId is missing sessionId={}", session.id)
            sendProtocolError(session, envelope.requestId, VALIDATION_ERROR_CODE, "chatId is required")
            return null
        }
        val clientMessageId = payload.clientMessageId ?: run {
            log.info(
                "Websocket message.send rejected because clientMessageId is missing sessionId={}",
                session.id,
            )
            sendProtocolError(session, envelope.requestId, VALIDATION_ERROR_CODE, "clientMessageId is required")
            return null
        }

        return MessageSendCommand(
            userId = userId,
            chatId = chatId,
            clientMessageId = clientMessageId,
            text = payload.text.orEmpty(),
        )
    }

    private fun resolveSessionUserId(session: WebSocketSession, requestId: String?): UUID? {
        val userId = session.attributes[SESSION_USER_ID_ATTRIBUTE] as? UUID
        if (userId == null) {
            log.info("Websocket message.send rejected because session is unauthorized sessionId={}", session.id)
            sendProtocolError(session, requestId, UNAUTHORIZED_CODE, "Websocket session is not authorized")
        }
        return userId
    }

    private fun parseMessageSendPayload(
        session: WebSocketSession,
        envelope: WsIncomingEnvelope,
    ): WsMessageSendPayload? = try {
        objectMapper.treeToValue(envelope.data, WsMessageSendPayload::class.java)
    } catch (exception: Exception) {
        log.info(
            "Websocket message.send rejected because payload is invalid sessionId={} cause={}",
            session.id,
            exception.javaClass.simpleName,
        )
        sendProtocolError(session, envelope.requestId, VALIDATION_ERROR_CODE, "Invalid message.send payload")
        null
    }

    private fun handleMessageSendValidationError(
        session: WebSocketSession,
        requestId: String?,
        command: MessageSendCommand,
        exception: ValidationException,
    ) {
        val code = if (command.text.trim().isEmpty()) MESSAGE_EMPTY_CODE else VALIDATION_ERROR_CODE
        log.info(
            "Websocket message.send rejected by validation sessionId={} chatId={} code={} reason={}",
            session.id,
            command.chatId,
            code,
            exception.message,
        )
        sendProtocolError(session, requestId, code, exception.message)
    }

    private fun parseEnvelope(session: WebSocketSession, payload: String): WsIncomingEnvelope? = try {
        objectMapper.readValue(payload, WsIncomingEnvelope::class.java)
    } catch (exception: Exception) {
        log.info(
            "Websocket payload rejected because envelope parsing failed sessionId={} cause={}",
            session.id,
            exception.javaClass.simpleName,
        )
        sendProtocolError(session, null, VALIDATION_ERROR_CODE, "Invalid websocket payload")
        null
    }

    private fun extractTicket(session: WebSocketSession): UUID? = session.uri
        ?.rawQuery
        ?.split('&')
        ?.asSequence()
        ?.mapNotNull { entry ->
            val index = entry.indexOf('=')
            if (index <= 0) {
                null
            } else {
                entry.substring(0, index) to URLDecoder.decode(entry.substring(index + 1), StandardCharsets.UTF_8)
            }
        }
        ?.firstOrNull { (name, _) -> name == TICKET_QUERY_PARAM }
        ?.second
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun closeSession(session: WebSocketSession, status: CloseStatus, reason: String) {
        log.info(
            "Closing websocket session sessionId={} code={} reason={}",
            session.id,
            status.code,
            reason,
        )
        runCatching {
            session.close(status.withReason(reason.take(MAX_CLOSE_REASON_LENGTH)))
        }
        realtimeService.unregisterSession(session)
    }

    private fun sendProtocolError(session: WebSocketSession, requestId: String?, code: String, message: String) {
        realtimeService.sendProtocolError(session, requestId, code, message)
    }

    private data class MessageSendCommand(
        val userId: UUID,
        val chatId: UUID,
        val clientMessageId: UUID,
        val text: String,
    )

    companion object {
        private const val FORBIDDEN_CHAT_ACCESS_CODE = "FORBIDDEN_CHAT_ACCESS"
        private const val INTERNAL_ERROR_CODE = "INTERNAL_ERROR"
        private const val MAX_CLOSE_REASON_LENGTH = 120
        private const val MESSAGE_EMPTY_CODE = "MESSAGE_EMPTY"
        private const val MESSAGE_SEND_EVENT = "message.send"
        private const val PING_EVENT = "ping"
        private const val SESSION_USER_ID_ATTRIBUTE = "userId"
        private const val TICKET_QUERY_PARAM = "ticket"
        private const val UNAUTHORIZED_CODE = "UNAUTHORIZED"
        private const val VALIDATION_ERROR_CODE = "VALIDATION_ERROR"
        private val log = LoggerFactory.getLogger(KinchatWebSocketHandler::class.java)
    }
}
