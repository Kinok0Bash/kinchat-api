package ru.kinoko.kinchat.service

import com.fasterxml.jackson.databind.ObjectMapper
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
    }

    fun unregisterSession(session: WebSocketSession) {
        val userId = userIdBySessionId.remove(session.id) ?: return
        sessionsByUserId[userId]?.let { sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) {
                sessionsByUserId.remove(userId)
            }
        }
    }

    fun sendConnectionReady(session: WebSocketSession, login: String) {
        sendEvent(
            session = session,
            event = CONNECTION_READY_EVENT,
            data = WsConnectionReadyPayload(login, OffsetDateTime.now()),
        )
    }

    fun sendPong(session: WebSocketSession) {
        sendEvent(session, PONG_EVENT, emptyMap<String, Any>())
    }

    fun sendProtocolError(session: WebSocketSession, requestId: String?, code: String, message: String) {
        sendEvent(
            session = session,
            event = ERROR_EVENT,
            requestId = requestId,
            data = WsErrorPayload(code = code, message = message),
        )
    }

    fun sendMessageCreated(session: WebSocketSession, message: MessageResponse) {
        sendEvent(session, MESSAGE_CREATED_EVENT, message)
    }

    fun publishMessageCreated(chatId: UUID, message: MessageResponse) {
        val sessions = chatRepository.findParticipantUserIds(chatId)
            .flatMapTo(linkedSetOf()) { userId ->
                sessionsByUserId[userId]?.toList().orEmpty()
            }
        sessions.forEach { session -> sendMessageCreated(session, message) }
    }

    private fun sendEvent(
        session: WebSocketSession,
        event: String,
        data: Any,
        requestId: String? = null,
    ) {
        if (!session.isOpen) {
            unregisterSession(session)
            return
        }

        val payload = objectMapper.writeValueAsString(
            WsOutgoingEnvelope(event = event, requestId = requestId, data = data),
        )

        runCatching {
            session.sendMessage(TextMessage(payload))
        }.onFailure {
            unregisterSession(session)
        }
    }

    companion object {
        private const val CONNECTION_READY_EVENT = "connection.ready"
        private const val ERROR_EVENT = "error"
        private const val MESSAGE_CREATED_EVENT = "message.created"
        private const val PONG_EVENT = "pong"
    }
}
