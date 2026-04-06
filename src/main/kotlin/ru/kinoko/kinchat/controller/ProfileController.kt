package ru.kinoko.kinchat.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.kinoko.kinchat.dto.common.ErrorResponse
import ru.kinoko.kinchat.dto.user.PublicUserResponse
import ru.kinoko.kinchat.dto.user.UpdateProfileRequest
import ru.kinoko.kinchat.security.CurrentUserProvider
import ru.kinoko.kinchat.service.ProfileService

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "Профиль текущего пользователя")
@SecurityRequirement(name = "bearerAuth")
class ProfileController(
    private val profileService: ProfileService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "Получить профиль текущего пользователя")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Профиль текущего пользователя",
                content = [Content(schema = Schema(implementation = PublicUserResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Требуется авторизация",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun getProfile(): ResponseEntity<PublicUserResponse> = ResponseEntity.ok(
        profileService.getCurrentUserProfile(currentUserProvider.getCurrentUser().userId),
    )

    @PatchMapping
    @Operation(summary = "Частично обновить профиль текущего пользователя")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Профиль обновлён",
                content = [Content(schema = Schema(implementation = PublicUserResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Некорректный запрос",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Требуется авторизация",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Login уже занят",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun patchProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ResponseEntity<PublicUserResponse> = ResponseEntity.ok(
        profileService.updateCurrentUserProfile(currentUserProvider.getCurrentUser().userId, request),
    )
}
