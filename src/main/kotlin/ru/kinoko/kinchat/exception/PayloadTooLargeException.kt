package ru.kinoko.kinchat.exception

import org.springframework.http.HttpStatus
import ru.kinoko.kinchat.util.ApiErrorCodes

class PayloadTooLargeException(message: String) :
    ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCodes.PAYLOAD_TOO_LARGE, message)
