package ru.kinoko.kinchat.dto.ws

import java.time.OffsetDateTime

data class WsConnectionReadyPayload(
    val login: String,
    val serverTime: OffsetDateTime,
)
