package ru.kinoko.kinchat.exception

import org.springframework.http.HttpStatus
import ru.kinoko.kinchat.util.ApiErrorCodes

class UnsupportedMediaTypeApiException(message: String) :
    ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiErrorCodes.UNSUPPORTED_MEDIA_TYPE, message)
