package ru.kinoko.kinchat.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import ru.kinoko.kinchat.dto.common.ErrorResponse
import ru.kinoko.kinchat.dto.user.AvatarUploadResponse
import ru.kinoko.kinchat.dto.user.MatchMode
import ru.kinoko.kinchat.dto.user.PagedUsersResponse
import ru.kinoko.kinchat.dto.user.PublicUserResponse
import ru.kinoko.kinchat.security.CurrentUserProvider
import ru.kinoko.kinchat.service.AvatarService
import ru.kinoko.kinchat.service.UserService
import ru.kinoko.kinchat.util.ApiConstants

@Validated
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Поиск пользователей, профиль по login и аватарка")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userService: UserService,
    private val avatarService: AvatarService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping("/search")
    @Operation(summary = "Поиск пользователей")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Страница результатов поиска",
                content = [Content(schema = Schema(implementation = PagedUsersResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Некорректный запрос",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Требуется авторизация",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ],
    )
    fun searchUsers(
        @Parameter(description = "Фильтр по login")
        @RequestParam(required = false) login: String?,

        @Parameter(description = "Фильтр по firstName")
        @RequestParam(required = false) firstName: String?,

        @Parameter(description = "Фильтр по lastName")
        @RequestParam(required = false) lastName: String?,

        @Parameter(description = "Режим совпадения для firstName/lastName")
        @RequestParam(defaultValue = "PARTIAL") matchMode: MatchMode,

        @Parameter(description = "Исключить текущего пользователя")
        @RequestParam(defaultValue = "true") excludeMe: Boolean,

        @Parameter(description = "Номер страницы")
        @RequestParam(defaultValue = "0") @Min(0) page: Int,

        @Parameter(description = "Размер страницы")
        @RequestParam(defaultValue = DEFAULT_SEARCH_PAGE_SIZE)
        @Min(1) @Max(ApiConstants.MAX_PAGE_SIZE_LONG)
        size: Int,
    ): ResponseEntity<PagedUsersResponse> {
        val currentUser = currentUserProvider.getCurrentUser()
        logger.info(
            "HTTP search users request received userId={} loginFilter={} firstNameFilterPresent={} " +
                "lastNameFilterPresent={} matchMode={} excludeMe={} page={} size={}",
            currentUser.userId,
            login?.trim(),
            !firstName.isNullOrBlank(),
            !lastName.isNullOrBlank(),
            matchMode,
            excludeMe,
            page,
            size,
        )
        val usersResponse = userService.searchUsers(
            currentUserId = currentUser.userId,
            login = login,
            firstName = firstName,
            lastName = lastName,
            matchMode = matchMode,
            excludeMe = excludeMe,
            page = page,
            size = size,
        )
        logger.info(
            "HTTP search users request completed userId={} returnedItems={} totalElements={}",
            currentUser.userId,
            usersResponse.items.size,
            usersResponse.totalElements,
        )
        return ResponseEntity.ok(usersResponse)
    }

    @GetMapping("/{login}")
    @Operation(summary = "Получить пользователя по login")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Пользователь найден",
                content = [Content(schema = Schema(implementation = PublicUserResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Требуется авторизация",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Пользователь не найден",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ],
    )
    fun getUserByLogin(@PathVariable login: String): ResponseEntity<PublicUserResponse> {
        val currentUser = currentUserProvider.getCurrentUser()
        logger.info("HTTP get user by login request received actorUserId={} targetLogin={}", currentUser.userId, login)
        val userResponse = userService.getPublicUserByLogin(login)
        logger.info(
            "HTTP get user by login request completed actorUserId={} resolvedLogin={}",
            currentUser.userId,
            userResponse.login,
        )
        return ResponseEntity.ok(userResponse)
    }

    @PutMapping("/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Заменить аватарку текущего пользователя")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Аватарка заменена",
                content = [Content(schema = Schema(implementation = AvatarUploadResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Некорректный файл",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Требуется авторизация",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "413",
                description = "Файл слишком большой",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "415",
                description = "Тип файла не поддерживается",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ],
    )
    fun uploadAvatar(
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<AvatarUploadResponse> {
        val currentUser = currentUserProvider.getCurrentUser()
        logger.info(
            "HTTP upload avatar request received userId={} sizeBytes={} contentType={}",
            currentUser.userId,
            file.size,
            file.contentType,
        )
        val avatarResponse = avatarService.uploadAvatar(currentUser, file)
        logger.info("HTTP upload avatar request completed userId={}", currentUser.userId)
        return ResponseEntity.ok(avatarResponse)
    }

    companion object {
        private const val DEFAULT_SEARCH_PAGE_SIZE = "20"
        private val logger = LoggerFactory.getLogger(UserController::class.java)
    }
}
