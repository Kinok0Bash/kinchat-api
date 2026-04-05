package ru.kinoko.kinchat.dto.common

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Ошибка REST API")
data class ErrorResponse(
    @field:Schema(format = "date-time")
    val timestamp: OffsetDateTime,

    @field:Schema(example = "400")
    val status: Int,

    @field:Schema(example = "VALIDATION_ERROR")
    val code: String,

    @field:Schema(example = "Некорректный запрос")
    val message: String,

    @field:Schema(example = "/api/chats/direct")
    val path: String,

    @field:Schema(nullable = true)
    val traceId: String? = null,
)
