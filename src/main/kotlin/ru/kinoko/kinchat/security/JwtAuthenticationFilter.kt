package ru.kinoko.kinchat.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.kinoko.kinchat.dto.common.ErrorResponse
import ru.kinoko.kinchat.exception.UnauthorizedException
import ru.kinoko.kinchat.service.JwtService
import ru.kinoko.kinchat.util.ApiErrorCodes
import java.time.OffsetDateTime

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        log.info(
            "Processing authorization header method={} path={} headerPresent={}",
            request.method,
            request.requestURI,
            !header.isNullOrBlank(),
        )

        if (header.isNullOrBlank()) {
            log.info("Proceeding without authentication method={} path={}", request.method, request.requestURI)
            filterChain.doFilter(request, response)
            return
        }

        if (!header.startsWith(BEARER_PREFIX)) {
            log.info("Rejecting request because authorization scheme is invalid path={}", request.requestURI)
            writeUnauthorizedResponse(response, request.requestURI, "Authorization header must use Bearer scheme")
            return
        }

        val token = header.removePrefix(BEARER_PREFIX).trim()
        if (token.isBlank()) {
            log.info("Rejecting request because access token is blank path={}", request.requestURI)
            writeUnauthorizedResponse(response, request.requestURI, "Access token is missing")
            return
        }

        try {
            val authenticatedUser = jwtService.parseAccessToken(token)
            val authentication = UsernamePasswordAuthenticationToken(
                authenticatedUser,
                token,
                AuthorityUtils.NO_AUTHORITIES,
            )
            SecurityContextHolder.getContext().authentication = authentication
            log.info(
                "Authentication established method={} path={} userId={} login={}",
                request.method,
                request.requestURI,
                authenticatedUser.userId,
                authenticatedUser.login,
            )
            filterChain.doFilter(request, response)
        } catch (exception: UnauthorizedException) {
            log.info(
                "Authentication failed method={} path={} reason={}",
                request.method,
                request.requestURI,
                exception.message,
            )
            SecurityContextHolder.clearContext()
            writeUnauthorizedResponse(response, request.requestURI, exception.message)
        }
    }

    private fun writeUnauthorizedResponse(response: HttpServletResponse, path: String, message: String) {
        log.info("Writing unauthorized response path={} message={}", path, message)
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse(
                    timestamp = OffsetDateTime.now(),
                    status = HttpServletResponse.SC_UNAUTHORIZED,
                    code = ApiErrorCodes.UNAUTHORIZED,
                    message = message,
                    path = path,
                    traceId = null,
                ),
            ),
        )
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }
}
