package ru.kinoko.kinchat.exception

import org.springframework.http.HttpStatus
import ru.kinoko.kinchat.util.ApiErrorCodes

class ForbiddenException(message: String) :
    ApiException(HttpStatus.FORBIDDEN, ApiErrorCodes.FORBIDDEN, message)
