package io.github.sfuri.proxy.lsp.server.components

import io.github.sfuri.proxy.lsp.server.LspProxy
import jakarta.annotation.PreDestroy
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class LspClientBootstrap {

    @EventListener(ApplicationReadyEvent::class)
    suspend fun onRead() {
        LspProxy.initializeClient()
    }

    @PreDestroy
    fun onShutdown() {
        LspProxy.closeAllProjects()
    }
}