package ru.kinoko.kinchat.service

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.kinoko.kinchat.dto.ws.WsTicketResponse
import ru.kinoko.kinchat.properties.AppProperties
import ru.kinoko.kinchat.repository.WsTicketRepository
import ru.kinoko.kinchat.util.isForwardedProto
import java.time.OffsetDateTime
import java.util.UUID

@Service
class WsTicketService(
    private val wsTicketRepository: WsTicketRepository,
    private val appProperties: AppProperties,
) {
    fun issueTicket(userId: UUID, request: HttpServletRequest): WsTicketResponse {
        logger.info("Issuing websocket ticket userId={}", userId)
        val expiresAt = OffsetDateTime.now().plus(appProperties.auth.wsTicketLifeTime)
        val ticket = wsTicketRepository.createWsTicket(userId, expiresAt)
        val wsUrl = buildWsUrl(request, ticket)

        val ticketResponse = WsTicketResponse(
            ticket = ticket,
            expiresAt = expiresAt,
            wsUrl = wsUrl,
        )
        logger.info(
            "Websocket ticket issued userId={} expiresAt={} wsEndpoint={}",
            userId,
            expiresAt,
            wsUrl.substringBefore('?'),
        )
        return ticketResponse
    }

    fun consumeTicket(ticket: UUID): UUID? {
        logger.info("Consuming websocket ticket")
        val userId = wsTicketRepository.consumeTicket(ticket, OffsetDateTime.now())
        logger.info("Websocket ticket consumption completed success={} userId={}", userId != null, userId)
        return userId
    }

    private fun buildWsUrl(request: HttpServletRequest, ticket: UUID): String {
        val protoHeader = request.getHeader("X-Forwarded-Proto")
        val forwardedProto = protoHeader?.substringBefore(',')?.trim()
        val scheme = when {
            forwardedProto.isForwardedProto("https") -> "wss"
            forwardedProto.isForwardedProto("http") -> "ws"
            request.scheme.equals("https", ignoreCase = true) -> "wss"
            else -> "ws"
        }
        val host = request.getHeader("X-Forwarded-Host")
            ?.substringBefore(',')
            ?.trim()
            ?: request.getHeader("Host")
            ?: (request.serverName + portSegment(request.serverPort, scheme))

        logger.info("Resolved websocket endpoint scheme={} host={}", scheme, host)
        return "$scheme://$host/ws?ticket=$ticket"
    }

    private fun portSegment(port: Int, scheme: String): String = when {
        scheme == "ws" && port == HTTP_PORT -> ""
        scheme == "wss" && port == HTTPS_PORT -> ""
        else -> ":$port"
    }

    companion object {
        private const val HTTP_PORT = 80
        private const val HTTPS_PORT = 443
        private val logger = LoggerFactory.getLogger(WsTicketService::class.java)
    }
}
