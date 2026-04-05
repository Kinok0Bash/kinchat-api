package ru.kinoko.kinchat.exception

import org.springframework.http.HttpStatus
import ru.kinoko.kinchat.util.ApiErrorCodes

class UnauthorizedException(message: String) :
    ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCodes.UNAUTHORIZED, message)
