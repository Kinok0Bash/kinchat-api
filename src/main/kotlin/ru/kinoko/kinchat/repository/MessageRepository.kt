package ru.kinoko.kinchat.repository

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import ru.kinoko.kinchat.dto.jooq.AttachmentProjection
import ru.kinoko.kinchat.dto.jooq.MessageProjection
import ru.kinoko.kinchat.jooq.tables.references.MESSAGE_ATTACHMENTS
import ru.kinoko.kinchat.jooq.tables.references.MESSAGES
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class MessageRepository(
    private val dsl: DSLContext,
) {
    fun countMessages(chatId: UUID): Long = dsl.fetchCount(
        dsl.selectFrom(MESSAGES).where(MESSAGES.CHAT_ID.eq(chatId)),
    ).toLong()

    fun findMessages(chatId: UUID, page: Int, size: Int): List<MessageProjection> = dsl
        .selectFrom(MESSAGES)
        .where(MESSAGES.CHAT_ID.eq(chatId))
        .orderBy(MESSAGES.CREATED_AT.desc(), MESSAGES.MESSAGE_ID.desc())
        .limit(size)
        .offset(page * size)
        .fetch { record -> record.toProjection() }

    fun findLatestMessage(chatId: UUID): MessageProjection? = dsl
        .selectFrom(MESSAGES)
        .where(MESSAGES.CHAT_ID.eq(chatId))
        .orderBy(MESSAGES.CREATED_AT.desc(), MESSAGES.MESSAGE_ID.desc())
        .limit(1)
        .fetchOne { record -> record.toProjection() }

    fun findByClientMessageId(chatId: UUID, senderUserId: UUID, clientMessageId: UUID): MessageProjection? = dsl
        .selectFrom(MESSAGES)
        .where(MESSAGES.CHAT_ID.eq(chatId))
        .and(MESSAGES.SENDER_USER_ID.eq(senderUserId))
        .and(MESSAGES.CLIENT_MESSAGE_ID.eq(clientMessageId))
        .limit(1)
        .fetchOne { record -> record.toProjection() }

    fun createTextMessage(
        chatId: UUID,
        senderUserId: UUID,
        clientMessageId: UUID,
        text: String,
    ): PersistedMessage = dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val inserted = tx.insertInto(MESSAGES)
            .set(MESSAGES.MESSAGE_ID, UUID.randomUUID())
            .set(MESSAGES.CHAT_ID, chatId)
            .set(MESSAGES.SENDER_USER_ID, senderUserId)
            .set(MESSAGES.CLIENT_MESSAGE_ID, clientMessageId)
            .set(MESSAGES.TEXT, text)
            .set(MESSAGES.CREATED_AT, OffsetDateTime.now())
            .onConflict(MESSAGES.SENDER_USER_ID, MESSAGES.CHAT_ID, MESSAGES.CLIENT_MESSAGE_ID)
            .doNothing()
            .returning()
            .fetchOne()

        if (inserted != null) {
            return@transactionResult PersistedMessage(inserted.toProjection(), true)
        }

        PersistedMessage(
            message = requireNotNull(findByClientMessageId(tx, chatId, senderUserId, clientMessageId)),
            created = false,
        )
    }

    fun createAttachmentMessage(
        chatId: UUID,
        senderUserId: UUID,
        clientMessageId: UUID?,
        text: String?,
        attachment: AttachmentProjection,
    ): PersistedMessage = dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val insert = tx.insertInto(MESSAGES)
            .set(MESSAGES.MESSAGE_ID, UUID.randomUUID())
            .set(MESSAGES.CHAT_ID, chatId)
            .set(MESSAGES.SENDER_USER_ID, senderUserId)
            .set(MESSAGES.CLIENT_MESSAGE_ID, clientMessageId)
            .set(MESSAGES.TEXT, text)
            .set(MESSAGES.CREATED_AT, OffsetDateTime.now())

        val inserted = if (clientMessageId == null) {
            insert.returning().fetchOne()
        } else {
            insert
                .onConflict(MESSAGES.SENDER_USER_ID, MESSAGES.CHAT_ID, MESSAGES.CLIENT_MESSAGE_ID)
                .doNothing()
                .returning()
                .fetchOne()
        }

        if (inserted == null) {
            val existingMessage = requireNotNull(
                findByClientMessageId(tx, chatId, senderUserId, requireNotNull(clientMessageId)),
            )
            return@transactionResult PersistedMessage(
                message = existingMessage,
                created = false,
            )
        }

        tx.insertInto(MESSAGE_ATTACHMENTS)
            .set(MESSAGE_ATTACHMENTS.MESSAGE_ID, inserted.get(MESSAGES.MESSAGE_ID))
            .set(MESSAGE_ATTACHMENTS.ATTACHMENT_NO, attachment.attachmentNo)
            .set(MESSAGE_ATTACHMENTS.OBJECT_KEY, attachment.objectKey)
            .set(MESSAGE_ATTACHMENTS.FILE_NAME, attachment.fileName)
            .set(MESSAGE_ATTACHMENTS.CONTENT_TYPE, attachment.contentType)
            .set(MESSAGE_ATTACHMENTS.SIZE_BYTES, attachment.sizeBytes)
            .set(MESSAGE_ATTACHMENTS.URL, attachment.url)
            .set(MESSAGE_ATTACHMENTS.CREATED_AT, OffsetDateTime.now())
            .execute()

        PersistedMessage(inserted.toProjection(), true)
    }

    private fun findByClientMessageId(
        context: DSLContext,
        chatId: UUID,
        senderUserId: UUID,
        clientMessageId: UUID,
    ): MessageProjection? = context
        .selectFrom(MESSAGES)
        .where(MESSAGES.CHAT_ID.eq(chatId))
        .and(MESSAGES.SENDER_USER_ID.eq(senderUserId))
        .and(MESSAGES.CLIENT_MESSAGE_ID.eq(clientMessageId))
        .limit(1)
        .fetchOne { record -> record.toProjection() }

    private fun org.jooq.Record.toProjection(): MessageProjection = MessageProjection(
        messageId = get(MESSAGES.MESSAGE_ID)!!,
        chatId = get(MESSAGES.CHAT_ID)!!,
        senderUserId = get(MESSAGES.SENDER_USER_ID)!!,
        clientMessageId = get(MESSAGES.CLIENT_MESSAGE_ID),
        text = get(MESSAGES.TEXT),
        createdAt = get(MESSAGES.CREATED_AT)!!,
    )
}
