package ru.kinoko.kinchat.dto.chat

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Страница сообщений")
data class PagedMessagesResponse(
    @field:ArraySchema(schema = Schema(implementation = MessageResponse::class))
    val items: List<MessageResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
