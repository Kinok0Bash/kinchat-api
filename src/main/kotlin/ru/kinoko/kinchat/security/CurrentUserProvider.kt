package ru.kinoko.kinchat.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import ru.kinoko.kinchat.dto.AuthenticatedUser
import ru.kinoko.kinchat.exception.UnauthorizedException

@Component
class CurrentUserProvider {
    fun getCurrentUser(): AuthenticatedUser = SecurityContextHolder.getContext().authentication
        ?.principal as? AuthenticatedUser
        ?: throw UnauthorizedException("Authentication is required")
}
