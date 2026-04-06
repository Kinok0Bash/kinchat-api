package ru.kinoko.kinchat.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.kinoko.kinchat.dto.chat.ChatSummaryResponse
import ru.kinoko.kinchat.dto.chat.CreateDirectChatRequest
import ru.kinoko.kinchat.dto.chat.MessageResponse
import ru.kinoko.kinchat.dto.chat.PagedChatsResponse
import ru.kinoko.kinchat.dto.chat.PagedMessagesResponse
import ru.kinoko.kinchat.dto.chat.UploadAttachmentRequest
import ru.kinoko.kinchat.dto.common.ErrorResponse
import ru.kinoko.kinchat.security.CurrentUserProvider
import ru.kinoko.kinchat.service.ChatService
import ru.kinoko.kinchat.util.ApiConstants
import java.util.*

@Validated
@RestController
@RequestMapping("/api/chats")
@Tag(name = "Chats", description = "Direct chats, история сообщений и upload вложений")
@SecurityRequirement(name = "bearerAuth")
class ChatController(
    private val chatService: ChatService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "Список чатов текущего пользователя")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Страница чатов",
                content = [Content(schema = Schema(implementation = PagedChatsResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Требуется авторизация",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ],
    )
    fun getChats(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = DEFAULT_CHAT_PAGE_SIZE) @Min(1) @Max(ApiConstants.MAX_PAGE_SIZE_LONG) size: Int,
    ): ResponseEntity<PagedChatsResponse> = ResponseEntity.ok(
        chatService.getChats(currentUserProvider.getCurrentUser(), page, size),
    )

    @PostMapping("/direct")
    @Operation(summary = "Создать или получить direct chat")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Чат найден или создан",
                content = [Content(schema = Schema(implementation = ChatSummaryResponse::class))]
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
            ApiResponse(
                responseCode = "404",
                description = "Собеседник не найден",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ],
    )
    fun createDirectChat(
        @Valid @RequestBody request: CreateDirectChatRequest,
    ): ResponseEntity<ChatSummaryResponse> = ResponseEntity.ok(
        chatService.createDirectChat(currentUserProvider.getCurrentUser(), request),
    )

    @GetMapping("/{chatId}/messages")
    @Operation(summary = "История сообщений чата")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Страница сообщений",
                content = [Content(schema = Schema(implementation = PagedMessagesResponse::class))]
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
            ApiResponse(
                responseCode = "403",
                description = "Нет доступа к чату",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Чат не найден",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
        ],
    )
    fun getMessages(
        @PathVariable chatId: UUID,

        @RequestParam(defaultValue = "0") @Min(0) page: Int,

        @RequestParam(defaultValue = DEFAULT_CHAT_PAGE_SIZE)
        @Min(1) @Max(ApiConstants.MAX_PAGE_SIZE_LONG)
        size: Int,
    ): ResponseEntity<PagedMessagesResponse> = ResponseEntity.ok(
        chatService.getMessages(currentUserProvider.getCurrentUser(), chatId, page, size),
    )

    @PostMapping(path = ["/{chatId}/attachments"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Загрузить файл или картинку в сообщение")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Файл загружен, сообщение создано",
                content = [Content(schema = Schema(implementation = MessageResponse::class))]
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
            ApiResponse(
                responseCode = "403",
                description = "Нет доступа к чату",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Чат не найден",
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
    fun uploadAttachment(
        @PathVariable chatId: UUID,
        @ModelAttribute request: UploadAttachmentRequest,
    ): ResponseEntity<MessageResponse> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(chatService.uploadAttachment(currentUserProvider.getCurrentUser(), chatId, request))

    companion object {
        private const val DEFAULT_CHAT_PAGE_SIZE = "50"
    }
}
