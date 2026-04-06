package ru.kinoko.kinchat.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.kinoko.kinchat.dto.common.ErrorResponse
import ru.kinoko.kinchat.dto.ws.WsTicketResponse
import ru.kinoko.kinchat.security.CurrentUserProvider
import ru.kinoko.kinchat.service.WsTicketService

@RestController
@RequestMapping("/api/ws/tickets")
@Tag(name = "WebSocket", description = "Выдача одноразового ticket для websocket")
@SecurityRequirement(name = "bearerAuth")
class WsTicketController(
    private val wsTicketService: WsTicketService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping
    @Operation(summary = "Выдать ws-ticket")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Тикет выдан",
                content = [Content(schema = Schema(implementation = WsTicketResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Требуется авторизация",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ],
    )
    fun issueTicket(request: HttpServletRequest): ResponseEntity<WsTicketResponse> {
        val currentUser = currentUserProvider.getCurrentUser()
        logger.info("HTTP websocket ticket request received userId={}", currentUser.userId)
        val ticketResponse = wsTicketService.issueTicket(currentUser.userId, request)
        logger.info(
            "HTTP websocket ticket request completed userId={} expiresAt={}",
            currentUser.userId,
            ticketResponse.expiresAt,
        )
        return ResponseEntity.ok(ticketResponse)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WsTicketController::class.java)
    }
}
