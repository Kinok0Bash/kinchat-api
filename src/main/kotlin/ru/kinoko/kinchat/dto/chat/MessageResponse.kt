package ru.kinoko.kinchat.dto.chat

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import ru.kinoko.kinchat.dto.user.PublicUserResponse
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Сообщение чата")
data class MessageResponse(
    @field:Schema(format = "uuid")
    val messageId: UUID,

    @field:Schema(format = "uuid")
    val chatId: UUID,

    val sender: PublicUserResponse,

    val type: MessageType,

    @field:Schema(nullable = true)
    val text: String?,

    @field:ArraySchema(schema = Schema(implementation = MessageAttachmentResponse::class))
    val attachments: List<MessageAttachmentResponse>,

    @field:Schema(format = "date-time")
    val createdAt: OffsetDateTime,

    @field:Schema(format = "uuid", nullable = true)
    val clientMessageId: UUID?,
)
