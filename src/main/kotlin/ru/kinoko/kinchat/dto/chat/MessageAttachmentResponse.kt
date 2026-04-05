package ru.kinoko.kinchat.dto.chat

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Вложение сообщения")
data class MessageAttachmentResponse(
    @field:Schema(example = "photo.jpg")
    val fileName: String,

    @field:Schema(example = "image/jpeg")
    val contentType: String,

    @field:Schema(example = "934223")
    val sizeBytes: Long,

    @field:Schema(example = "https://cdn.example.com/message-files/photo.jpg")
    val url: String,
)
