package ru.kinoko.kinchat.repository

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import ru.kinoko.kinchat.dto.jooq.ChatParticipantProjection
import ru.kinoko.kinchat.jooq.tables.references.CHATS
import ru.kinoko.kinchat.jooq.tables.references.CHAT_MEMBERS
import ru.kinoko.kinchat.jooq.tables.references.MESSAGES
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class ChatRepository(
    private val dsl: DSLContext,
) {
    fun countChats(userId: UUID): Long = dsl.fetchCount(
        dsl.select()
            .from(CHAT_MEMBERS)
            .join(CHATS)
            .on(CHATS.CHAT_ID.eq(CHAT_MEMBERS.CHAT_ID))
            .where(CHAT_MEMBERS.USER_ID.eq(userId))
            .and(CHATS.CHAT_TYPE.eq(DIRECT_CHAT_TYPE)),
    ).toLong()

    fun findChats(userId: UUID, page: Int, size: Int): List<ChatParticipantProjection> {
        val selfMember = CHAT_MEMBERS.`as`("self_member")
        val peerMember = CHAT_MEMBERS.`as`("peer_member")
        val lastMessageAt = DSL.field(
            DSL.select(DSL.max(MESSAGES.CREATED_AT))
                .from(MESSAGES)
                .where(MESSAGES.CHAT_ID.eq(CHATS.CHAT_ID)),
        )

        return dsl
            .select(CHATS.CHAT_ID, peerMember.USER_ID)
            .from(CHATS)
            .join(selfMember)
            .on(selfMember.CHAT_ID.eq(CHATS.CHAT_ID).and(selfMember.USER_ID.eq(userId)))
            .join(peerMember)
            .on(peerMember.CHAT_ID.eq(CHATS.CHAT_ID).and(peerMember.USER_ID.ne(userId)))
            .where(CHATS.CHAT_TYPE.eq(DIRECT_CHAT_TYPE))
            .orderBy(lastMessageAt.desc().nullsLast(), CHATS.CHAT_ID.desc())
            .limit(size)
            .offset(page * size)
            .fetch { record ->
                ChatParticipantProjection(
                    chatId = record.get(CHATS.CHAT_ID)!!,
                    participantUserId = record.get(peerMember.USER_ID)!!,
                )
            }
    }

    fun createOrGetDirectChat(currentUserId: UUID, peerUserId: UUID): UUID = dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val directKey = buildDirectKey(currentUserId, peerUserId)

        tx.fetch(ADVISORY_LOCK_QUERY, directKey)

        tx.select(CHATS.CHAT_ID)
            .from(CHATS)
            .where(CHATS.DIRECT_KEY.eq(directKey))
            .limit(1)
            .fetchOne(CHATS.CHAT_ID)
            ?: run {
                val chatId = UUID.randomUUID()
                val now = OffsetDateTime.now()

                tx.insertInto(CHATS)
                    .set(CHATS.CHAT_ID, chatId)
                    .set(CHATS.CHAT_TYPE, DIRECT_CHAT_TYPE)
                    .set(CHATS.DIRECT_KEY, directKey)
                    .set(CHATS.CREATED_BY_USER_ID, currentUserId)
                    .set(CHATS.CREATED_AT, now)
                    .execute()

                tx.batch(
                    tx.insertInto(CHAT_MEMBERS)
                        .set(CHAT_MEMBERS.CHAT_ID, chatId)
                        .set(CHAT_MEMBERS.USER_ID, currentUserId)
                        .set(CHAT_MEMBERS.JOINED_AT, now),
                    tx.insertInto(CHAT_MEMBERS)
                        .set(CHAT_MEMBERS.CHAT_ID, chatId)
                        .set(CHAT_MEMBERS.USER_ID, peerUserId)
                        .set(CHAT_MEMBERS.JOINED_AT, now),
                ).execute()

                chatId
            }
    }

    fun chatExists(chatId: UUID): Boolean = dsl.fetchExists(
        dsl.selectFrom(CHATS).where(CHATS.CHAT_ID.eq(chatId)),
    )

    fun isChatParticipant(chatId: UUID, userId: UUID): Boolean = dsl.fetchExists(
        dsl.selectFrom(CHAT_MEMBERS)
            .where(CHAT_MEMBERS.CHAT_ID.eq(chatId))
            .and(CHAT_MEMBERS.USER_ID.eq(userId)),
    )

    fun findParticipantUserIds(chatId: UUID): Set<UUID> = dsl
        .select(CHAT_MEMBERS.USER_ID)
        .from(CHAT_MEMBERS)
        .where(CHAT_MEMBERS.CHAT_ID.eq(chatId))
        .fetch(CHAT_MEMBERS.USER_ID)
        .filterNotNull()
        .toSet()

    private fun buildDirectKey(firstUserId: UUID, secondUserId: UUID): String = listOf(firstUserId, secondUserId)
        .map(UUID::toString)
        .sorted()
        .joinToString(DIRECT_KEY_DELIMITER)

    companion object {
        private const val ADVISORY_LOCK_QUERY = "select pg_advisory_xact_lock(hashtext(?))"
        private const val DIRECT_CHAT_TYPE = "DIRECT"
        private const val DIRECT_KEY_DELIMITER = ":"
    }
}
