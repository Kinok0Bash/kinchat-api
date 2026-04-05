package ru.kinoko.kinchat.dto.jooq

import java.time.OffsetDateTime
import java.util.UUID

data class MessageProjection(
    val messageId: UUID,
    val chatId: UUID,
    val senderUserId: UUID,
    val clientMessageId: UUID?,
    val text: String?,
    val createdAt: OffsetDateTime,
)
