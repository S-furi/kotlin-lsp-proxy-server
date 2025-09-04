package io.github.sfuri.proxy.lsp.server.controllers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/monitoring")
class MonitoringController {

    @Autowired
    private lateinit var webSocketHandler: CompletionWebSocketHandler

    @GetMapping("/active-connections")
    fun getActiveConnections(): Map<String, Any> {
        return mapOf(
            "activeConnections" to webSocketHandler.activeSessionCount,
            "timestamp" to System.currentTimeMillis()
        )
    }
}