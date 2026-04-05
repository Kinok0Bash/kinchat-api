package ru.kinoko.kinchat.dto.ws

data class WsOutgoingEnvelope(
    val event: String,
    val requestId: String? = null,
    val data: Any,
)
