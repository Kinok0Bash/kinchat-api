package ru.kinoko.kinchat.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.kinoko.kinchat.dto.jooq.PublicUserProjection
import ru.kinoko.kinchat.dto.jooq.UserRepository
import ru.kinoko.kinchat.dto.user.PublicUserResponse
import ru.kinoko.kinchat.dto.user.UpdateProfileRequest
import ru.kinoko.kinchat.exception.ConflictException
import ru.kinoko.kinchat.exception.NotFoundException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.mapper.toResponse
import java.util.UUID

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val userService: UserService,
) {
    fun getCurrentUserProfile(userId: UUID): PublicUserResponse {
        logger.info("Loading current profile userId={}", userId)
        val userResponse = userService.getPublicUserById(userId)
        logger.info("Current profile loaded userId={} login={}", userId, userResponse.login)
        return userResponse
    }

    fun updateCurrentUserProfile(
        userId: UUID,
        request: UpdateProfileRequest,
    ): PublicUserResponse {
        logger.info(
            "Updating current profile userId={} loginChanged={} firstNameChanged={} lastNameChanged={}",
            userId,
            request.login != null,
            request.firstName != null,
            request.lastName != null,
        )
        val userResponse = updateProfile(userId, request).toResponse()
        logger.info("Current profile updated userId={} login={}", userId, userResponse.login)
        return userResponse
    }

    private fun updateProfile(userId: UUID, request: UpdateProfileRequest): PublicUserProjection {
        validatePatchRequest(request)

        val currentProfile = userService.getPublicUserProjectionById(userId)
        val loginChange = request.login
            ?.let(userService::normalizeLogin)
            ?.takeUnless(currentProfile.login::equals)
        val firstNameChange = request.firstName
            ?.let { firstName -> normalizeName(firstName, "firstName") }
            ?.takeUnless(currentProfile.firstName::equals)
        val lastNameChange = request.lastName
            ?.let { lastName -> normalizeName(lastName, "lastName") }
            ?.takeUnless(currentProfile.lastName::equals)
        logger.info(
            "Calculated profile changes userId={} loginChanged={} firstNameChanged={} lastNameChanged={}",
            userId,
            loginChange != null,
            firstNameChange != null,
            lastNameChange != null,
        )

        if (loginChange == null && firstNameChange == null && lastNameChange == null) {
            logger.info("Profile update skipped because request produced no effective changes userId={}", userId)
            return currentProfile
        }

        loginChange?.let { updatedLogin ->
            validateLoginAvailability(userId, updatedLogin)
        }
        val avatarUrlChange = resolveAvatarUrlChange(
            userId = userId,
            currentFirstName = currentProfile.firstName,
            currentLastName = currentProfile.lastName,
            firstNameChange = firstNameChange,
            lastNameChange = lastNameChange,
        )

        val updatedProfile = userRepository.updateProfile(
            userId = userId,
            login = loginChange,
            loginLower = loginChange,
            firstName = firstNameChange,
            lastName = lastNameChange,
            avatarUrl = avatarUrlChange,
        ) ?: run {
            logger.info("Profile update failed because userId={} was not found", userId)
            throw NotFoundException("User was not found")
        }
        logger.info(
            "Profile update persisted userId={} login={} avatarUrlChanged={}",
            userId,
            updatedProfile.login,
            avatarUrlChange != null,
        )
        return updatedProfile
    }

    private fun validatePatchRequest(request: UpdateProfileRequest) {
        if (request.login == null && request.firstName == null && request.lastName == null) {
            logger.info("Profile update rejected because request does not contain any fields")
            throw ValidationException("At least one field must be provided")
        }
    }

    private fun validateLoginAvailability(userId: UUID, login: String) {
        val existingUserId = userRepository.findUserIdByLoginLower(login)
        if (existingUserId != null && existingUserId != userId) {
            logger.info(
                "Profile update rejected because login={} is already used by userId={}",
                login,
                existingUserId,
            )
            throw ConflictException("User with this login already exists")
        }
        logger.info("Profile update verified that login={} is available for userId={}", login, userId)
    }

    private fun resolveAvatarUrlChange(
        userId: UUID,
        currentFirstName: String,
        currentLastName: String,
        firstNameChange: String?,
        lastNameChange: String?,
    ): String? {
        if (firstNameChange == null && lastNameChange == null) {
            logger.info("Avatar URL remains unchanged because display name was not updated userId={}", userId)
            return null
        }

        val avatarInfo = userRepository.findAvatarInfoByUserId(userId)
            ?: run {
                logger.info("Avatar URL recalculation failed because userId={} was not found", userId)
                throw NotFoundException("User was not found")
            }
        if (avatarInfo.avatarObjectKey != null) {
            logger.info("Avatar URL remains unchanged because userId={} has uploaded avatar", userId)
            return null
        }

        val effectiveFirstName = firstNameChange ?: currentFirstName
        val effectiveLastName = lastNameChange ?: currentLastName
        logger.info("Rebuilding default avatar URL for userId={}", userId)
        return userService.buildDefaultAvatarUrl(effectiveFirstName, effectiveLastName)
    }

    private fun normalizeName(value: String, fieldName: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            logger.info("Profile update rejected because field={} is blank", fieldName)
            throw ValidationException("$fieldName must not be blank")
        }
        return normalized
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProfileService::class.java)
    }
}
