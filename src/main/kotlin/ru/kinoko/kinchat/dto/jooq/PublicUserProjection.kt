package ru.kinoko.kinchat.dto.jooq

import java.util.UUID

data class PublicUserProjection(
    val userId: UUID,
    val login: String,
    val firstName: String,
    val lastName: String,
    val avatarUrl: String,
)
