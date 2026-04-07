package ru.kinoko.kinchat.util

object SecurityPaths {
    val PUBLIC_ENDPOINT_PATTERNS = arrayOf(
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/logout",
        "/api/docs/**",
        "/api/swagger/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health",
        "/error",
        "/ws",
    )
}
