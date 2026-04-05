package ru.kinoko.kinchat.dto.jooq

import java.time.OffsetDateTime
import java.util.UUID

data class AuthUserProjection(
    val userId: UUID,
    val login: String,
    val loginLower: String,
    val passwordHash: String,
    val createdAt: OffsetDateTime,
)
