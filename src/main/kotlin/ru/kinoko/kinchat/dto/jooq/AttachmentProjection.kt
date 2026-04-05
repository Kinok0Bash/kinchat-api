package ru.kinoko.kinchat.dto.jooq

import java.util.UUID

data class AttachmentProjection(
    val messageId: UUID,
    val attachmentNo: Int,
    val objectKey: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val url: String,
)
