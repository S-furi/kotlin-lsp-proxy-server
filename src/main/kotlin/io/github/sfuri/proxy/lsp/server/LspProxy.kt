package io.github.sfuri.proxy.lsp.server

import io.github.sfuri.proxy.lsp.client.DocumentSync.changeDocument
import io.github.sfuri.proxy.lsp.client.DocumentSync.closeDocument
import io.github.sfuri.proxy.lsp.client.DocumentSync.openDocument
import io.github.sfuri.proxy.lsp.client.KotlinLSPClient
import io.github.sfuri.proxy.lsp.server.model.LspProject
import io.github.sfuri.proxy.lsp.server.model.Project
import io.github.sfuri.proxy.lsp.server.model.ProjectFile
import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Position
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object LspProxy {
    private val WORKSPACE_URI = Path.of("projectRoot").toUri()

    private lateinit var client: KotlinLSPClient

    private val lspProjects = ConcurrentHashMap<Project, LspProject>()

    private val usersProjects = ConcurrentHashMap<String, Project>()

    fun initializeClient(workspacePath: String = WORKSPACE_URI.path, clientName: String = "lsp-proxy") {
        if (!::client.isInitialized) {
            client = KotlinLSPClient(workspacePath, clientName)
            logger.info("LSP client initialized with workspace=$workspacePath, name=$clientName")
        }
    }

    fun onUserConnected(userId: String) {
        val project = Project(files = listOf(ProjectFile(name = "$userId.kt")))
        val lspProject = LspProject.fromProject(project)
        lspProjects[project] = lspProject
        usersProjects[userId] = project

        lspProject.getFilesUris().forEach {
            uri -> client.openDocument(uri, project.files.first().text)
        }
    }

    fun onUserDisconnected(userId: String) {
        closeProject(userId)
    }

    suspend fun getCompletionsSingleRoundTrip(project: Project, line: Int, ch: Int): List<CompletionItem> {
        val lspProject = lspProjects[project] ?: createNewProject(project)
        val projectFile = project.files.first()
        val uri = lspProject.getFileUri(projectFile.name) ?: return emptyList()
        client.openDocument(uri, projectFile.text)
        return getCompletions(project, line, ch, closeAfter = true)
    }

    suspend fun getCompletionsForUser(userId: String, newProject: Project, line: Int, ch: Int): List<CompletionItem> {
        val project = usersProjects[userId] ?: return emptyList()
        val lspProject = lspProjects[project] ?: return emptyList()
        val newContent = newProject.files.first().text
        val documentToChange = newProject.files.first().name
        changeContent(lspProject, documentToChange, newContent)
        return getCompletions(project, line, ch)
    }

    /**
     * Retrieve completions for a given line and character position in a project file. By now
     *
     * - we assume that the project contains a single file
     * - changes arrive before completion is triggered
     *
     * Changes are not incremental, whole file content is transmitted (which can make
     * sense being kotlin playground files quite small in content).
     */
    suspend fun getCompletions(
        project: Project,
        line: Int,
        ch: Int,
        fileName: String = project.files.first().name,
        closeAfter: Boolean = false
    ): List<CompletionItem> {
        val lspProject = lspProjects[project] ?: return emptyList()
        val uri = lspProject.getFileUri(fileName) ?: return emptyList()
        return client.getCompletion(uri, Position(line, ch)).await()
            .also { if (closeAfter) client.closeDocument(uri) }
    }

    private fun createNewProject(project: Project): LspProject {
        val lspProject = LspProject.fromProject(project)
        lspProjects[project] = lspProject
        return lspProject
    }

    // TODO: investigate potential race conditions
    private fun changeContent(lspProject: LspProject, name: String, newContent: String) {
        lspProject.changeFileContents(name, newContent)
        client.changeDocument(lspProject.getFileUri(name)!!, newContent)
    }

    fun closeProject(project: Project) {
        val lspProject = lspProjects[project] ?: return
        lspProject.getFilesUris().forEach { uri -> client.closeDocument(uri) }
        lspProject.tearDown()
        lspProjects.remove(project)
    }

    fun closeProject(userId: String) {
        usersProjects[userId]?.let {
            closeProject(it)
            usersProjects.remove(userId)
        }
    }

    fun closeAllProjects() {
        usersProjects.keys.forEach { closeProject(it) }
        usersProjects.clear()
        lspProjects.clear()
    }

    private val logger = LoggerFactory.getLogger(LspProxy::class.java)
}