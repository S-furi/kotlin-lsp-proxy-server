package io.github.sfuri.proxy.lsp.server

import io.github.sfuri.proxy.lsp.server.model.Project
import io.github.sfuri.proxy.lsp.server.model.ProjectFile
import io.github.sfuri.proxy.lsp.server.model.ProjectType
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

@SpringBootTest
class KotlinLspProxyServerApplicationTests {

    @Test
    fun contextLoads() {
    }

    @Test
    fun projectTest() {
        val project1 = Project(files = listOf(ProjectFile("3.0.toInt()", "source1.kt")), confType = ProjectType.JAVA)
        val project2 = Project(files = listOf(ProjectFile("3.0.toInt()", "source1.kt")), confType = ProjectType.JAVA)

        assertEquals(project1.hashCode(), project2.hashCode())
    }



}
