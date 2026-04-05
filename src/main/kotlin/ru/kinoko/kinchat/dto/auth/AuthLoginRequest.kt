package ru.kinoko.kinchat.dto.auth

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Запрос на логин пользователя")
data class AuthLoginRequest(
    @field:NotBlank
    @field:Schema(example = "ivan.petrov")
    val login: String,

    @field:NotBlank
    @field:Schema(example = "Sup3rStrong!")
    val password: String,
)
