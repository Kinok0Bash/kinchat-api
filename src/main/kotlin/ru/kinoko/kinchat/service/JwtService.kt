package ru.kinoko.kinchat.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.kinoko.kinchat.dto.AuthenticatedUser
import ru.kinoko.kinchat.exception.UnauthorizedException
import ru.kinoko.kinchat.properties.AppProperties
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    private val appProperties: AppProperties,
) {
    private val signKey: SecretKey = Keys.hmacShaKeyFor(
        appProperties.auth.secret.toByteArray(StandardCharsets.UTF_8),
    )

    init {
        logger.info(
            "JwtService initialized accessLifetimeSeconds={} refreshLifetimeSeconds={}",
            appProperties.auth.accessLifeTime.toSeconds(),
            appProperties.auth.refreshLifeTime.toSeconds(),
        )
    }

    fun generateAccessToken(userId: UUID, login: String): String {
        logger.info("Generating access token userId={} login={}", userId, login)
        return generateToken(
            userId = userId,
            login = login,
            type = ACCESS_TOKEN_TYPE,
            ttlMillis = appProperties.auth.accessLifeTime.toMillis(),
        )
    }

    fun generateRefreshToken(userId: UUID, login: String): String {
        logger.info("Generating refresh token userId={} login={}", userId, login)
        return generateToken(
            userId = userId,
            login = login,
            type = REFRESH_TOKEN_TYPE,
            ttlMillis = appProperties.auth.refreshLifeTime.toMillis(),
        )
    }

    fun parseAccessToken(token: String): AuthenticatedUser {
        logger.info("Parsing access token")
        return parseToken(token, ACCESS_TOKEN_TYPE)
    }

    fun parseRefreshToken(token: String): AuthenticatedUser {
        logger.info("Parsing refresh token")
        return parseToken(token, REFRESH_TOKEN_TYPE)
    }

    fun accessTokenLifetimeSeconds(): Long = appProperties.auth.accessLifeTime.toSeconds()

    private fun generateToken(userId: UUID, login: String, type: String, ttlMillis: Long): String {
        logger.info(
            "Building jwt token type={} userId={} login={} ttlSeconds={}",
            type,
            userId,
            login,
            ttlMillis / MILLIS_IN_SECOND,
        )
        return Jwts.builder()
            .subject(login)
            .claim(USER_ID_CLAIM, userId.toString())
            .claim(TOKEN_TYPE_CLAIM, type)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + ttlMillis))
            .signWith(signKey)
            .compact()
    }

    private fun parseToken(token: String, expectedType: String): AuthenticatedUser {
        val claims = try {
            Jwts.parser()
                .verifyWith(signKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (exception: Exception) {
            logger.info(
                "JWT parsing failed expectedType={} cause={}",
                expectedType,
                exception.javaClass.simpleName,
            )
            throw UnauthorizedException("Token is invalid or expired")
        }

        if (claims.get(TOKEN_TYPE_CLAIM, String::class.java) != expectedType) {
            logger.info(
                "JWT parsing rejected because token type did not match expectedType={} actualType={}",
                expectedType,
                claims.get(TOKEN_TYPE_CLAIM, String::class.java),
            )
            throw UnauthorizedException("Token type is invalid")
        }

        val userId = claims.get(USER_ID_CLAIM, String::class.java)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: run {
                logger.info("JWT parsing rejected because user id claim is invalid expectedType={}", expectedType)
                throw UnauthorizedException("Token subject is invalid")
            }
        val login = claims.subject?.takeIf(String::isNotBlank)
            ?: run {
                logger.info("JWT parsing rejected because subject is blank expectedType={}", expectedType)
                throw UnauthorizedException("Token subject is invalid")
            }

        logger.info("JWT parsing completed expectedType={} userId={} login={}", expectedType, userId, login)
        return AuthenticatedUser(userId = userId, login = login)
    }

    companion object {
        private const val ACCESS_TOKEN_TYPE = "ACCESS"
        private const val REFRESH_TOKEN_TYPE = "REFRESH"
        private const val MILLIS_IN_SECOND = 1000
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val USER_ID_CLAIM = "uid"
        private val logger = LoggerFactory.getLogger(JwtService::class.java)
    }
}
