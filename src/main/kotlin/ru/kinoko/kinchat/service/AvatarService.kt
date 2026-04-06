package ru.kinoko.kinchat.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ru.kinoko.kinchat.dto.AuthenticatedUser
import ru.kinoko.kinchat.dto.jooq.UserRepository
import ru.kinoko.kinchat.dto.user.AvatarUploadResponse
import ru.kinoko.kinchat.exception.NotFoundException
import ru.kinoko.kinchat.exception.PayloadTooLargeException
import ru.kinoko.kinchat.exception.UnsupportedMediaTypeApiException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.mapper.toResponse
import ru.kinoko.kinchat.properties.MinioProperties
import ru.kinoko.kinchat.util.ApiConstants
import java.util.UUID

@Service
class AvatarService(
    private val userRepository: UserRepository,
    private val minioProperties: MinioProperties,
    private val minioStorageService: MinioStorageService,
) {
    fun uploadAvatar(currentUser: AuthenticatedUser, file: MultipartFile): AvatarUploadResponse {
        logger.info(
            "Uploading avatar userId={} sizeBytes={} contentType={}",
            currentUser.userId,
            file.size,
            file.contentType,
        )
        validateAvatar(file)
        val currentAvatar = userRepository.findAvatarInfoByUserId(currentUser.userId)
            ?: run {
                logger.info("Avatar upload failed because avatar info for userId={} was not found", currentUser.userId)
                throw NotFoundException("User was not found")
            }
        val currentProfile = userRepository.findPublicUserById(currentUser.userId)
            ?: run {
                logger.info("Avatar upload failed because profile for userId={} was not found", currentUser.userId)
                throw NotFoundException("User was not found")
            }
        val objectKey = buildAvatarObjectKey(currentProfile.login, file)
        val contentType = file.contentType!!.trim()
        logger.info(
            "Uploading avatar object to storage userId={} objectKey={} oldAvatarPresent={}",
            currentUser.userId,
            objectKey,
            currentAvatar.avatarObjectKey != null,
        )
        val storedObject = minioStorageService.upload(
            bucket = minioProperties.avatars,
            objectKey = objectKey,
            file = file,
            contentType = contentType,
        )

        try {
            val updatedUser = userRepository.updateAvatar(
                userId = currentUser.userId,
                avatarUrl = storedObject.url,
                avatarObjectKey = storedObject.objectKey,
            ) ?: run {
                logger.info("Avatar upload failed because userId={} disappeared before update", currentUser.userId)
                throw NotFoundException("User was not found")
            }

            currentAvatar.avatarObjectKey?.let {
                logger.info("Deleting previous avatar object userId={} objectKey={}", currentUser.userId, it)
                minioStorageService.delete(minioProperties.avatars, it)
            }

            val avatarResponse = AvatarUploadResponse(updatedUser.toResponse())
            logger.info("Avatar upload completed userId={} login={}", currentUser.userId, avatarResponse.user.login)
            return avatarResponse
        } catch (exception: Exception) {
            logger.error(
                "Avatar upload failed userId={} objectKey={}",
                currentUser.userId,
                storedObject.objectKey,
                exception,
            )
            minioStorageService.delete(minioProperties.avatars, storedObject.objectKey)
            throw exception
        }
    }

    private fun validateAvatar(file: MultipartFile) {
        if (file.isEmpty) {
            logger.info("Avatar validation failed because file is empty")
            throw ValidationException("Avatar file is empty")
        }
        if (file.size > ApiConstants.MAX_AVATAR_SIZE_BYTES) {
            logger.info(
                "Avatar validation failed because sizeBytes={} exceeds limitBytes={}",
                file.size,
                ApiConstants.MAX_AVATAR_SIZE_BYTES,
            )
            throw PayloadTooLargeException("Avatar size exceeds ${ApiConstants.MAX_AVATAR_SIZE_BYTES} bytes")
        }
        val contentType = file.contentType?.trim().orEmpty()
        if (!contentType.startsWith("image/", ignoreCase = true)) {
            logger.info("Avatar validation failed because contentType={} is not image/*", contentType)
            throw UnsupportedMediaTypeApiException("Avatar content type must be image/*")
        }
    }

    private fun buildAvatarObjectKey(login: String, file: MultipartFile): String {
        val extension = extractExtension(file.originalFilename, file.contentType)
        return "avatars/$login/${UUID.randomUUID()}.$extension"
    }

    private fun extractExtension(fileName: String?, contentType: String?): String {
        val originalExtension = fileName
            ?.substringAfterLast('.', "")
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.matches(Regex("^[a-z0-9]+$")) }
        if (originalExtension != null) {
            return originalExtension
        }

        return when (contentType?.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "img"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AvatarService::class.java)
    }
}
