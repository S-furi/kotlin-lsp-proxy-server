package io.github.sfuri.proxy.lsp.server

import io.github.sfuri.proxy.lsp.server.model.LspProject
import io.github.sfuri.proxy.lsp.server.model.Project
import io.github.sfuri.proxy.lsp.server.model.ProjectFile
import io.github.sfuri.proxy.lsp.server.model.ProjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LspProjectTest {
    private val createdProjects = mutableListOf<LspProject>()
    private val baseDir: Path = Path.of("usersFiles").toAbsolutePath()

    @BeforeEach
    fun ensureBaseDirExists() {
        Files.createDirectories(baseDir)
    }

    @AfterEach
    fun tearDownCreatedProjects() {
        createdProjects.forEach { it.tearDown() }
        createdProjects.clear()

        try {
            Files.newDirectoryStream(baseDir).use { stream ->
                if (!stream.iterator().hasNext()) {
                    Files.deleteIfExists(baseDir)
                }
            }
        } catch (_: Exception) {
        }
    }

    @Test
    fun `fromProject creates project folder and files`() {
        val p = Project(
            files = listOf(ProjectFile(text = "val x = 42", name = "Main.kt")),
            confType = ProjectType.JAVA
        )

        val lspProject = LspProject.fromProject(p).also { createdProjects += it }

        val expectedProjectDir = baseDir.resolve(p.hashCode().toString())
        assertTrue(expectedProjectDir.exists())

        val filePath = expectedProjectDir.resolve("Main.kt")
        assertTrue(filePath.exists())

        val content = Files.readString(filePath)
        assertEquals("val x = 42", content)

        val uri = lspProject.getFileUri("Main.kt")
        assertNotNull(uri)
        assertTrue(uri.startsWith("file://"))
        assertTrue(uri.endsWith("Main.kt"))
    }

    @Test
    fun `fromProject caches same Project instance`() {
        val p = Project(
            files = listOf(ProjectFile(text = "fun foo() = Unit", name = "Foo.kt")),
            confType = ProjectType.JAVA
        )

        val first = LspProject.fromProject(p).also { createdProjects += it }
        val second = LspProject.fromProject(p)

        assertEquals(first, second)
        val filePath = baseDir.resolve(p.hashCode().toString()).resolve("Foo.kt")
        assertTrue(filePath.exists())
    }

    @Test
    fun `getFileUri returns null for unknown file name`() {
        val p = Project(
            files = listOf(ProjectFile(text = "class A", name = "A.kt")),
            confType = ProjectType.JAVA
        )

        val lspProject = LspProject.fromProject(p).also { createdProjects += it }

        val unknownUri = lspProject.getFileUri("B.kt")
        assertNull(unknownUri)
    }

    @Test
    fun `tearDown removes created files and project directory`() {
        val p = Project(
            files = listOf(ProjectFile(text = "object X", name = "X.kt")),
            confType = ProjectType.JAVA
        )

        val lspProject = LspProject.fromProject(p)
        val projectDir = baseDir.resolve(p.hashCode().toString())
        val filePath = projectDir.resolve("X.kt")

        assertTrue(projectDir.exists())
        assertTrue(filePath.exists())

        lspProject.tearDown()

        assertTrue(!filePath.exists())
        assertTrue(!projectDir.exists())
    }
}
