package ru.kinoko.kinchat.dto.ws

import com.fasterxml.jackson.databind.JsonNode

data class WsIncomingEnvelope(
    val event: String?,
    val requestId: String? = null,
    val data: JsonNode? = null,
)
