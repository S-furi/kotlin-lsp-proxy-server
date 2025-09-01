package io.github.sfuri.proxy.lsp.server

import io.github.sfuri.proxy.lsp.client.DocumentSync.changeDocument
import io.github.sfuri.proxy.lsp.client.DocumentSync.openDocument
import io.github.sfuri.proxy.lsp.client.KotlinLSPClient
import io.github.sfuri.proxy.lsp.server.model.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Position
import org.slf4j.LoggerFactory
import java.nio.file.Path

object LspProxy {
    private val WORKSPACE_URI = Path.of("projectRoot").toUri()

    private val client: KotlinLSPClient = KotlinLSPClient(WORKSPACE_URI.path, "lsp-proxy")

    private val lspProjects = mutableMapOf<Project, LspProject>()

    private val usersProjects = mutableMapOf<String, Project>()

    suspend fun getCompletionsForUser(userId: String, project: Project, line: Int, ch: Int): List<CompletionItem> {
        usersProjects[userId]?.let {
            val fileName = it.files.first().name
            val newContent = project.files.first().text
            LspProject.fromOwner(userId, it).changeFileContents(fileName, newContent)
            client.changeDocument(
                lspProjects[usersProjects[userId]]!!.getFileUri(fileName)!!,
                newContent
            )
        } ?: usersProjects.put(userId, project)

        return getCompletions(usersProjects[userId]!!, line, ch)
    }

    /**
     * Retrieve completions for a given line and character position in a project. By now we assume
     * that the project contains a single file.
     */
    suspend fun getCompletions(project: Project, line: Int, ch: Int): List<CompletionItem> {

        var needsOpen = false
        val lspProject = lspProjects.getOrPut(project) {
            needsOpen = true
            LspProject.fromProject(project)
        }

        val projectFile = project.files.first()
        val uri = lspProject.getFileUri(projectFile.name) ?: return emptyList()
        val position = Position(line, ch)
        if (needsOpen) client.openDocument(uri, projectFile.text); delay(1000)
        return client.getCompletion(uri, position).await()
    }
}

data class LspProject(
    private val projectRoot: String,
    private val files: Map<String, Path>,
    private val owner: String? = null
) {

    fun changeFileContents(name: String, newContent: String) {
        files[name]?.toFile()?.writeText(newContent)
    }

    fun getFileUri(name: String): String? =
        files[name]?.toUri()?.toString()

    fun tearDown() {
        files.values.forEach { f -> f.toFile().delete() }
        Path.of(projectRoot).toFile().delete()
    }

    companion object {
        const val ANONYMOUS_USER = "anonymous"

        private val baseDir = Path.of("usersFiles").toAbsolutePath()

        fun fromOwner(owner: String?, project: Project): LspProject =
            if (owner == null || owner == ANONYMOUS_USER) fromProject(project)
            else createLspProjectWithName(owner, project)

        fun fromProject(project: Project): LspProject =
            createLspProjectWithName(project.hashCode().toString(), project).also {
                logger.info("LspProject created for project ${project.hashCode()}")
            }

        private fun createLspProjectWithName(name: String, project: Project): LspProject {
            val projectDir = baseDir.resolve(name)
            projectDir.toFile().mkdirs()

            val files = project.files.associate { f ->
                f.name to projectDir.resolve(f.name).apply {
                    toFile().writeText(f.text)
                }
            }

            return LspProject(projectDir.toString(), files)
        }
    }
}

private val logger = LoggerFactory.getLogger(LspProject::class.java)