package ru.kinoko.kinchat.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import ru.kinoko.kinchat.exception.UnauthorizedException
import ru.kinoko.kinchat.properties.AppProperties
import ru.kinoko.kinchat.dto.AuthenticatedUser
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

    fun generateAccessToken(userId: UUID, login: String): String = generateToken(
        userId = userId,
        login = login,
        type = ACCESS_TOKEN_TYPE,
        ttlMillis = appProperties.auth.accessLifeTime.toMillis(),
    )

    fun generateRefreshToken(userId: UUID, login: String): String = generateToken(
        userId = userId,
        login = login,
        type = REFRESH_TOKEN_TYPE,
        ttlMillis = appProperties.auth.refreshLifeTime.toMillis(),
    )

    fun parseAccessToken(token: String): AuthenticatedUser = parseToken(token, ACCESS_TOKEN_TYPE)

    fun parseRefreshToken(token: String): AuthenticatedUser = parseToken(token, REFRESH_TOKEN_TYPE)

    fun accessTokenLifetimeSeconds(): Long = appProperties.auth.accessLifeTime.toSeconds()

    private fun generateToken(userId: UUID, login: String, type: String, ttlMillis: Long): String = Jwts.builder()
        .subject(login)
        .claim(USER_ID_CLAIM, userId.toString())
        .claim(TOKEN_TYPE_CLAIM, type)
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + ttlMillis))
        .signWith(signKey)
        .compact()

    private fun parseToken(token: String, expectedType: String): AuthenticatedUser {
        val claims = try {
            Jwts.parser()
                .verifyWith(signKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: Exception) {
            throw UnauthorizedException("Token is invalid or expired")
        }

        if (claims.get(TOKEN_TYPE_CLAIM, String::class.java) != expectedType) {
            throw UnauthorizedException("Token type is invalid")
        }

        val userId = claims.get(USER_ID_CLAIM, String::class.java)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw UnauthorizedException("Token subject is invalid")
        val login = claims.subject?.takeIf(String::isNotBlank)
            ?: throw UnauthorizedException("Token subject is invalid")

        return AuthenticatedUser(userId = userId, login = login)
    }

    companion object {
        private const val ACCESS_TOKEN_TYPE = "ACCESS"
        private const val REFRESH_TOKEN_TYPE = "REFRESH"
        private const val TOKEN_TYPE_CLAIM = "typ"
        private const val USER_ID_CLAIM = "uid"
    }
}
