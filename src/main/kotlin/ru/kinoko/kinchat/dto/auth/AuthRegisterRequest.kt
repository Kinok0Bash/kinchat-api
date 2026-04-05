package ru.kinoko.kinchat.dto.auth

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Запрос на регистрацию пользователя")
data class AuthRegisterRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 32)
    @field:Pattern(regexp = "^[A-Za-z0-9._-]+$")
    @field:Schema(example = "ivan.petrov")
    val login: String,

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    @field:Schema(example = "Sup3rStrong!")
    val password: String,

    @field:NotBlank
    @field:Size(max = 60)
    @field:Schema(example = "Иван")
    val firstName: String,

    @field:NotBlank
    @field:Size(max = 60)
    @field:Schema(example = "Петров")
    val lastName: String,
)
