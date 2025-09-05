package io.github.sfuri.proxy.lsp.server

import io.github.sfuri.proxy.lsp.server.model.CompletionParser.toCompletion
import io.github.sfuri.proxy.lsp.server.model.Project
import io.github.sfuri.proxy.lsp.server.model.ProjectFile
import io.github.sfuri.proxy.lsp.server.model.ProjectType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Path
import kotlin.test.assertEquals

@SpringBootTest
class KotlinLspProxyServerApplicationTests {

    @Test
    fun testCompletions() = runTest {
        val content =
            """
            fun main() {
                3.0.toIn
            }
        """.trimIndent()

        complete(
            code = content,
            line = 1,
            ch = 11,
            completions = listOf(
                "toInt", "toUInt"
            ),
            exhaustive = false,
        )
    }

    @Test
    fun `test project isolation`() = runTest {
        val content1 =
            """
            fun main() {
                3.0.toIn
            }
            """.trimIndent()

        val content2 =
            """
            fun main() {
                3.0.toIn
            }
            fun Double.toInterval(): IntRange = IntRange(0, toInt())
            """.trimIndent()

        val project1 = Project(files = listOf(ProjectFile(content1, "source1.kt")), confType = ProjectType.JAVA)
        val project2 = Project(files = listOf(ProjectFile(content2, "source2.kt")), confType = ProjectType.JAVA)

        val compl1 = async { LspProxy.getCompletionsSingleRoundTrip(project1, 1, 11).map { it.toCompletion()?.text } }
        val compl2 = async { LspProxy.getCompletionsSingleRoundTrip(project2, 1, 11).map { it.toCompletion()?.text } }

        listOf(compl1, compl2).awaitAll().let { (c1, c2) ->
            assertTrue(c2.contains("toInterval("))
            assertFalse(c1.contains("toInterval("))
        }
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun tearDown() {
            val root = Path.of("usersFiles")
            root.toFile().listFiles()?.forEach { it.deleteRecursively() }
        }

        @BeforeAll
        @JvmStatic
        fun setup() = runBlocking {
            val root = Path.of("usersFiles")
            LspProxy.initializeClient()
        }
    }
}

private suspend fun complete(code: String, line: Int, ch: Int, completions: List<String>, exhaustive: Boolean = true) {
    val project = Project(files = listOf(ProjectFile(code, "source1.kt")), confType = ProjectType.JAVA)
    val res = LspProxy.getCompletionsSingleRoundTrip(
        project,
        line,
        ch,
    ).mapNotNull { it.toCompletion()?.text?.replace("(", "") }

    assertFalse(res.isEmpty())
    if (exhaustive) assertEquals(completions, res)
    else assertTrue(res.containsAll(completions))
}
