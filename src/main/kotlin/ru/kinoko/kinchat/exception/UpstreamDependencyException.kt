package ru.kinoko.kinchat.exception

import org.springframework.http.HttpStatus
import ru.kinoko.kinchat.util.ApiErrorCodes

class UpstreamDependencyException(message: String, cause: Throwable? = null) :
    ApiException(HttpStatus.BAD_GATEWAY, ApiErrorCodes.UPSTREAM_SERVICE_ERROR, message, cause)
