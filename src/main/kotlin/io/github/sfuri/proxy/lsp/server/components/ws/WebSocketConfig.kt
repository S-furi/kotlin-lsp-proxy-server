package io.github.sfuri.proxy.lsp.server.components.ws

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig : WebSocketConfigurer {

    @Autowired
    private lateinit var completionWebSocketHandler: CompletionWebSocketHandler

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(completionWebSocketHandler, "/ws/complete")
            .setAllowedOriginPatterns("*")
    }
}