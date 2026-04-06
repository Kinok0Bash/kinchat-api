package ru.kinoko.kinchat.security

import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import ru.kinoko.kinchat.dto.AuthenticatedUser
import ru.kinoko.kinchat.exception.UnauthorizedException

@Component
class CurrentUserProvider {
    fun getCurrentUser(): AuthenticatedUser {
        val currentUser = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
            ?: run {
                logger.info("Current user resolution failed because authentication is missing")
                throw UnauthorizedException("Authentication is required")
            }
        logger.info(
            "Resolved current user from security context userId={} login={}",
            currentUser.userId,
            currentUser.login,
        )
        return currentUser
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CurrentUserProvider::class.java)
    }
}
