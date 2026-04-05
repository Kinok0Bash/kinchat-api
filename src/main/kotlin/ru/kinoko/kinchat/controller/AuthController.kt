package ru.kinoko.kinchat.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.kinoko.kinchat.dto.auth.AuthLoginRequest
import ru.kinoko.kinchat.dto.auth.AuthRegisterRequest
import ru.kinoko.kinchat.dto.auth.AuthTokensResponse
import ru.kinoko.kinchat.dto.auth.LogoutResponse
import ru.kinoko.kinchat.dto.common.ErrorResponse
import ru.kinoko.kinchat.dto.user.PublicUserResponse
import ru.kinoko.kinchat.security.CurrentUserProvider
import ru.kinoko.kinchat.service.AuthenticationService

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Регистрация, логин, refresh, logout и текущий пользователь")
class AuthController(
    private val authenticationService: AuthenticationService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping("/register")
    @Operation(summary = "Регистрация пользователя")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Регистрация успешна", content = [Content(schema = Schema(implementation = AuthTokensResponse::class))]),
            ApiResponse(responseCode = "400", description = "Некорректный запрос", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "409", description = "Логин уже занят", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun register(
        @Valid @RequestBody request: AuthRegisterRequest,
        response: HttpServletResponse,
    ): ResponseEntity<AuthTokensResponse> = ResponseEntity.ok(authenticationService.register(request, response))

    @PostMapping("/login")
    @Operation(summary = "Логин пользователя")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Логин успешен", content = [Content(schema = Schema(implementation = AuthTokensResponse::class))]),
            ApiResponse(responseCode = "400", description = "Некорректный запрос", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "401", description = "Неверный логин или пароль", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun login(
        @Valid @RequestBody request: AuthLoginRequest,
        response: HttpServletResponse,
    ): ResponseEntity<AuthTokensResponse> = ResponseEntity.ok(authenticationService.login(request, response))

    @PostMapping("/refresh")
    @Operation(
        summary = "Обновить access token по refresh cookie",
        security = [SecurityRequirement(name = "refreshTokenCookie")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Токены обновлены", content = [Content(schema = Schema(implementation = AuthTokensResponse::class))]),
            ApiResponse(responseCode = "401", description = "Refresh token отсутствует или невалиден", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun refresh(
        @CookieValue(value = AuthenticationService.REFRESH_COOKIE_NAME, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ResponseEntity<AuthTokensResponse> = ResponseEntity.ok(authenticationService.refresh(refreshToken, response))

    @PostMapping("/logout")
    @Operation(
        summary = "Выход пользователя",
        security = [SecurityRequirement(name = "refreshTokenCookie")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Выход выполнен", content = [Content(schema = Schema(implementation = LogoutResponse::class))]),
        ],
    )
    fun logout(response: HttpServletResponse): ResponseEntity<LogoutResponse> = ResponseEntity.ok(
        authenticationService.logout(response),
    )

    @GetMapping("/me")
    @Operation(
        summary = "Текущий пользователь",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Текущий пользователь", content = [Content(schema = Schema(implementation = PublicUserResponse::class))]),
            ApiResponse(responseCode = "401", description = "Требуется авторизация", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun me(): ResponseEntity<PublicUserResponse> = ResponseEntity.ok(
        authenticationService.me(currentUserProvider.getCurrentUser()),
    )
}
