package ru.kinoko.kinchat.controller.advice

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
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
    fun handleApiException(exception: ApiException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.info(
            "Handling api exception method={} path={} status={} code={} message={}",
            request.method,
            request.requestURI,
            exception.status.value(),
            exception.code,
            exception.message,
        )
        return buildResponse(exception.status, exception.code, exception.message, request)
    }

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
                .ifBlank { "РќРµРєРѕСЂСЂРµРєС‚РЅС‹Р№ Р·Р°РїСЂРѕСЃ" }

            is ConstraintViolationException -> exception.constraintViolations
                .joinToString("; ") { it.message }
                .ifBlank { "РќРµРєРѕСЂСЂРµРєС‚РЅС‹Р№ Р·Р°РїСЂРѕСЃ" }

            is MethodArgumentTypeMismatchException -> buildTypeMismatchMessage(exception.name)
            else -> exception.message ?: "РќРµРєРѕСЂСЂРµРєС‚РЅС‹Р№ Р·Р°РїСЂРѕСЃ"
        }
        logger.info(
            "Handling bad request exception method={} path={} type={} message={}",
            request.method,
            request.requestURI,
            exception.javaClass.simpleName,
            message,
        )

        return buildResponse(HttpStatus.BAD_REQUEST, ApiErrorCodes.VALIDATION_ERROR, message, request)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handlePayloadTooLarge(
        exception: MaxUploadSizeExceededException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val message = exception.message ?: "РџСЂРµРІС‹С€РµРЅ РґРѕРїСѓСЃС‚РёРјС‹Р№ СЂР°Р·РјРµСЂ С„Р°Р№Р»Р°"
        logger.info(
            "Handling payload too large exception method={} path={} message={}",
            request.method,
            request.requestURI,
            message,
        )
        return buildResponse(
            status = HttpStatus.PAYLOAD_TOO_LARGE,
            code = ApiErrorCodes.PAYLOAD_TOO_LARGE,
            message = message,
            request = request,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error(
            "Handling unexpected exception method={} path={} type={}",
            request.method,
            request.requestURI,
            exception.javaClass.simpleName,
            exception,
        )
        return buildResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = ApiErrorCodes.INTERNAL_ERROR,
            message = "Р’РЅСѓС‚СЂРµРЅРЅСЏСЏ РѕС€РёР±РєР° СЃРµСЂРІРёСЃР°",
            request = request,
        )
    }

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

    private fun buildTypeMismatchMessage(parameterName: String): String =
        "РќРµРєРѕСЂСЂРµРєС‚РЅРѕРµ Р·РЅР°С‡РµРЅРёРµ РїР°СЂР°РјРµС‚СЂР° $parameterName"

    companion object {
        private val logger = LoggerFactory.getLogger(ApiControllerAdvice::class.java)
    }
}
