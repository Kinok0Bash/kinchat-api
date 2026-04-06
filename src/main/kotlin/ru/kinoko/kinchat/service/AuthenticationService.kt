package ru.kinoko.kinchat.service

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import ru.kinoko.kinchat.dto.auth.AuthLoginRequest
import ru.kinoko.kinchat.dto.auth.AuthRegisterRequest
import ru.kinoko.kinchat.dto.auth.AuthTokensResponse
import ru.kinoko.kinchat.dto.auth.LogoutResponse
import ru.kinoko.kinchat.exception.ConflictException
import ru.kinoko.kinchat.exception.UnauthorizedException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.properties.AppProperties
import ru.kinoko.kinchat.dto.jooq.UserRepository
import ru.kinoko.kinchat.dto.AuthenticatedUser
import java.time.Duration
import java.util.UUID

@Service
class AuthenticationService(
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val appProperties: AppProperties,
) {
    fun register(request: AuthRegisterRequest, response: HttpServletResponse): AuthTokensResponse {
        val login = userService.normalizeLogin(request.login)
        val firstName = request.firstName.trim()
        val lastName = request.lastName.trim()
        validateNames(firstName, lastName)

        if (userRepository.findAuthByLoginLower(login) != null) {
            throw ConflictException("User with this login already exists")
        }

        val userId = UUID.randomUUID()
        val publicUser = userRepository.createUser(
            userId = userId,
            login = login,
            loginLower = login,
            passwordHash = passwordEncoder.encode(request.password),
            firstName = firstName,
            lastName = lastName,
            avatarUrl = userService.buildDefaultAvatarUrl(firstName, lastName),
        )

        return issueTokens(AuthenticatedUser(publicUser.userId, publicUser.login), response)
    }

    fun login(request: AuthLoginRequest, response: HttpServletResponse): AuthTokensResponse {
        val login = userService.normalizeLogin(request.login)
        val authUser = userRepository.findAuthByLoginLower(login)
            ?: throw UnauthorizedException("Invalid login or password")

        if (!passwordEncoder.matches(request.password, authUser.passwordHash)) {
            throw UnauthorizedException("Invalid login or password")
        }

        return issueTokens(AuthenticatedUser(authUser.userId, authUser.login), response)
    }

    fun refresh(refreshToken: String?, response: HttpServletResponse): AuthTokensResponse {
        val token = refreshToken?.trim().orEmpty()
        if (token.isBlank()) {
            throw UnauthorizedException("Refresh token is missing")
        }

        val user = jwtService.parseRefreshToken(token)
        userService.getPublicUserProjectionById(user.userId)
        return issueTokens(user, response)
    }

    fun logout(response: HttpServletResponse): LogoutResponse {
        clearRefreshCookie(response)
        return LogoutResponse("Выход выполнен успешно")
    }

    fun me(currentUser: AuthenticatedUser) = userService.getPublicUserById(currentUser.userId)

    private fun issueTokens(user: AuthenticatedUser, response: HttpServletResponse): AuthTokensResponse {
        val accessToken = jwtService.generateAccessToken(user.userId, user.login)
        val refreshToken = jwtService.generateRefreshToken(user.userId, user.login)
        setRefreshCookie(response, refreshToken)

        return AuthTokensResponse(
            accessToken = accessToken,
            expiresIn = jwtService.accessTokenLifetimeSeconds(),
            user = userService.getPublicUserById(user.userId),
        )
    }

    private fun setRefreshCookie(response: HttpServletResponse, refreshToken: String) {
        val cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .sameSite("None")
            .maxAge(appProperties.auth.refreshLifeTime)
            .build()

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    private fun clearRefreshCookie(response: HttpServletResponse) {
        val cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .sameSite("None")
            .maxAge(Duration.ZERO)
            .build()

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    private fun validateNames(firstName: String, lastName: String) {
        if (firstName.isBlank() || lastName.isBlank()) {
            throw ValidationException("firstName and lastName must not be blank")
        }
    }

    companion object {
        const val REFRESH_COOKIE_NAME = "refreshToken"
    }
}
