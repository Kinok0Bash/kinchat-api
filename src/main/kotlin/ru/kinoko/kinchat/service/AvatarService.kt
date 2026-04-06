package ru.kinoko.kinchat.service

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ru.kinoko.kinchat.dto.user.AvatarUploadResponse
import ru.kinoko.kinchat.exception.NotFoundException
import ru.kinoko.kinchat.exception.PayloadTooLargeException
import ru.kinoko.kinchat.exception.UnsupportedMediaTypeApiException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.mapper.toResponse
import ru.kinoko.kinchat.properties.MinioProperties
import ru.kinoko.kinchat.dto.jooq.UserRepository
import ru.kinoko.kinchat.dto.AuthenticatedUser
import ru.kinoko.kinchat.util.ApiConstants
import java.util.UUID

@Service
class AvatarService(
    private val userRepository: UserRepository,
    private val minioProperties: MinioProperties,
    private val minioStorageService: MinioStorageService,
) {
    fun uploadAvatar(currentUser: AuthenticatedUser, file: MultipartFile): AvatarUploadResponse {
        validateAvatar(file)
        val currentAvatar = userRepository.findAvatarInfoByUserId(currentUser.userId)
            ?: throw NotFoundException("User was not found")
        val currentProfile = userRepository.findPublicUserById(currentUser.userId)
            ?: throw NotFoundException("User was not found")
        val objectKey = buildAvatarObjectKey(currentProfile.login, file)
        val contentType = file.contentType!!.trim()
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
            ) ?: throw NotFoundException("User was not found")

            currentAvatar.avatarObjectKey?.let {
                minioStorageService.delete(minioProperties.avatars, it)
            }

            return AvatarUploadResponse(updatedUser.toResponse())
        } catch (exception: Exception) {
            minioStorageService.delete(minioProperties.avatars, storedObject.objectKey)
            throw exception
        }
    }

    private fun validateAvatar(file: MultipartFile) {
        if (file.isEmpty) {
            throw ValidationException("Avatar file is empty")
        }
        if (file.size > ApiConstants.MAX_AVATAR_SIZE_BYTES) {
            throw PayloadTooLargeException("Avatar size exceeds ${ApiConstants.MAX_AVATAR_SIZE_BYTES} bytes")
        }
        val contentType = file.contentType?.trim().orEmpty()
        if (!contentType.startsWith("image/", ignoreCase = true)) {
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
}
