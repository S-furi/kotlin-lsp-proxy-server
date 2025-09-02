package io.github.sfuri.proxy.lsp.server.ws

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.sfuri.proxy.lsp.server.LspProject
import io.github.sfuri.proxy.lsp.server.LspProxy
import io.github.sfuri.proxy.lsp.server.model.Project
import io.github.sfuri.proxy.lsp.server.model.toCompletion
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

data class CompletionWSRequest(
    val project: Project,
    val line: Int,
    val ch: Int,
)

@Component
class CompletionWebSocketHandler : TextWebSocketHandler() {
    private val objectMapper = ObjectMapper()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + CoroutineName("lsp-completion"))
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.info("WebSocket connection established: ${session.id}")
        val userId = extractClientId(session)
        session.attributes["userId"] = userId ?: session.id
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        session.runSafely {
            val request = objectMapper.readValue(message.payload, CompletionWSRequest::class.java)
            val userId = session.attributes["userId"] as String

            val completions = LspProxy.getCompletionsForUser(userId, request.project, request.line, request.ch)
                .mapNotNull { it.toCompletion() }

            sendMessage(TextMessage(objectMapper.writeValueAsString(completions)))
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.error("Transport error: ${exception.message}")
        exception.printStackTrace(System.err)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        LspProxy.closeProject(session.attributes["userId"] as String)
        log.info("WebSocket connection closed: ${session.id}, with status: $status")
    }

    private fun WebSocketSession.runSafely(body: suspend WebSocketSession.() -> Unit) =
        try {
            coroutineScope.launch { body() }
        } catch (e: Exception) {
            val msg = "Error handling WebSocket message: ${e.message}"
            log.error(msg)
            sendMessage(buildError(msg))
        }

    private fun extractClientId(session: WebSocketSession): String? {
        val cookieHeader = session.handshakeHeaders["cookie"]?.first() ?: return null
        val cookies = cookieHeader.split(";")
        return cookies
            .map { it.trim().split("=", limit = 2) }
            .find { it.size == 2 && it[0].startsWith("clientId=") }
            ?.get(1)
    }

    private fun buildError(msg: String) =
        TextMessage(objectMapper.writeValueAsString(mapOf("error" to msg)))
}