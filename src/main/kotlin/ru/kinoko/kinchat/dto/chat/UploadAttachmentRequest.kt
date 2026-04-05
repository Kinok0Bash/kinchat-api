package ru.kinoko.kinchat.dto.chat

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Schema(description = "Multipart запрос на загрузку вложения")
data class UploadAttachmentRequest(
    @field:Schema(type = "string", format = "binary")
    val file: MultipartFile? = null,

    @field:Schema(nullable = true)
    val text: String? = null,

    @field:Schema(format = "uuid", nullable = true)
    val clientMessageId: UUID? = null,
)
