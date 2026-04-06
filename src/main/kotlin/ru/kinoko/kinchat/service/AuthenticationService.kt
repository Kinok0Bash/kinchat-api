package ru.kinoko.kinchat.service

import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import ru.kinoko.kinchat.dto.AuthenticatedUser
import ru.kinoko.kinchat.dto.auth.AuthLoginRequest
import ru.kinoko.kinchat.dto.auth.AuthRegisterRequest
import ru.kinoko.kinchat.dto.auth.AuthTokensResponse
import ru.kinoko.kinchat.dto.auth.LogoutResponse
import ru.kinoko.kinchat.dto.jooq.UserRepository
import ru.kinoko.kinchat.dto.user.PublicUserResponse
import ru.kinoko.kinchat.exception.ConflictException
import ru.kinoko.kinchat.exception.UnauthorizedException
import ru.kinoko.kinchat.exception.ValidationException
import ru.kinoko.kinchat.properties.AppProperties
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
        logger.info("Registering user login={}", login)
        validateNames(firstName, lastName)

        if (userRepository.findAuthByLoginLower(login) != null) {
            logger.info("Registration rejected because login={} already exists", login)
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

        logger.info("User record created userId={} login={}", publicUser.userId, publicUser.login)
        val tokensResponse = issueTokens(AuthenticatedUser(publicUser.userId, publicUser.login), response)
        logger.info("Registration completed userId={} login={}", publicUser.userId, publicUser.login)
        return tokensResponse
    }

    fun login(request: AuthLoginRequest, response: HttpServletResponse): AuthTokensResponse {
        val login = userService.normalizeLogin(request.login)
        logger.info("Authenticating user login={}", login)
        val authUser = userRepository.findAuthByLoginLower(login)
            ?: run {
                logger.info("Authentication rejected because login={} was not found", login)
                throw UnauthorizedException("Invalid login or password")
            }

        if (!passwordEncoder.matches(request.password, authUser.passwordHash)) {
            logger.info("Authentication rejected because password did not match for login={}", login)
            throw UnauthorizedException("Invalid login or password")
        }

        val tokensResponse = issueTokens(AuthenticatedUser(authUser.userId, authUser.login), response)
        logger.info("Authentication completed userId={} login={}", authUser.userId, authUser.login)
        return tokensResponse
    }

    fun refresh(refreshToken: String?, response: HttpServletResponse): AuthTokensResponse {
        val token = refreshToken?.trim().orEmpty()
        logger.info("Refreshing tokens cookiePresent={}", token.isNotBlank())
        if (token.isBlank()) {
            logger.info("Refresh rejected because refresh cookie is missing")
            throw UnauthorizedException("Refresh token is missing")
        }

        val user = jwtService.parseRefreshToken(token)
        val publicUser = userService.getPublicUserProjectionById(user.userId)
        val tokensResponse = issueTokens(AuthenticatedUser(publicUser.userId, publicUser.login), response)
        logger.info("Refresh completed userId={} login={}", publicUser.userId, publicUser.login)
        return tokensResponse
    }

    fun logout(response: HttpServletResponse): LogoutResponse {
        logger.info("Processing logout request")
        clearRefreshCookie(response)
        logger.info("Logout completed and refresh cookie cleared")
        return LogoutResponse("Р’С‹С…РѕРґ РІС‹РїРѕР»РЅРµРЅ СѓСЃРїРµС€РЅРѕ")
    }

    fun me(currentUser: AuthenticatedUser): PublicUserResponse {
        logger.info("Loading current user profile userId={} login={}", currentUser.userId, currentUser.login)
        val userResponse = userService.getPublicUserById(currentUser.userId)
        logger.info("Current user profile loaded login={}", userResponse.login)
        return userResponse
    }

    private fun issueTokens(user: AuthenticatedUser, response: HttpServletResponse): AuthTokensResponse {
        logger.info("Issuing tokens userId={} login={}", user.userId, user.login)
        val accessToken = jwtService.generateAccessToken(user.userId, user.login)
        val refreshToken = jwtService.generateRefreshToken(user.userId, user.login)
        setRefreshCookie(response, refreshToken)

        val tokensResponse = AuthTokensResponse(
            accessToken = accessToken,
            expiresIn = jwtService.accessTokenLifetimeSeconds(),
            user = userService.getPublicUserById(user.userId),
        )
        logger.info(
            "Tokens issued userId={} login={} accessTtlSeconds={}",
            user.userId,
            user.login,
            tokensResponse.expiresIn,
        )
        return tokensResponse
    }

    private fun setRefreshCookie(response: HttpServletResponse, refreshToken: String) {
        logger.info("Setting refresh cookie lifetimeSeconds={}", appProperties.auth.refreshLifeTime.toSeconds())
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
        logger.info("Clearing refresh cookie")
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
            logger.info(
                "Registration rejected because firstNameBlank={} lastNameBlank={}",
                firstName.isBlank(),
                lastName.isBlank(),
            )
            throw ValidationException("firstName and lastName must not be blank")
        }
    }

    companion object {
        const val REFRESH_COOKIE_NAME = "refreshToken"
        private val logger = LoggerFactory.getLogger(AuthenticationService::class.java)
    }
}
