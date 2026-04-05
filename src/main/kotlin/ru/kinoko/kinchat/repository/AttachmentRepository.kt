package ru.kinoko.kinchat.repository

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.kinoko.kinchat.dto.jooq.AttachmentProjection
import ru.kinoko.kinchat.jooq.tables.references.MESSAGE_ATTACHMENTS
import java.util.UUID

@Repository
class AttachmentRepository(
    private val dsl: DSLContext,
) {
    fun findAttachments(messageIds: Set<UUID>): Map<UUID, List<AttachmentProjection>> {
        if (messageIds.isEmpty()) {
            return emptyMap()
        }

        return dsl
            .selectFrom(MESSAGE_ATTACHMENTS)
            .where(MESSAGE_ATTACHMENTS.MESSAGE_ID.`in`(messageIds))
            .orderBy(MESSAGE_ATTACHMENTS.MESSAGE_ID.asc(), MESSAGE_ATTACHMENTS.ATTACHMENT_NO.asc())
            .fetch()
            .groupBy { it.messageId!! }
            .mapValues { (_, records) ->
                records.map { record ->
                    AttachmentProjection(
                        messageId = record.messageId!!,
                        attachmentNo = record.attachmentNo!!,
                        objectKey = record.objectKey!!,
                        fileName = record.fileName!!,
                        contentType = record.contentType!!,
                        sizeBytes = record.sizeBytes!!,
                        url = record.url!!,
                    )
                }
            }
    }
}
