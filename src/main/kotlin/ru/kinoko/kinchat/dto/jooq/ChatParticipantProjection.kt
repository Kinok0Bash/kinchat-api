package ru.kinoko.kinchat.dto.jooq

import java.util.UUID

data class ChatParticipantProjection(
    val chatId: UUID,
    val participantUserId: UUID,
)
