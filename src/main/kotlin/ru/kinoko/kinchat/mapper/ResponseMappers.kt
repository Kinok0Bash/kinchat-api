package ru.kinoko.kinchat.mapper

import ru.kinoko.kinchat.dto.chat.ChatSummaryResponse
import ru.kinoko.kinchat.dto.chat.MessageAttachmentResponse
import ru.kinoko.kinchat.dto.chat.MessageResponse
import ru.kinoko.kinchat.dto.chat.MessageType
import ru.kinoko.kinchat.dto.user.PublicUserResponse
import ru.kinoko.kinchat.dto.jooq.AttachmentProjection
import ru.kinoko.kinchat.dto.jooq.ChatParticipantProjection
import ru.kinoko.kinchat.dto.jooq.MessageProjection
import ru.kinoko.kinchat.dto.jooq.PublicUserProjection
import java.util.UUID

fun PublicUserProjection.toResponse(): PublicUserResponse = PublicUserResponse(
    login = login,
    firstName = firstName,
    lastName = lastName,
    avatarUrl = avatarUrl,
)

fun AttachmentProjection.toResponse(): MessageAttachmentResponse = MessageAttachmentResponse(
    fileName = fileName,
    contentType = contentType,
    sizeBytes = sizeBytes,
    url = url,
)

fun MessageProjection.toResponse(
    sender: PublicUserResponse,
    attachments: List<MessageAttachmentResponse>,
): MessageResponse = MessageResponse(
    messageId = messageId,
    chatId = chatId,
    sender = sender,
    type = resolveMessageType(attachments),
    text = text,
    attachments = attachments,
    createdAt = createdAt,
    clientMessageId = clientMessageId,
)

fun MessageProjection.toResponse(
    senderProfiles: Map<UUID, PublicUserProjection>,
    attachmentsByMessage: Map<UUID, List<AttachmentProjection>>,
): MessageResponse {
    val sender = requireNotNull(senderProfiles[senderUserId]) { "Sender profile was not found for $senderUserId" }
    val attachments = attachmentsByMessage[messageId].orEmpty().map(AttachmentProjection::toResponse)
    return toResponse(sender.toResponse(), attachments)
}

fun ChatParticipantProjection.toResponse(
    participantProfiles: Map<UUID, PublicUserProjection>,
    lastMessages: Map<UUID, MessageProjection?>,
    attachmentsByMessage: Map<UUID, List<AttachmentProjection>>,
): ChatSummaryResponse {
    val participant = requireNotNull(participantProfiles[participantUserId]) {
        "Participant profile was not found for $participantUserId"
    }
    val lastMessage = lastMessages[chatId]
    val attachments = lastMessage
        ?.let { message -> attachmentsByMessage[message.messageId].orEmpty().map(AttachmentProjection::toResponse) }
        .orEmpty()

    return ChatSummaryResponse(
        chatId = chatId,
        participant = participant.toResponse(),
        lastMessagePreview = when {
            lastMessage == null -> null
            !lastMessage.text.isNullOrBlank() -> lastMessage.text
            attachments.isNotEmpty() -> attachments.first().fileName
            else -> null
        },
        lastMessageType = lastMessage?.let { resolveMessageType(attachments) },
        lastMessageAt = lastMessage?.createdAt,
    )
}

private fun resolveMessageType(attachments: List<MessageAttachmentResponse>): MessageType = when {
    attachments.isEmpty() -> MessageType.TEXT
    attachments.first().contentType.startsWith("image/", ignoreCase = true) -> MessageType.IMAGE
    else -> MessageType.FILE
}
