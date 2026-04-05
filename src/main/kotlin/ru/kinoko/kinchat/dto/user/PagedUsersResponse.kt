package ru.kinoko.kinchat.dto.user

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Страница пользователей")
data class PagedUsersResponse(
    @field:ArraySchema(schema = Schema(implementation = PublicUserResponse::class))
    val items: List<PublicUserResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
