package ru.kinoko.kinchat.controller.advice

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import ru.kinoko.kinchat.dto.common.ErrorResponse
import ru.kinoko.kinchat.exception.ApiException
import ru.kinoko.kinchat.util.ApiErrorCodes
import java.time.OffsetDateTime

@RestControllerAdvice
class ApiControllerAdvice {
    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildResponse(exception.status, exception.code, exception.message, request)

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        ConstraintViolationException::class,
        HttpMessageNotReadableException::class,
        MissingServletRequestPartException::class,
        MultipartException::class,
        MethodArgumentTypeMismatchException::class,
        IllegalArgumentException::class,
    )
    fun handleBadRequest(exception: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = when (exception) {
            is MethodArgumentNotValidException -> exception.bindingResult.fieldErrors
                .joinToString("; ") { "${it.field}: ${it.defaultMessage ?: "invalid"}" }
                .ifBlank { "Некорректный запрос" }

            is ConstraintViolationException -> exception.constraintViolations
                .joinToString("; ") { it.message }
                .ifBlank { "Некорректный запрос" }

            is MethodArgumentTypeMismatchException -> "Некорректное значение параметра ${exception.name}"
            else -> exception.message ?: "Некорректный запрос"
        }

        return buildResponse(HttpStatus.BAD_REQUEST, ApiErrorCodes.VALIDATION_ERROR, message, request)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handlePayloadTooLarge(
        exception: MaxUploadSizeExceededException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = buildResponse(
        status = HttpStatus.PAYLOAD_TOO_LARGE,
        code = ApiErrorCodes.PAYLOAD_TOO_LARGE,
        message = exception.message ?: "Превышен допустимый размер файла",
        request = request,
    )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = ApiErrorCodes.INTERNAL_ERROR,
            message = "Внутренняя ошибка сервиса",
            request = request,
        )

    private fun buildResponse(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(status).body(
        ErrorResponse(
            timestamp = OffsetDateTime.now(),
            status = status.value(),
            code = code,
            message = message,
            path = request.requestURI,
            traceId = null,
        ),
    )
}
