package ru.kinoko.kinchat.exception

import org.springframework.http.HttpStatus
import ru.kinoko.kinchat.util.ApiErrorCodes

class ConflictException(message: String) :
    ApiException(HttpStatus.CONFLICT, ApiErrorCodes.CONFLICT, message)
