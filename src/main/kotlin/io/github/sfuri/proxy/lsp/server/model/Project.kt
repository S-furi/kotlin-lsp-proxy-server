package io.github.sfuri.proxy.lsp.server.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonValue
import java.nio.file.Path

@JsonIgnoreProperties(ignoreUnknown = true)
data class Project(
    val args: String = "",
    val files: List<ProjectFile> = listOf(),
    val confType: ProjectType = ProjectType.JAVA
) {
    fun generateId(): String = "${confType.id}-${hashCode()}"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProjectFile(val text: String = "", val name: String = "", val publicId: String? = null)

@Suppress("unused")
enum class ProjectType(@JsonValue val id: String) {
    JAVA("java"),
    JUNIT("junit"),
    JS("js"),
    CANVAS("canvas"),
    JS_IR("js-ir"),
    WASM("wasm"),
    COMPOSE_WASM("compose-wasm");
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

    fun getFilesUris(): List<String> =
        files.values.map { it.toUri().toString() }

    fun tearDown() {
        files.values.forEach { f ->
            f.toFile().delete()
        }
        Path.of(projectRoot).toFile().delete()
    }

    companion object {
        const val ANONYMOUS_USER = "anonymous"

        private val baseDir = Path.of("usersFiles").toAbsolutePath()

        fun fromProject(project: Project): LspProject = createLspProjectWithName(project.generateId(), project)

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
