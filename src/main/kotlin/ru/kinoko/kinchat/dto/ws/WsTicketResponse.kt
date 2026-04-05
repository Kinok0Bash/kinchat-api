package ru.kinoko.kinchat.dto.ws

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Ответ на выдачу ws-ticket")
data class WsTicketResponse(
    @field:Schema(format = "uuid")
    val ticket: UUID,

    @field:Schema(format = "date-time")
    val expiresAt: OffsetDateTime,

    val wsUrl: String,
)
