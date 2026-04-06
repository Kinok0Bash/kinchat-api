package ru.kinoko.kinchat.service

import org.springframework.stereotype.Service
import ru.kinoko.kinchat.dto.jooq.PublicUserProjection
import ru.kinoko.kinchat.dto.jooq.UserRepository
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
    fun getCurrentUserProfile(userId: UUID) = userService.getPublicUserById(userId)

    fun updateCurrentUserProfile(
        userId: UUID,
        request: UpdateProfileRequest,
    ) = updateProfile(userId, request).toResponse()

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

        if (loginChange == null && firstNameChange == null && lastNameChange == null) {
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

        return userRepository.updateProfile(
            userId = userId,
            login = loginChange,
            loginLower = loginChange,
            firstName = firstNameChange,
            lastName = lastNameChange,
            avatarUrl = avatarUrlChange,
        ) ?: throw NotFoundException("User was not found")
    }

    private fun validatePatchRequest(request: UpdateProfileRequest) {
        if (request.login == null && request.firstName == null && request.lastName == null) {
            throw ValidationException("At least one field must be provided")
        }
    }

    private fun validateLoginAvailability(userId: UUID, login: String) {
        val existingUserId = userRepository.findUserIdByLoginLower(login)
        if (existingUserId != null && existingUserId != userId) {
            throw ConflictException("User with this login already exists")
        }
    }

    private fun resolveAvatarUrlChange(
        userId: UUID,
        currentFirstName: String,
        currentLastName: String,
        firstNameChange: String?,
        lastNameChange: String?,
    ): String? {
        if (firstNameChange == null && lastNameChange == null) {
            return null
        }

        val avatarInfo = userRepository.findAvatarInfoByUserId(userId)
            ?: throw NotFoundException("User was not found")
        if (avatarInfo.avatarObjectKey != null) {
            return null
        }

        val effectiveFirstName = firstNameChange ?: currentFirstName
        val effectiveLastName = lastNameChange ?: currentLastName
        return userService.buildDefaultAvatarUrl(effectiveFirstName, effectiveLastName)
    }

    private fun normalizeName(value: String, fieldName: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            throw ValidationException("$fieldName must not be blank")
        }
        return normalized
    }
}
