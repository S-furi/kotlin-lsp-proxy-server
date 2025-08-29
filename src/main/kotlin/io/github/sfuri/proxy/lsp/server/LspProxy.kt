package io.github.sfuri.proxy.lsp.server

import io.github.sfuri.proxy.lsp.client.KotlinLSPClient
import java.net.URI

object LspProxy {
    val client = KotlinLSPClient()
    val users = mutableMapOf<String, LspFile>()

    // TODO: generate a way to map a user to a random file, primarily a virtual file
    // TODO: store file contents in map, to be later used in `didOpen` requests
    // TODO: expose `/complete` endpoint that returns a list of completion options given the same input as KCS
}

data class LspFile(val physicalUri: URI?) {
    private val safeTmpDir = "/foo/bar/tmp"

    /**
     * To be used when making actual LSP requests.
     */
    internal val logicalUri: LspURI = physicalUri?.toLspUri()?.buildTmpUri(safeTmpDir)
        ?: LspURI.Parser.parse("file://$safeTmpDir/dummy.kt")
}

internal fun URI.toLspUri(): LspURI = LspURI.Parser.parse(this.toString())

data class LspURI(val uri: URI) {

    fun buildTmpUri(tmpDir: String, enforceExtension: String? = null): LspURI =
        LspURI(URI.create("file://$tmpDir/${Parser.extractFileName(this, enforceExtension) ?: "dummy.kt"}"))

    object Parser {
        const val KOTLIN_EXTENSION = ".kt"

        fun parse(uri: String): LspURI {
            return LspURI(URI.create(uri))
        }

        fun extractFileName(lspUri: LspURI, enforceExtension: String? = null): String? {
            return lspUri.uri.path.substringAfterLast("/").let { fileName ->
                fileName.takeIf { it.endsWith(enforceExtension ?: "") }
            }
        }
    }
}