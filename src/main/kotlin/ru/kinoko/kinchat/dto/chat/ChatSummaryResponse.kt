package ru.kinoko.kinchat.dto.chat

import io.swagger.v3.oas.annotations.media.Schema
import ru.kinoko.kinchat.dto.user.PublicUserResponse
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Карточка direct chat")
data class ChatSummaryResponse(
    @field:Schema(format = "uuid")
    val chatId: UUID,

    val participant: PublicUserResponse,

    @field:Schema(nullable = true)
    val lastMessagePreview: String?,

    @field:Schema(nullable = true)
    val lastMessageType: MessageType?,

    @field:Schema(format = "date-time", nullable = true)
    val lastMessageAt: OffsetDateTime?,
)
