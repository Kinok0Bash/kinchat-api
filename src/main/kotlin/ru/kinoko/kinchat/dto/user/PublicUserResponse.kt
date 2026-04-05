package ru.kinoko.kinchat.dto.user

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Публичный DTO пользователя")
data class PublicUserResponse(
    @field:Schema(example = "ivan.petrov")
    val login: String,

    @field:Schema(example = "Иван")
    val firstName: String,

    @field:Schema(example = "Петров")
    val lastName: String,

    @field:Schema(example = "https://cdn.example.com/avatars/ivan.petrov/a1b2c3.webp")
    val avatarUrl: String,
)
