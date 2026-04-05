package ru.kinoko.kinchat.exception

import org.springframework.http.HttpStatus
import ru.kinoko.kinchat.util.ApiErrorCodes

class NotFoundException(message: String) :
    ApiException(HttpStatus.NOT_FOUND, ApiErrorCodes.NOT_FOUND, message)
