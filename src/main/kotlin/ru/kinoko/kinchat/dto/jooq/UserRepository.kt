package ru.kinoko.kinchat.dto.jooq

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import ru.kinoko.kinchat.dto.user.MatchMode
import ru.kinoko.kinchat.jooq.tables.references.USERS_AUTH
import ru.kinoko.kinchat.jooq.tables.references.USERS_PROFILE
import java.time.OffsetDateTime
import java.util.UUID

@Suppress("TooManyFunctions")
@Repository
class UserRepository(
    private val dsl: DSLContext,
) {
    fun findAuthByLoginLower(loginLower: String): AuthUserProjection? = dsl
        .selectFrom(USERS_AUTH)
        .where(USERS_AUTH.LOGIN_LOWER.eq(loginLower))
        .fetchOne { record -> record.toAuthProjection() }

    fun findAuthByUserId(userId: UUID): AuthUserProjection? = dsl
        .selectFrom(USERS_AUTH)
        .where(USERS_AUTH.USER_ID.eq(userId))
        .fetchOne { record -> record.toAuthProjection() }

    fun createUser(
        userId: UUID,
        login: String,
        loginLower: String,
        passwordHash: String,
        firstName: String,
        lastName: String,
        avatarUrl: String,
    ): PublicUserProjection = dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        val now = OffsetDateTime.now()

        tx.insertInto(USERS_AUTH)
            .set(USERS_AUTH.USER_ID, userId)
            .set(USERS_AUTH.LOGIN, login)
            .set(USERS_AUTH.LOGIN_LOWER, loginLower)
            .set(USERS_AUTH.PASSWORD_HASH, passwordHash)
            .set(USERS_AUTH.CREATED_AT, now)
            .execute()

        tx.insertInto(USERS_PROFILE)
            .set(USERS_PROFILE.USER_ID, userId)
            .set(USERS_PROFILE.FIRST_NAME, firstName)
            .set(USERS_PROFILE.LAST_NAME, lastName)
            .set(USERS_PROFILE.AVATAR_URL, avatarUrl)
            .set(USERS_PROFILE.AVATAR_OBJECT_KEY, null as String?)
            .set(USERS_PROFILE.UPDATED_AT, now)
            .execute()

        requireNotNull(findPublicUserById(tx, userId))
    }

    fun findPublicUserById(userId: UUID): PublicUserProjection? = findPublicUserById(dsl, userId)

    fun findPublicUserByLoginLower(loginLower: String): PublicUserProjection? = dsl
        .select(
            USERS_AUTH.USER_ID,
            USERS_AUTH.LOGIN,
            USERS_PROFILE.FIRST_NAME,
            USERS_PROFILE.LAST_NAME,
            USERS_PROFILE.AVATAR_URL,
        )
        .from(USERS_AUTH)
        .join(USERS_PROFILE)
        .on(USERS_PROFILE.USER_ID.eq(USERS_AUTH.USER_ID))
        .where(USERS_AUTH.LOGIN_LOWER.eq(loginLower))
        .fetchOne { record -> record.toPublicProjection() }

    fun findPublicUsersByIds(userIds: Set<UUID>): Map<UUID, PublicUserProjection> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        return dsl
            .select(
                USERS_AUTH.USER_ID,
                USERS_AUTH.LOGIN,
                USERS_PROFILE.FIRST_NAME,
                USERS_PROFILE.LAST_NAME,
                USERS_PROFILE.AVATAR_URL,
            )
            .from(USERS_AUTH)
            .join(USERS_PROFILE)
            .on(USERS_PROFILE.USER_ID.eq(USERS_AUTH.USER_ID))
            .where(USERS_AUTH.USER_ID.`in`(userIds))
            .fetch { record -> record.toPublicProjection() }
            .associateBy(PublicUserProjection::userId)
    }

    fun countUsers(
        currentUserId: UUID,
        login: String?,
        firstName: String?,
        lastName: String?,
        matchMode: MatchMode,
        excludeMe: Boolean,
    ): Long = dsl.fetchCount(
        dsl.select()
            .from(USERS_AUTH)
            .join(USERS_PROFILE)
            .on(USERS_PROFILE.USER_ID.eq(USERS_AUTH.USER_ID))
            .where(buildSearchCondition(currentUserId, login, firstName, lastName, matchMode, excludeMe)),
    ).toLong()

    fun searchUsers(
        currentUserId: UUID,
        login: String?,
        firstName: String?,
        lastName: String?,
        matchMode: MatchMode,
        excludeMe: Boolean,
        page: Int,
        size: Int,
    ): List<PublicUserProjection> = dsl
        .select(
            USERS_AUTH.USER_ID,
            USERS_AUTH.LOGIN,
            USERS_PROFILE.FIRST_NAME,
            USERS_PROFILE.LAST_NAME,
            USERS_PROFILE.AVATAR_URL,
        )
        .from(USERS_AUTH)
        .join(USERS_PROFILE)
        .on(USERS_PROFILE.USER_ID.eq(USERS_AUTH.USER_ID))
        .where(buildSearchCondition(currentUserId, login, firstName, lastName, matchMode, excludeMe))
        .orderBy(USERS_AUTH.LOGIN.asc())
        .limit(size)
        .offset(page * size)
        .fetch { record -> record.toPublicProjection() }

    fun findUserIdByLoginLower(loginLower: String): UUID? = dsl
        .select(USERS_AUTH.USER_ID)
        .from(USERS_AUTH)
        .where(USERS_AUTH.LOGIN_LOWER.eq(loginLower))
        .fetchOne(USERS_AUTH.USER_ID)

    fun findAvatarInfoByUserId(userId: UUID): AvatarInfoProjection? = dsl
        .select(USERS_PROFILE.AVATAR_URL, USERS_PROFILE.AVATAR_OBJECT_KEY)
        .from(USERS_PROFILE)
        .where(USERS_PROFILE.USER_ID.eq(userId))
        .fetchOne { record ->
            AvatarInfoProjection(
                avatarUrl = record.get(USERS_PROFILE.AVATAR_URL)!!,
                avatarObjectKey = record.get(USERS_PROFILE.AVATAR_OBJECT_KEY),
            )
        }

    fun updateProfile(
        userId: UUID,
        login: String?,
        loginLower: String?,
        firstName: String?,
        lastName: String?,
        avatarUrl: String?,
    ): PublicUserProjection? = dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)

        if (login != null && loginLower != null) {
            val updatedAuthRows = tx
                .update(USERS_AUTH)
                .set(USERS_AUTH.LOGIN, login)
                .set(USERS_AUTH.LOGIN_LOWER, loginLower)
                .where(USERS_AUTH.USER_ID.eq(userId))
                .execute()

            if (updatedAuthRows == 0) {
                return@transactionResult null
            }
        }

        val profileUpdate = tx.update(USERS_PROFILE)
            .set(USERS_PROFILE.UPDATED_AT, OffsetDateTime.now())
        var shouldUpdateProfile = false

        firstName?.let { updatedFirstName ->
            profileUpdate.set(USERS_PROFILE.FIRST_NAME, updatedFirstName)
            shouldUpdateProfile = true
        }

        lastName?.let { updatedLastName ->
            profileUpdate.set(USERS_PROFILE.LAST_NAME, updatedLastName)
            shouldUpdateProfile = true
        }

        avatarUrl?.let { updatedAvatarUrl ->
            profileUpdate.set(USERS_PROFILE.AVATAR_URL, updatedAvatarUrl)
            shouldUpdateProfile = true
        }

        if (shouldUpdateProfile) {
            val updatedProfileRows = profileUpdate
                .where(USERS_PROFILE.USER_ID.eq(userId))
                .execute()

            if (updatedProfileRows == 0) {
                return@transactionResult null
            }
        }

        findPublicUserById(tx, userId)
    }

    fun updateAvatar(
        userId: UUID,
        avatarUrl: String,
        avatarObjectKey: String?,
    ): PublicUserProjection? {
        val updated = dsl
            .update(USERS_PROFILE)
            .set(USERS_PROFILE.AVATAR_URL, avatarUrl)
            .set(USERS_PROFILE.AVATAR_OBJECT_KEY, avatarObjectKey)
            .set(USERS_PROFILE.UPDATED_AT, OffsetDateTime.now())
            .where(USERS_PROFILE.USER_ID.eq(userId))
            .execute()

        if (updated == 0) {
            return null
        }

        return findPublicUserById(userId)
    }

    private fun findPublicUserById(context: DSLContext, userId: UUID): PublicUserProjection? = context
        .select(
            USERS_AUTH.USER_ID,
            USERS_AUTH.LOGIN,
            USERS_PROFILE.FIRST_NAME,
            USERS_PROFILE.LAST_NAME,
            USERS_PROFILE.AVATAR_URL,
        )
        .from(USERS_AUTH)
        .join(USERS_PROFILE)
        .on(USERS_PROFILE.USER_ID.eq(USERS_AUTH.USER_ID))
        .where(USERS_AUTH.USER_ID.eq(userId))
        .fetchOne { record -> record.toPublicProjection() }

    private fun buildSearchCondition(
        currentUserId: UUID,
        login: String?,
        firstName: String?,
        lastName: String?,
        matchMode: MatchMode,
        excludeMe: Boolean,
    ): Condition {
        var condition: Condition = DSL.trueCondition()

        login?.takeIf(String::isNotBlank)?.let { normalizedLogin ->
            condition = condition.and(USERS_AUTH.LOGIN_LOWER.like("%$normalizedLogin%"))
        }

        firstName?.takeIf(String::isNotBlank)?.let { value ->
            condition = condition.and(nameCondition(USERS_PROFILE.FIRST_NAME, value, matchMode))
        }

        lastName?.takeIf(String::isNotBlank)?.let { value ->
            condition = condition.and(nameCondition(USERS_PROFILE.LAST_NAME, value, matchMode))
        }

        if (excludeMe) {
            condition = condition.and(USERS_AUTH.USER_ID.ne(currentUserId))
        }

        return condition
    }

    private fun nameCondition(field: Field<String?>, value: String, matchMode: MatchMode): Condition {
        val normalized = value.lowercase()
        return when (matchMode) {
            MatchMode.EXACT -> DSL.lower(field).eq(normalized)
            MatchMode.PARTIAL -> DSL.lower(field).like("%$normalized%")
        }
    }

    private fun Record.toAuthProjection(): AuthUserProjection = AuthUserProjection(
        userId = get(USERS_AUTH.USER_ID)!!,
        login = get(USERS_AUTH.LOGIN)!!,
        loginLower = get(USERS_AUTH.LOGIN_LOWER)!!,
        passwordHash = get(USERS_AUTH.PASSWORD_HASH)!!,
        createdAt = get(USERS_AUTH.CREATED_AT)!!,
    )

    private fun Record.toPublicProjection(): PublicUserProjection = PublicUserProjection(
        userId = get(USERS_AUTH.USER_ID)!!,
        login = get(USERS_AUTH.LOGIN)!!,
        firstName = get(USERS_PROFILE.FIRST_NAME)!!,
        lastName = get(USERS_PROFILE.LAST_NAME)!!,
        avatarUrl = get(USERS_PROFILE.AVATAR_URL)!!,
    )
}
