package ru.kinoko.kinchat.dto.chat

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Запрос на создание direct chat")
data class CreateDirectChatRequest(
    @field:NotBlank
    @field:Schema(example = "anna.ivanova")
    val peerLogin: String,
)
