package ru.kinoko.kinchat.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.kinoko.kinchat.dto.jooq.PublicUserProjection
import ru.kinoko.kinchat.dto.jooq.UserRepository
import ru.kinoko.kinchat.dto.user.MatchMode
import ru.kinoko.kinchat.dto.user.PagedUsersResponse
import ru.kinoko.kinchat.dto.user.PublicUserResponse
import ru.kinoko.kinchat.exception.NotFoundException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.mapper.toResponse
import ru.kinoko.kinchat.properties.AppProperties
import ru.kinoko.kinchat.util.ApiConstants
import ru.kinoko.kinchat.util.calculateTotalPages
import java.util.Locale
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val appProperties: AppProperties,
) {
    fun getPublicUserById(userId: UUID): PublicUserResponse {
        logger.info("Loading public user by id userId={}", userId)
        val userResponse = getPublicUserProjectionById(userId).toResponse()
        logger.info("Loaded public user by id userId={} login={}", userId, userResponse.login)
        return userResponse
    }

    fun getPublicUserByLogin(login: String): PublicUserResponse {
        logger.info("Loading public user by login login={}", login)
        val userResponse = getPublicUserProjectionByLogin(login).toResponse()
        logger.info("Loaded public user by login login={}", userResponse.login)
        return userResponse
    }

    fun getPublicUserProjectionById(userId: UUID): PublicUserProjection {
        logger.info("Resolving public user projection by id userId={}", userId)
        val userProjection = userRepository.findPublicUserById(userId)
            ?: run {
                logger.info("Public user projection by id was not found userId={}", userId)
                throw NotFoundException("User was not found")
            }
        logger.info("Resolved public user projection by id userId={} login={}", userId, userProjection.login)
        return userProjection
    }

    fun getPublicUserProjectionByLogin(login: String): PublicUserProjection {
        val normalizedLogin = normalizeLogin(login)
        logger.info("Resolving public user projection by login normalizedLogin={}", normalizedLogin)
        val userProjection = userRepository.findPublicUserByLoginLower(normalizedLogin)
            ?: run {
                logger.info("Public user projection by login was not found normalizedLogin={}", normalizedLogin)
                throw NotFoundException("User was not found")
            }
        logger.info("Resolved public user projection by login normalizedLogin={}", userProjection.login)
        return userProjection
    }

    fun findPublicUsersByIds(
        userIds: Set<UUID>,
    ): Map<UUID, PublicUserProjection> {
        logger.info("Resolving public users by ids requestedCount={}", userIds.size)
        val usersById = userRepository.findPublicUsersByIds(userIds)
        logger.info("Resolved public users by ids requestedCount={} returnedCount={}", userIds.size, usersById.size)
        return usersById
    }

    fun findUserIdByLogin(
        login: String,
    ): UUID? {
        val normalizedLogin = normalizeLogin(login)
        logger.info("Resolving user id by login normalizedLogin={}", normalizedLogin)
        val userId = userRepository.findUserIdByLoginLower(normalizedLogin)
        logger.info("Resolved user id by login normalizedLogin={} found={}", normalizedLogin, userId != null)
        return userId
    }

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
        logger.info(
            "Searching users currentUserId={} loginFilter={} firstNameFilterPresent={} " +
                "lastNameFilterPresent={} matchMode={} excludeMe={} page={} size={}",
            currentUserId,
            login?.trim(),
            !firstName.isNullOrBlank(),
            !lastName.isNullOrBlank(),
            matchMode,
            excludeMe,
            page,
            size,
        )
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

        val usersResponse = PagedUsersResponse(
            items = users.map(PublicUserProjection::toResponse),
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = calculateTotalPages(totalElements, size),
        )
        logger.info(
            "User search completed currentUserId={} returnedItems={} totalElements={}",
            currentUserId,
            usersResponse.items.size,
            totalElements,
        )
        return usersResponse
    }

    fun normalizeLogin(login: String): String {
        val normalized = login.trim().lowercase(Locale.ROOT)
        if (normalized.length !in 3..32 || !LOGIN_REGEX.matches(normalized)) {
            logger.info("Login normalization rejected value={}", login.trim())
            throw ValidationException("Login must contain 3..32 latin letters, digits, dot, underscore or hyphen")
        }
        return normalized
    }

    fun buildDefaultAvatarUrl(firstName: String, lastName: String): String {
        val template = appProperties.user.defaultAvatarUrlTemplate
        val avatarUrl = if (template.contains("%s")) {
            template.format("$firstName+$lastName")
        } else {
            "$template$firstName+$lastName"
        }
        logger.info("Built default avatar url for initials={}+{}", firstName, lastName)
        return avatarUrl
    }

    private fun validatePaging(page: Int, size: Int) {
        if (page < 0) {
            logger.info("User search paging rejected because page={} is negative", page)
            throw ValidationException("page must be greater than or equal to 0")
        }
        if (size !in 1..ApiConstants.MAX_PAGE_SIZE) {
            logger.info("User search paging rejected because size={} is outside allowed range", size)
            throw ValidationException("size must be in range 1..${ApiConstants.MAX_PAGE_SIZE}")
        }
    }

    companion object {
        private val LOGIN_REGEX = Regex("^[a-z0-9._-]+$")
        private val logger = LoggerFactory.getLogger(UserService::class.java)
    }
}
