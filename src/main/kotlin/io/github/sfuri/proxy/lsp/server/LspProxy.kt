package io.github.sfuri.proxy.lsp.server

import io.github.sfuri.proxy.lsp.client.DocumentSync.openDocument
import io.github.sfuri.proxy.lsp.client.KotlinLSPClient
import io.github.sfuri.proxy.lsp.server.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Position
import java.nio.file.Path

object LspProxy {
    private val WORKSPACE_URI = Path.of("projectRoot").toUri()

    private val proxyCoroutineScope = CoroutineScope(Dispatchers.IO)

    private val client: KotlinLSPClient by lazy {
        KotlinLSPClient(WORKSPACE_URI.path, "lsp-proxy")
    }

    private val lspProjects = mutableMapOf<Project, LspProject>()

    /**
     * Retrieve completions for a given line and character position in a project. By now we assume
     * that the project contains a single file.
     */
    suspend fun getCompletions(project: Project, line: Int, ch: Int): List<CompletionEntry> {

        val lspProject = lspProjects.getOrPut(project) {
            LspProject.fromProject(project)
        }

        val projectFile = project.files.first()
        val uri = lspProject.getFileUri(projectFile.name) ?: return emptyList()
        val position = Position(line, ch)
        client.openDocument(uri, projectFile.text)
        delay(200)
        return client.getCompletion(uri, position).await().toCompletionEntries()
    }
}

private fun List<CompletionItem>.toCompletionEntries(): List<CompletionEntry> =
    map { compItem ->
        CompletionEntry(
            text = compItem.label,
            displayText = compItem.detail,
            tail = "bho",
            icon = compItem.kind?.name?.lowercase() ?: "undefined",
        )
    }

data class LspProject(
    private val projectRoot: String,
    private val files: Map<String, Path>,
) {

    fun getFileUri(name: String): String? =
        files[name]?.toUri()?.toString()

    fun tearDown() {
        files.values.forEach { f -> f.toFile().delete() }
        Path.of(projectRoot).toFile().delete()
    }

    companion object {
        private val baseDir = Path.of("usersFiles").toAbsolutePath()

        private val projects = mutableMapOf<Project, LspProject>()

        fun fromProject(project: Project): LspProject =
            projects.getOrPut(project) {
                val projectDirName = project.hashCode().toString()
                val projectDir = baseDir.resolve(projectDirName)
                projectDir.toFile().mkdirs()

                val files = project.files.associate { f ->
                    f.name to projectDir.resolve(f.name).apply {
                        toFile().writeText(f.text)
                    }
                }

                LspProject(projectDir.toString(), files)
            }
    }
}