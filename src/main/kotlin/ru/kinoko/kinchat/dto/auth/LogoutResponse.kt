package ru.kinoko.kinchat.dto.auth

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Ответ на logout")
data class LogoutResponse(
    @field:Schema(example = "Выход выполнен успешно")
    val message: String,
)
