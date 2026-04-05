package ru.kinoko.kinchat.dto

import java.util.UUID

data class AuthenticatedUser(
    val userId: UUID,
    val login: String,
)
