package ru.kinoko.kinchat.repository

import ru.kinoko.kinchat.dto.jooq.MessageProjection

data class PersistedMessage(
    val message: MessageProjection,
    val created: Boolean,
)
