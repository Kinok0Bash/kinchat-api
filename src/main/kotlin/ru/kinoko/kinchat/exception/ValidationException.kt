package ru.kinoko.kinchat.exception

import org.springframework.http.HttpStatus
import ru.kinoko.kinchat.util.ApiErrorCodes

class ValidationException(message: String) :
    ApiException(HttpStatus.BAD_REQUEST, ApiErrorCodes.VALIDATION_ERROR, message)
