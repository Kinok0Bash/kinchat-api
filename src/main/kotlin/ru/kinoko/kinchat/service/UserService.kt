package ru.kinoko.kinchat.service

import org.springframework.stereotype.Service
import ru.kinoko.kinchat.dto.user.MatchMode
import ru.kinoko.kinchat.dto.user.PagedUsersResponse
import ru.kinoko.kinchat.exception.NotFoundException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.mapper.toResponse
import ru.kinoko.kinchat.properties.AppProperties
import ru.kinoko.kinchat.dto.jooq.PublicUserProjection
import ru.kinoko.kinchat.dto.jooq.UserRepository
import ru.kinoko.kinchat.util.ApiConstants
import ru.kinoko.kinchat.util.calculateTotalPages
import java.util.Locale
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val appProperties: AppProperties,
) {
    fun getPublicUserById(userId: UUID) = getPublicUserProjectionById(userId).toResponse()

    fun getPublicUserByLogin(login: String) = getPublicUserProjectionByLogin(login).toResponse()

    fun getPublicUserProjectionById(userId: UUID): PublicUserProjection = userRepository.findPublicUserById(userId)
        ?: throw NotFoundException("User was not found")

    fun getPublicUserProjectionByLogin(login: String): PublicUserProjection = userRepository
        .findPublicUserByLoginLower(normalizeLogin(login))
        ?: throw NotFoundException("User was not found")

    fun findPublicUsersByIds(userIds: Set<UUID>): Map<UUID, PublicUserProjection> = userRepository.findPublicUsersByIds(userIds)

    fun findUserIdByLogin(login: String): UUID? = userRepository.findUserIdByLoginLower(normalizeLogin(login))

    fun searchUsers(
        currentUserId: UUID,
        login: String?,
        firstName: String?,
        lastName: String?,
        matchMode: MatchMode,
        excludeMe: Boolean,
        page: Int,
        size: Int,
    ): PagedUsersResponse {
        validatePaging(page, size)
        val normalizedLogin = login?.trim()?.takeIf(String::isNotBlank)?.lowercase(Locale.ROOT)
        val normalizedFirstName = firstName?.trim()?.takeIf(String::isNotBlank)
        val normalizedLastName = lastName?.trim()?.takeIf(String::isNotBlank)
        val totalElements = userRepository.countUsers(
            currentUserId = currentUserId,
            login = normalizedLogin,
            firstName = normalizedFirstName,
            lastName = normalizedLastName,
            matchMode = matchMode,
            excludeMe = excludeMe,
        )
        val users = userRepository.searchUsers(
            currentUserId = currentUserId,
            login = normalizedLogin,
            firstName = normalizedFirstName,
            lastName = normalizedLastName,
            matchMode = matchMode,
            excludeMe = excludeMe,
            page = page,
            size = size,
        )

        return PagedUsersResponse(
            items = users.map(PublicUserProjection::toResponse),
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = calculateTotalPages(totalElements, size),
        )
    }

    fun normalizeLogin(login: String): String {
        val normalized = login.trim().lowercase(Locale.ROOT)
        if (normalized.length !in 3..32 || !LOGIN_REGEX.matches(normalized)) {
            throw ValidationException("Login must contain 3..32 latin letters, digits, dot, underscore or hyphen")
        }
        return normalized
    }

    fun buildDefaultAvatarUrl(firstName: String, lastName: String): String {
        val template = appProperties.user.defaultAvatarUrlTemplate
        return if (template.contains("%s")) {
            template.format("$firstName+$lastName")
        } else {
            "$template$firstName+$lastName"
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

    companion object {
        private val LOGIN_REGEX = Regex("^[a-z0-9._-]+$")
    }
}
