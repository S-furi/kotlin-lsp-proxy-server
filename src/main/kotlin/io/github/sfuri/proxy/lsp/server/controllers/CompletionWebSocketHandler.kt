package io.github.sfuri.proxy.lsp.server.controllers

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.sfuri.proxy.lsp.server.LspProxy
import io.github.sfuri.proxy.lsp.server.model.Completion
import io.github.sfuri.proxy.lsp.server.model.CompletionParser.toCompletion
import io.github.sfuri.proxy.lsp.server.model.Project
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

@Component
class CompletionWebSocketHandler : TextWebSocketHandler(), CoroutineScope {
    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext = Dispatchers.IO + job + CoroutineName("CompletionWebSocketHandler")

    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }

    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()

    val activeSessionCount: Int
        get() = activeSessions.size

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val sessionId = session.id
        activeSessions[sessionId] = session

        launch {
            logger.info("Client connected: $sessionId")
            with(session) {
                LspProxy.onUserConnected(sessionId)
                sendMessage(Response.init(sessionId))
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) =
        handleUserDisconnected(session.id).also {
            logger.info("Client disconnected: ${session.id}")
        }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) =
        handleUserDisconnected(session.id)

    private fun handleUserDisconnected(sessionId: String) {
        activeSessions.remove(sessionId)
        launch { LspProxy.onUserDisconnected(sessionId) }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        launch {
            val completionParams = session.decodeCompletionFromTextMessage(message) ?: return@launch
            handleCompletionRequest(session, completionParams)
        }
    }

    private suspend fun handleCompletionRequest(session: WebSocketSession, request: CompletionRequest) {
        LspProxy.getCompletionsForUser(session.id, request.project, request.line, request.ch)
            .mapNotNull { it.toCompletion() }
            .let { session.sendMessage(Response.completionResult(it)) }
    }

    private suspend fun WebSocketSession.decodeCompletionFromTextMessage(message: TextMessage): CompletionRequest? =
        try {
            objectMapper.readValue(message.payload, CompletionRequest::class.java)
        } catch (e: JsonProcessingException) {
            logger.warn("Invalid JSON from client: ${message.payload}")
            sendMessage(Response.error("Invalid JSON format for ${message.payload}: ${e.message}"))
            null
        }

    private suspend fun WebSocketSession.sendMessage(message: Response) {
        try {
            if (isOpen) {
                val json = objectMapper.writeValueAsString(message)
                withContext(Dispatchers.IO) {
                    sendMessage(TextMessage(json))
                }
            }
        } catch (e: Exception) {
            logger.error("Error sending message to client ${id}: ${e.message}")

        }
    }

    @PreDestroy
    fun cleanup() {
        job.cancel()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }
}
private data class CompletionRequest(
    val project: Project,
    val line: Int,
    val ch: Int,
)

private data class Response(
    val type: String,
    val message: String,
    val additionalData: Map<String, Any> = emptyMap()
) {
    companion object {
        fun error(message: String) = Response("error", message)
        fun init(sessionId: String) = Response(
            type = "connection_established",
            message = "Connection established and users's document opened",
            additionalData = mapOf("sessionId" to sessionId)
        )

        fun completionResult(completionResult: List<Completion>) =
            Response(
                type = "completions",
                message = "Completions received",
                additionalData = mapOf("completions" to completionResult)
            )
    }
}