package ru.kinoko.kinchat.websocket

import com.fasterxml.jackson.databind.ObjectMapper
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
class KinchatWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val wsTicketService: WsTicketService,
    private val chatService: ChatService,
    private val realtimeService: RealtimeService,
    private val userService: UserService,
) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val ticket = extractTicket(session) ?: run {
            closeSession(session, CloseStatus.POLICY_VIOLATION, "Missing or invalid ws ticket")
            return
        }

        val userId = wsTicketService.consumeTicket(ticket) ?: run {
            closeSession(session, CloseStatus.POLICY_VIOLATION, "Expired or already used ws ticket")
            return
        }

        val login = runCatching { userService.getPublicUserProjectionById(userId).login }
            .getOrElse {
                closeSession(session, CloseStatus.SERVER_ERROR, "Failed to initialize websocket session")
                return
            }

        session.attributes[SESSION_USER_ID_ATTRIBUTE] = userId
        realtimeService.registerSession(userId, session)
        realtimeService.sendConnectionReady(session, login)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val envelope = parseEnvelope(session, message.payload) ?: return

        when (envelope.event) {
            MESSAGE_SEND_EVENT -> handleMessageSend(session, envelope)
            PING_EVENT -> realtimeService.sendPong(session)
            null, "" -> sendProtocolError(session, envelope.requestId, VALIDATION_ERROR_CODE, "Event is required")
            else -> sendProtocolError(
                session,
                envelope.requestId,
                VALIDATION_ERROR_CODE,
                "Unsupported event ${envelope.event}",
            )
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        realtimeService.unregisterSession(session)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        if (!session.isOpen) {
            realtimeService.unregisterSession(session)
        }
    }

    private fun handleMessageSend(session: WebSocketSession, envelope: WsIncomingEnvelope) {
        val userId = session.attributes[SESSION_USER_ID_ATTRIBUTE] as? UUID ?: run {
            sendProtocolError(session, envelope.requestId, UNAUTHORIZED_CODE, "Websocket session is not authorized")
            return
        }
        val payload = try {
            objectMapper.treeToValue(envelope.data, WsMessageSendPayload::class.java)
        } catch (_: Exception) {
            sendProtocolError(session, envelope.requestId, VALIDATION_ERROR_CODE, "Invalid message.send payload")
            return
        }
        val chatId = payload.chatId ?: run {
            sendProtocolError(session, envelope.requestId, VALIDATION_ERROR_CODE, "chatId is required")
            return
        }
        val clientMessageId = payload.clientMessageId ?: run {
            sendProtocolError(session, envelope.requestId, VALIDATION_ERROR_CODE, "clientMessageId is required")
            return
        }

        try {
            val result = chatService.sendTextMessage(
                userId = userId,
                chatId = chatId,
                clientMessageId = clientMessageId,
                text = payload.text.orEmpty(),
            )
            if (!result.created) {
                realtimeService.sendMessageCreated(session, result.message)
            }
        } catch (exception: ValidationException) {
            val code = if (payload.text.orEmpty().trim().isEmpty()) MESSAGE_EMPTY_CODE else VALIDATION_ERROR_CODE
            sendProtocolError(session, envelope.requestId, code, exception.message)
        } catch (_: ForbiddenException) {
            sendProtocolError(session, envelope.requestId, FORBIDDEN_CHAT_ACCESS_CODE, "Chat access denied")
        } catch (_: NotFoundException) {
            sendProtocolError(session, envelope.requestId, FORBIDDEN_CHAT_ACCESS_CODE, "Chat access denied")
        } catch (_: Exception) {
            sendProtocolError(session, envelope.requestId, INTERNAL_ERROR_CODE, "Internal server error")
        }
    }

    private fun parseEnvelope(session: WebSocketSession, payload: String): WsIncomingEnvelope? = try {
        objectMapper.readValue(payload, WsIncomingEnvelope::class.java)
    } catch (_: Exception) {
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
        runCatching {
            session.close(status.withReason(reason.take(MAX_CLOSE_REASON_LENGTH)))
        }
        realtimeService.unregisterSession(session)
    }

    private fun sendProtocolError(session: WebSocketSession, requestId: String?, code: String, message: String) {
        realtimeService.sendProtocolError(session, requestId, code, message)
    }

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
    }
}
