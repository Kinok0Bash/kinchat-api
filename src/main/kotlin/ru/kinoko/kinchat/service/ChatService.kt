package ru.kinoko.kinchat.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ru.kinoko.kinchat.dto.AuthenticatedUser
import ru.kinoko.kinchat.dto.StoredObject
import ru.kinoko.kinchat.dto.chat.ChatSummaryResponse
import ru.kinoko.kinchat.dto.chat.CreateDirectChatRequest
import ru.kinoko.kinchat.dto.chat.MessageAttachmentResponse
import ru.kinoko.kinchat.dto.chat.MessageDeliveryResult
import ru.kinoko.kinchat.dto.chat.MessageResponse
import ru.kinoko.kinchat.dto.chat.PagedChatsResponse
import ru.kinoko.kinchat.dto.chat.PagedMessagesResponse
import ru.kinoko.kinchat.dto.chat.UploadAttachmentRequest
import ru.kinoko.kinchat.dto.jooq.AttachmentProjection
import ru.kinoko.kinchat.dto.jooq.ChatParticipantProjection
import ru.kinoko.kinchat.dto.jooq.MessageProjection
import ru.kinoko.kinchat.exception.ForbiddenException
import ru.kinoko.kinchat.exception.NotFoundException
import ru.kinoko.kinchat.exception.PayloadTooLargeException
import ru.kinoko.kinchat.exception.UnsupportedMediaTypeApiException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.mapper.toResponse
import ru.kinoko.kinchat.properties.MinioProperties
import ru.kinoko.kinchat.repository.AttachmentRepository
import ru.kinoko.kinchat.repository.ChatRepository
import ru.kinoko.kinchat.repository.MessageRepository
import ru.kinoko.kinchat.util.ApiConstants
import ru.kinoko.kinchat.util.calculateTotalPages
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Suppress("TooManyFunctions")
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
        logger.info("Loading chats userId={} page={} size={}", currentUser.userId, page, size)
        validatePaging(page, size)
        val totalElements = chatRepository.countChats(currentUser.userId)
        val chats = chatRepository.findChats(currentUser.userId, page, size)
        val participantProfiles = userService.findPublicUsersByIds(
            chats.map(ChatParticipantProjection::participantUserId).toSet(),
        )
        val lastMessages = chats.associate { chat -> chat.chatId to messageRepository.findLatestMessage(chat.chatId) }
        val attachmentsByMessage = attachmentRepository.findAttachments(
            lastMessages.values.mapNotNull { it?.messageId }.toSet(),
        )

        val chatsResponse = PagedChatsResponse(
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
        logger.info(
            "Loaded chats userId={} returnedItems={} totalElements={} resolvedParticipants={} " +
                "resolvedLastMessages={} attachmentGroups={}",
            currentUser.userId,
            chatsResponse.items.size,
            totalElements,
            participantProfiles.size,
            lastMessages.values.count { it != null },
            attachmentsByMessage.size,
        )
        return chatsResponse
    }

    fun createDirectChat(currentUser: AuthenticatedUser, request: CreateDirectChatRequest): ChatSummaryResponse {
        val peerLogin = userService.normalizeLogin(request.peerLogin)
        logger.info("Creating direct chat requesterUserId={} peerLogin={}", currentUser.userId, peerLogin)
        val peerUserId = userService.findUserIdByLogin(peerLogin)
            ?: run {
                logger.info("Direct chat creation rejected because peerLogin={} was not found", peerLogin)
                throw NotFoundException("Peer user was not found")
            }
        if (peerUserId == currentUser.userId) {
            logger.info("Direct chat creation rejected because userId={} targeted self", currentUser.userId)
            throw ValidationException("Cannot create direct chat with yourself")
        }

        val chatId = chatRepository.createOrGetDirectChat(currentUser.userId, peerUserId)
        val participant = userService.getPublicUserProjectionById(peerUserId)

        val chatResponse = ChatSummaryResponse(
            chatId = chatId,
            participant = participant.toResponse(),
            lastMessagePreview = null,
            lastMessageType = null,
            lastMessageAt = null,
        )
        logger.info(
            "Direct chat resolved requesterUserId={} peerUserId={} chatId={}",
            currentUser.userId,
            peerUserId,
            chatId,
        )
        return chatResponse
    }

    fun getMessages(currentUser: AuthenticatedUser, chatId: UUID, page: Int, size: Int): PagedMessagesResponse {
        logger.info(
            "Loading messages userId={} chatId={} page={} size={}",
            currentUser.userId,
            chatId,
            page,
            size,
        )
        validatePaging(page, size)
        ensureChatAccessible(chatId, currentUser.userId)

        val totalElements = messageRepository.countMessages(chatId)
        val messages = messageRepository.findMessages(chatId, page, size)
        val senderProfiles = userService.findPublicUsersByIds(
            messages.map(MessageProjection::senderUserId).toSet(),
        )
        val attachmentsByMessage = attachmentRepository.findAttachments(
            messages.map(MessageProjection::messageId).toSet(),
        )

        val messagesResponse = PagedMessagesResponse(
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
        logger.info(
            "Loaded messages userId={} chatId={} returnedItems={} totalElements={} senderProfiles={} " +
                "attachmentGroups={}",
            currentUser.userId,
            chatId,
            messagesResponse.items.size,
            totalElements,
            senderProfiles.size,
            attachmentsByMessage.size,
        )
        return messagesResponse
    }

    fun uploadAttachment(
        currentUser: AuthenticatedUser,
        chatId: UUID,
        request: UploadAttachmentRequest,
    ): MessageResponse {
        logger.info(
            "Uploading attachment userId={} chatId={} clientMessageId={} textPresent={}",
            currentUser.userId,
            chatId,
            request.clientMessageId,
            !request.text.isNullOrBlank(),
        )
        ensureChatAccessible(chatId, currentUser.userId)

        findExistingAttachmentResponse(currentUser.userId, chatId, request.clientMessageId)?.let {
            return it
        }

        val file = requireAttachmentFile(request, chatId)
        val text = request.text?.trim()?.takeIf(String::isNotBlank)
        val storedObject = uploadAttachmentObject(chatId, file)
        val attachment = buildAttachmentProjection(storedObject)

        return persistAttachmentMessage(
            currentUser = currentUser,
            chatId = chatId,
            clientMessageId = request.clientMessageId,
            text = text,
            storedObject = storedObject,
            attachment = attachment,
        )
    }

    fun sendTextMessage(
        userId: UUID,
        chatId: UUID,
        clientMessageId: UUID,
        text: String,
    ): MessageDeliveryResult {
        logger.info(
            "Sending text message userId={} chatId={} clientMessageId={} textLength={}",
            userId,
            chatId,
            clientMessageId,
            text.length,
        )
        ensureChatAccessible(chatId, userId)
        val normalizedText = text.trim().takeIf(String::isNotBlank)
            ?: run {
                logger.info(
                    "Text message rejected because body was blank userId={} chatId={} clientMessageId={}",
                    userId,
                    chatId,
                    clientMessageId,
                )
                throw ValidationException("Message text cannot be blank")
            }
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

        logger.info(
            "Text message processed userId={} chatId={} messageId={} created={}",
            userId,
            chatId,
            response.messageId,
            persisted.created,
        )
        return MessageDeliveryResult(response, persisted.created)
    }

    private fun findExistingAttachmentResponse(
        userId: UUID,
        chatId: UUID,
        clientMessageId: UUID?,
    ): MessageResponse? = clientMessageId?.let { requestId ->
        messageRepository.findByClientMessageId(chatId, userId, requestId)?.let { message ->
            logger.info(
                "Attachment upload deduplicated userId={} chatId={} clientMessageId={} messageId={}",
                userId,
                chatId,
                requestId,
                message.messageId,
            )
            buildMessageResponse(message)
        }
    }

    private fun requireAttachmentFile(
        request: UploadAttachmentRequest,
        chatId: UUID,
    ): MultipartFile = request.file ?: run {
        logger.info("Attachment upload rejected because file was not provided chatId={}", chatId)
        throw ValidationException("Attachment file is required")
    }

    private fun uploadAttachmentObject(chatId: UUID, file: MultipartFile): StoredObject {
        validateAttachment(file)
        val objectKey = buildAttachmentObjectKey(chatId, file.originalFilename)
        val contentType = file.contentType!!.trim()
        logger.info(
            "Uploading attachment object to storage chatId={} objectKey={} sizeBytes={} contentType={}",
            chatId,
            objectKey,
            file.size,
            contentType,
        )
        return minioStorageService.upload(
            bucket = minioProperties.messageFiles,
            objectKey = objectKey,
            file = file,
            contentType = contentType,
        )
    }

    private fun buildAttachmentProjection(storedObject: StoredObject): AttachmentProjection = AttachmentProjection(
        messageId = UUID.randomUUID(),
        attachmentNo = 1,
        objectKey = storedObject.objectKey,
        fileName = storedObject.fileName,
        contentType = storedObject.contentType,
        sizeBytes = storedObject.sizeBytes,
        url = storedObject.url,
    )

    private fun persistAttachmentMessage(
        currentUser: AuthenticatedUser,
        chatId: UUID,
        clientMessageId: UUID?,
        text: String?,
        storedObject: StoredObject,
        attachment: AttachmentProjection,
    ): MessageResponse = try {
        val persisted = messageRepository.createAttachmentMessage(
            chatId = chatId,
            senderUserId = currentUser.userId,
            clientMessageId = clientMessageId,
            text = text,
            attachment = attachment,
        )

        if (!persisted.created) {
            return handleDuplicateAttachmentMessage(
                currentUserId = currentUser.userId,
                chatId = chatId,
                clientMessageId = clientMessageId,
                storedObject = storedObject,
                persistedMessage = persisted.message,
            )
        }

        val response = createAttachmentMessageResponse(currentUser.userId, persisted.message, storedObject)
        realtimeService.publishMessageCreated(chatId, response)
        logger.info(
            "Attachment message created userId={} chatId={} messageId={} attachmentKey={}",
            currentUser.userId,
            chatId,
            response.messageId,
            storedObject.objectKey,
        )
        response
    } catch (exception: Exception) {
        handleAttachmentUploadFailure(currentUser.userId, chatId, storedObject.objectKey, exception)
    }

    private fun handleDuplicateAttachmentMessage(
        currentUserId: UUID,
        chatId: UUID,
        clientMessageId: UUID?,
        storedObject: StoredObject,
        persistedMessage: MessageProjection,
    ): MessageResponse {
        logger.info(
            "Attachment message deduplicated after upload userId={} chatId={} clientMessageId={} " +
                "existingMessageId={}",
            currentUserId,
            chatId,
            clientMessageId,
            persistedMessage.messageId,
        )
        minioStorageService.delete(minioProperties.messageFiles, storedObject.objectKey)
        return buildMessageResponse(persistedMessage)
    }

    private fun createAttachmentMessageResponse(
        currentUserId: UUID,
        persistedMessage: MessageProjection,
        storedObject: StoredObject,
    ): MessageResponse = persistedMessage.toResponse(
        sender = userService.getPublicUserProjectionById(currentUserId).toResponse(),
        attachments = listOf(
            MessageAttachmentResponse(
                fileName = storedObject.fileName,
                contentType = storedObject.contentType,
                sizeBytes = storedObject.sizeBytes,
                url = storedObject.url,
            ),
        ),
    )

    private fun handleAttachmentUploadFailure(
        userId: UUID,
        chatId: UUID,
        objectKey: String,
        exception: Exception,
    ): Nothing {
        logger.error(
            "Attachment upload failed userId={} chatId={} objectKey={}",
            userId,
            chatId,
            objectKey,
            exception,
        )
        minioStorageService.delete(minioProperties.messageFiles, objectKey)
        throw exception
    }

    private fun buildMessageResponse(message: MessageProjection): MessageResponse {
        val attachments = attachmentRepository.findAttachments(setOf(message.messageId))[message.messageId]
            .orEmpty()
            .map(AttachmentProjection::toResponse)
        val sender = userService.getPublicUserProjectionById(message.senderUserId).toResponse()
        val messageResponse = message.toResponse(sender = sender, attachments = attachments)
        logger.info(
            "Built message response messageId={} chatId={} attachmentCount={}",
            messageResponse.messageId,
            messageResponse.chatId,
            messageResponse.attachments.size,
        )
        return messageResponse
    }

    private fun ensureChatAccessible(chatId: UUID, userId: UUID) {
        logger.info("Checking chat access userId={} chatId={}", userId, chatId)
        if (!chatRepository.chatExists(chatId)) {
            logger.info("Chat access rejected because chatId={} was not found", chatId)
            throw NotFoundException("Chat was not found")
        }
        if (!chatRepository.isChatParticipant(chatId, userId)) {
            logger.info(
                "Chat access rejected because userId={} is not a participant of chatId={}",
                userId,
                chatId,
            )
            throw ForbiddenException("Chat access denied")
        }
        logger.info("Chat access granted userId={} chatId={}", userId, chatId)
    }

    private fun validatePaging(page: Int, size: Int) {
        if (page < 0) {
            logger.info("Paging rejected because page={} is negative", page)
            throw ValidationException("page must be greater than or equal to 0")
        }
        if (size !in 1..ApiConstants.MAX_PAGE_SIZE) {
            logger.info("Paging rejected because size={} is outside allowed range", size)
            throw ValidationException("size must be in range 1..${ApiConstants.MAX_PAGE_SIZE}")
        }
    }

    private fun validateAttachment(file: MultipartFile) {
        if (file.isEmpty) {
            logger.info("Attachment validation failed because file is empty")
            throw ValidationException("Attachment file is empty")
        }
        if (file.size > ApiConstants.MAX_ATTACHMENT_SIZE_BYTES) {
            logger.info(
                "Attachment validation failed because sizeBytes={} exceeds limitBytes={}",
                file.size,
                ApiConstants.MAX_ATTACHMENT_SIZE_BYTES,
            )
            throw PayloadTooLargeException("Attachment size exceeds ${ApiConstants.MAX_ATTACHMENT_SIZE_BYTES} bytes")
        }
        if (file.contentType?.trim().isNullOrBlank()) {
            logger.info("Attachment validation failed because content type is missing")
            throw UnsupportedMediaTypeApiException("Attachment content type is missing")
        }
    }

    private fun buildAttachmentObjectKey(chatId: UUID, originalFileName: String?): String {
        val now = OffsetDateTime.now()
        val safeFileName = sanitizeFileName(originalFileName ?: "attachment.bin")
        val monthSegment = now.monthValue.toString().padStart(2, '0')
        return "messages/$chatId/${now.year}/$monthSegment/${UUID.randomUUID()}_$safeFileName"
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

    companion object {
        private val logger = LoggerFactory.getLogger(ChatService::class.java)
    }
}
