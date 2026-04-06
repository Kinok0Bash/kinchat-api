package ru.kinoko.kinchat.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import ru.kinoko.kinchat.dto.chat.MessageResponse
import ru.kinoko.kinchat.dto.ws.WsConnectionReadyPayload
import ru.kinoko.kinchat.dto.ws.WsErrorPayload
import ru.kinoko.kinchat.dto.ws.WsOutgoingEnvelope
import ru.kinoko.kinchat.repository.ChatRepository
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Service
class RealtimeService(
    private val chatRepository: ChatRepository,
    private val objectMapper: ObjectMapper,
) {
    private val sessionsByUserId = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    private val userIdBySessionId = ConcurrentHashMap<String, UUID>()

    fun registerSession(userId: UUID, session: WebSocketSession) {
        sessionsByUserId.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
        userIdBySessionId[session.id] = userId
        logger.info(
            "Registered websocket session sessionId={} userId={} activeSessionsForUser={}",
            session.id,
            userId,
            sessionsByUserId[userId]?.size ?: 0,
        )
    }

    fun unregisterSession(session: WebSocketSession) {
        val userId = userIdBySessionId.remove(session.id) ?: run {
            logger.info("Skipping websocket session unregister because sessionId={} is unknown", session.id)
            return
        }
        sessionsByUserId[userId]?.let { sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) {
                sessionsByUserId.remove(userId)
            }
        }
        logger.info(
            "Unregistered websocket session sessionId={} userId={} remainingSessionsForUser={}",
            session.id,
            userId,
            sessionsByUserId[userId]?.size ?: 0,
        )
    }

    fun sendConnectionReady(session: WebSocketSession, login: String) {
        logger.info("Sending websocket connection.ready sessionId={} login={}", session.id, login)
        sendEvent(
            session = session,
            event = CONNECTION_READY_EVENT,
            data = WsConnectionReadyPayload(login, OffsetDateTime.now()),
        )
    }

    fun sendPong(session: WebSocketSession) {
        logger.info("Sending websocket pong sessionId={}", session.id)
        sendEvent(session, PONG_EVENT, emptyMap<String, Any>())
    }

    fun sendProtocolError(session: WebSocketSession, requestId: String?, code: String, message: String) {
        logger.info(
            "Sending websocket protocol error sessionId={} requestId={} code={} message={}",
            session.id,
            requestId,
            code,
            message,
        )
        sendEvent(
            session = session,
            event = ERROR_EVENT,
            requestId = requestId,
            data = WsErrorPayload(code = code, message = message),
        )
    }

    fun sendMessageCreated(session: WebSocketSession, message: MessageResponse) {
        logger.info(
            "Sending websocket message.created sessionId={} chatId={} messageId={}",
            session.id,
            message.chatId,
            message.messageId,
        )
        sendEvent(session, MESSAGE_CREATED_EVENT, message)
    }

    fun publishMessageCreated(chatId: UUID, message: MessageResponse) {
        val participantUserIds = chatRepository.findParticipantUserIds(chatId)
        val sessions = participantUserIds.flatMapTo(linkedSetOf()) { userId ->
            sessionsByUserId[userId]?.toList().orEmpty()
        }
        logger.info(
            "Publishing websocket message.created chatId={} messageId={} participants={} sessions={}",
            chatId,
            message.messageId,
            participantUserIds.size,
            sessions.size,
        )
        sessions.forEach { session -> sendMessageCreated(session, message) }
    }

    private fun sendEvent(
        session: WebSocketSession,
        event: String,
        data: Any,
        requestId: String? = null,
    ) {
        if (!session.isOpen) {
            logger.info(
                "Skipping websocket send because sessionId={} is already closed event={}",
                session.id,
                event,
            )
            unregisterSession(session)
            return
        }

        val payload = objectMapper.writeValueAsString(
            WsOutgoingEnvelope(event = event, requestId = requestId, data = data),
        )

        runCatching {
            session.sendMessage(TextMessage(payload))
            logger.info(
                "Websocket event sent sessionId={} event={} requestId={}",
                session.id,
                event,
                requestId,
            )
        }.onFailure { exception ->
            logger.info(
                "Websocket send failed sessionId={} event={} requestId={} cause={}",
                session.id,
                event,
                requestId,
                exception.javaClass.simpleName,
            )
            unregisterSession(session)
        }
    }

    companion object {
        private const val CONNECTION_READY_EVENT = "connection.ready"
        private const val ERROR_EVENT = "error"
        private const val MESSAGE_CREATED_EVENT = "message.created"
        private const val PONG_EVENT = "pong"
        private val logger = LoggerFactory.getLogger(RealtimeService::class.java)
    }
}
