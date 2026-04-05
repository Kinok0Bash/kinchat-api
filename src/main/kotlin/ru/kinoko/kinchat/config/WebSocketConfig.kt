package ru.kinoko.kinchat.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import ru.kinoko.kinchat.websocket.KinchatWebSocketHandler

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val kinchatWebSocketHandler: KinchatWebSocketHandler,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(kinchatWebSocketHandler, "/ws")
            .setAllowedOriginPatterns("*")
    }
}
