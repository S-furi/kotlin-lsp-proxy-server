package io.github.sfuri.proxy.lsp.client

import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.slf4j.LoggerFactory
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class KotlinLSPClient {
    private val socket by lazy { Socket("127.0.0.1", 9999) }
    private val languageClient = KotlinLanguageClient()
    internal val languageServer: LanguageServer by lazy { getRemoteLanguageServer() }
    private lateinit var stopFuture: Future<Void>
    private val LOG = LoggerFactory.getLogger(this::class.java)

    fun initRequest(kotlinProjectRoot: String, projectName: String = "None"): CompletableFuture<Void> {
        val capabilities = getCompletionCapabilities()
        val workspaceFolders = listOf(WorkspaceFolder("file://$kotlinProjectRoot", projectName))

        val params = InitializeParams().apply {
            this.capabilities = capabilities
            this.workspaceFolders = workspaceFolders
        }

        LOG.info("Initializing LSP client...")

        return languageServer.initialize(params)
            .thenCompose { res ->
                LOG.debug(">>> Initialization response from server:\n{}", res)
                languageServer.initialized(InitializedParams())
                CompletableFuture.completedFuture(null)
            }
    }

    fun getCompletion(
        uri: String,
        position: Position,
        triggerKind: CompletionTriggerKind = CompletionTriggerKind.Invoked,
    ): CompletableFuture<List<CompletionItem>> {
        val context = CompletionContext(triggerKind)
        val params = CompletionParams(
            TextDocumentIdentifier(uri),
            position,
            context
        )

        return languageServer.textDocumentService.completion(params)
            .thenCompose { res ->
                when {
                    res.isLeft -> res.left
                    res.isRight -> res.right.items
                    else -> emptyList()
                }.let { CompletableFuture.completedFuture(it) }
            }
    }

    fun shutdown(): CompletableFuture<Any> = languageServer.shutdown()

    fun exit() {
        languageServer.exit()
        stopFuture.cancel(true)
        socket.close()
    }

    private fun getCompletionCapabilities() = ClientCapabilities().apply {
        textDocument = TextDocumentClientCapabilities().apply {
            completion =
                CompletionCapabilities(
                    CompletionItemCapabilities(true)
                ).apply { contextSupport = true }
        }
        workspace = WorkspaceClientCapabilities().apply { workspaceFolders = true }
    }

    private fun getRemoteLanguageServer(): LanguageServer{
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val launcher = LSPLauncher.createClientLauncher(languageClient, input, output)
        stopFuture = launcher.startListening()
        return launcher?.remoteProxy ?: throw RuntimeException("Cannot connect to server")
    }

    companion object {
        suspend fun create(kotlinProjectRoot: String, projectName: String = "None"): KotlinLSPClient {
            return KotlinLSPClient().apply {
                initRequest(kotlinProjectRoot, projectName).await()
            }
        }
    }
}

object DocumentSync {
    fun KotlinLSPClient.openDocument(uri: String, content: String, version: Int = 1, languageId: String = "kotlin") {
        languageServer.textDocumentService.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(uri, languageId, version, content)
            )
        )
    }

    fun KotlinLSPClient.changeDocument(uri: String, newContent: String, version: Int = 1) {
        if (uri.isEmpty()) return
        val params = DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(uri, version),
            listOf(TextDocumentContentChangeEvent(newContent)),
        )
        languageServer.textDocumentService.didChange(params)
    }

    fun KotlinLSPClient.closeDocument(uri: String) {
        languageServer.textDocumentService.didClose(
            DidCloseTextDocumentParams(TextDocumentIdentifier(uri))
        )
    }
}