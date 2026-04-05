package ru.kinoko.kinchat.exception

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
