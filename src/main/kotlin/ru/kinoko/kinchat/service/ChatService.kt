package ru.kinoko.kinchat.service

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ru.kinoko.kinchat.dto.chat.ChatSummaryResponse
import ru.kinoko.kinchat.dto.chat.CreateDirectChatRequest
import ru.kinoko.kinchat.dto.chat.MessageAttachmentResponse
import ru.kinoko.kinchat.dto.chat.MessageDeliveryResult
import ru.kinoko.kinchat.dto.chat.MessageResponse
import ru.kinoko.kinchat.dto.chat.PagedChatsResponse
import ru.kinoko.kinchat.dto.chat.PagedMessagesResponse
import ru.kinoko.kinchat.dto.chat.UploadAttachmentRequest
import ru.kinoko.kinchat.exception.ForbiddenException
import ru.kinoko.kinchat.exception.NotFoundException
import ru.kinoko.kinchat.exception.PayloadTooLargeException
import ru.kinoko.kinchat.exception.UnsupportedMediaTypeApiException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.mapper.toResponse
import ru.kinoko.kinchat.properties.MinioProperties
import ru.kinoko.kinchat.dto.jooq.AttachmentProjection
import ru.kinoko.kinchat.repository.AttachmentRepository
import ru.kinoko.kinchat.dto.jooq.ChatParticipantProjection
import ru.kinoko.kinchat.repository.ChatRepository
import ru.kinoko.kinchat.dto.jooq.MessageProjection
import ru.kinoko.kinchat.repository.MessageRepository
import ru.kinoko.kinchat.dto.AuthenticatedUser
import ru.kinoko.kinchat.util.ApiConstants
import ru.kinoko.kinchat.util.calculateTotalPages
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val attachmentRepository: AttachmentRepository,
    private val userService: UserService,
    private val minioProperties: MinioProperties,
    private val minioStorageService: MinioStorageService,
    private val realtimeService: RealtimeService,
) {
    fun getChats(currentUser: AuthenticatedUser, page: Int, size: Int): PagedChatsResponse {
        validatePaging(page, size)
        val totalElements = chatRepository.countChats(currentUser.userId)
        val chats = chatRepository.findChats(currentUser.userId, page, size)
        val participantProfiles = userService.findPublicUsersByIds(chats.map(ChatParticipantProjection::participantUserId).toSet())
        val lastMessages = chats.associate { chat -> chat.chatId to messageRepository.findLatestMessage(chat.chatId) }
        val attachmentsByMessage = attachmentRepository.findAttachments(
            lastMessages.values.mapNotNull { it?.messageId }.toSet(),
        )

        return PagedChatsResponse(
            items = chats.map { chat ->
                chat.toResponse(
                    participantProfiles = participantProfiles,
                    lastMessages = lastMessages,
                    attachmentsByMessage = attachmentsByMessage,
                )
            },
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = calculateTotalPages(totalElements, size),
        )
    }

    fun createDirectChat(currentUser: AuthenticatedUser, request: CreateDirectChatRequest): ChatSummaryResponse {
        val peerLogin = userService.normalizeLogin(request.peerLogin)
        val peerUserId = userService.findUserIdByLogin(peerLogin)
            ?: throw NotFoundException("Peer user was not found")
        if (peerUserId == currentUser.userId) {
            throw ValidationException("Cannot create direct chat with yourself")
        }

        val chatId = chatRepository.createOrGetDirectChat(currentUser.userId, peerUserId)
        val participant = userService.getPublicUserProjectionById(peerUserId)

        return ChatSummaryResponse(
            chatId = chatId,
            participant = participant.toResponse(),
            lastMessagePreview = null,
            lastMessageType = null,
            lastMessageAt = null,
        )
    }

    fun getMessages(currentUser: AuthenticatedUser, chatId: UUID, page: Int, size: Int): PagedMessagesResponse {
        validatePaging(page, size)
        ensureChatAccessible(chatId, currentUser.userId)

        val totalElements = messageRepository.countMessages(chatId)
        val messages = messageRepository.findMessages(chatId, page, size)
        val senderProfiles = userService.findPublicUsersByIds(messages.map(MessageProjection::senderUserId).toSet())
        val attachmentsByMessage = attachmentRepository.findAttachments(messages.map(MessageProjection::messageId).toSet())

        return PagedMessagesResponse(
            items = messages.map { message ->
                message.toResponse(
                    senderProfiles = senderProfiles,
                    attachmentsByMessage = attachmentsByMessage,
                )
            },
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = calculateTotalPages(totalElements, size),
        )
    }

    fun uploadAttachment(
        currentUser: AuthenticatedUser,
        chatId: UUID,
        request: UploadAttachmentRequest,
    ): MessageResponse {
        ensureChatAccessible(chatId, currentUser.userId)
        request.clientMessageId?.let { clientMessageId ->
            messageRepository.findByClientMessageId(chatId, currentUser.userId, clientMessageId)?.let {
                return buildMessageResponse(it)
            }
        }

        val file = request.file ?: throw ValidationException("Attachment file is required")
        validateAttachment(file)
        val text = request.text?.trim()?.takeIf(String::isNotBlank)
        val objectKey = buildAttachmentObjectKey(chatId, file.originalFilename)
        val contentType = file.contentType!!.trim()
        val storedObject = minioStorageService.upload(
            bucket = minioProperties.messageFiles,
            objectKey = objectKey,
            file = file,
            contentType = contentType,
        )
        val attachment = AttachmentProjection(
            messageId = UUID.randomUUID(),
            attachmentNo = 1,
            objectKey = storedObject.objectKey,
            fileName = storedObject.fileName,
            contentType = storedObject.contentType,
            sizeBytes = storedObject.sizeBytes,
            url = storedObject.url,
        )

        try {
            val persisted = messageRepository.createAttachmentMessage(
                chatId = chatId,
                senderUserId = currentUser.userId,
                clientMessageId = request.clientMessageId,
                text = text,
                attachment = attachment,
            )

            if (!persisted.created) {
                minioStorageService.delete(minioProperties.messageFiles, storedObject.objectKey)
                return buildMessageResponse(persisted.message)
            }

            val response = persisted.message.toResponse(
                sender = userService.getPublicUserProjectionById(currentUser.userId).toResponse(),
                attachments = listOf(
                    MessageAttachmentResponse(
                        fileName = storedObject.fileName,
                        contentType = storedObject.contentType,
                        sizeBytes = storedObject.sizeBytes,
                        url = storedObject.url,
                    ),
                ),
            )
            realtimeService.publishMessageCreated(chatId, response)
            return response
        } catch (exception: Exception) {
            minioStorageService.delete(minioProperties.messageFiles, storedObject.objectKey)
            throw exception
        }
    }

    fun sendTextMessage(
        userId: UUID,
        chatId: UUID,
        clientMessageId: UUID,
        text: String,
    ): MessageDeliveryResult {
        ensureChatAccessible(chatId, userId)
        val normalizedText = text.trim().takeIf(String::isNotBlank)
            ?: throw ValidationException("Message text cannot be blank")
        val persisted = messageRepository.createTextMessage(
            chatId = chatId,
            senderUserId = userId,
            clientMessageId = clientMessageId,
            text = normalizedText,
        )
        val response = buildMessageResponse(persisted.message)

        if (persisted.created) {
            realtimeService.publishMessageCreated(chatId, response)
        }

        return MessageDeliveryResult(response, persisted.created)
    }

    private fun buildMessageResponse(message: MessageProjection): MessageResponse {
        val attachments = attachmentRepository.findAttachments(setOf(message.messageId))[message.messageId]
            .orEmpty()
            .map(AttachmentProjection::toResponse)
        val sender = userService.getPublicUserProjectionById(message.senderUserId).toResponse()
        return message.toResponse(sender = sender, attachments = attachments)
    }

    private fun ensureChatAccessible(chatId: UUID, userId: UUID) {
        if (!chatRepository.chatExists(chatId)) {
            throw NotFoundException("Chat was not found")
        }
        if (!chatRepository.isChatParticipant(chatId, userId)) {
            throw ForbiddenException("Chat access denied")
        }
    }

    private fun validatePaging(page: Int, size: Int) {
        if (page < 0) {
            throw ValidationException("page must be greater than or equal to 0")
        }
        if (size !in 1..ApiConstants.MAX_PAGE_SIZE) {
            throw ValidationException("size must be in range 1..${ApiConstants.MAX_PAGE_SIZE}")
        }
    }

    private fun validateAttachment(file: MultipartFile) {
        if (file.isEmpty) {
            throw ValidationException("Attachment file is empty")
        }
        if (file.size > ApiConstants.MAX_ATTACHMENT_SIZE_BYTES) {
            throw PayloadTooLargeException("Attachment size exceeds ${ApiConstants.MAX_ATTACHMENT_SIZE_BYTES} bytes")
        }
        if (file.contentType?.trim().isNullOrBlank()) {
            throw UnsupportedMediaTypeApiException("Attachment content type is missing")
        }
    }

    private fun buildAttachmentObjectKey(chatId: UUID, originalFileName: String?): String {
        val now = OffsetDateTime.now()
        val safeFileName = sanitizeFileName(originalFileName ?: "attachment.bin")
        return "messages/$chatId/${now.year}/${now.monthValue.toString().padStart(2, '0')}/${UUID.randomUUID()}_$safeFileName"
    }

    private fun sanitizeFileName(fileName: String): String {
        val sanitized = buildString(fileName.length) {
            fileName.forEach { char ->
                append(
                    when {
                        char.isLetterOrDigit() -> char
                        char == '.' || char == '_' || char == '-' -> char
                        else -> '_'
                    },
                )
            }
        }.trim('_')

        return sanitized.ifBlank { "attachment.bin" }
    }
}
