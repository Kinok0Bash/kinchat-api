package ru.kinoko.kinchat.dto.chat

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Страница чатов")
data class PagedChatsResponse(
    @field:ArraySchema(schema = Schema(implementation = ChatSummaryResponse::class))
    val items: List<ChatSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
