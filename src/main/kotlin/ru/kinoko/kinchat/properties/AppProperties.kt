package ru.kinoko.kinchat.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val auth: AuthProperties,
    val user: UserProperties,
) {

    data class AuthProperties(
        val secret: String,
        val accessLifeTime: Duration,
        val refreshLifeTime: Duration,
        val wsTicketLifeTime: Duration,
    )

    data class UserProperties(
        val defaultAvatarUrlTemplate: String,
    )
}
