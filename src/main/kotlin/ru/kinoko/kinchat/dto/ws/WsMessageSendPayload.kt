package ru.kinoko.kinchat.dto.ws

import java.util.UUID

data class WsMessageSendPayload(
    val chatId: UUID? = null,
    val clientMessageId: UUID? = null,
    val text: String? = null,
)
