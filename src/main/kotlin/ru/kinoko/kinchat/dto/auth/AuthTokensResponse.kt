package ru.kinoko.kinchat.dto.auth

import io.swagger.v3.oas.annotations.media.Schema
import ru.kinoko.kinchat.dto.user.PublicUserResponse

@Schema(description = "Ответ после успешной регистрации, логина или refresh")
data class AuthTokensResponse(
    @field:Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String,

    @field:Schema(example = "900", description = "TTL access token в секундах")
    val expiresIn: Long,

    val user: PublicUserResponse,
)
