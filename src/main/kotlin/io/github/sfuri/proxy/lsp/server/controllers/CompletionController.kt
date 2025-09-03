package io.github.sfuri.proxy.lsp.server.controllers

import io.github.sfuri.proxy.lsp.server.LspProxy
import io.github.sfuri.proxy.lsp.server.model.Completion
import io.github.sfuri.proxy.lsp.server.model.Project
import io.github.sfuri.proxy.lsp.server.model.toCompletion
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/compiler")
class CompletionController {

    private val completionContext = Dispatchers.IO + CoroutineName("CompletionController")

    @CrossOrigin(
        origins = ["http://localhost:9001"],
        allowedHeaders = ["*"],
        methods = [RequestMethod.POST, RequestMethod.OPTIONS]
    )
    @PostMapping(
        "/complete",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun complete(
        @RequestBody project: Project,
        @RequestParam(name = "line") line: Int,
        @RequestParam(name = "ch", required = true) ch: Int,
    ): List<Completion> = withContext(completionContext) {
        LspProxy.getCompletions(project, line, ch).mapNotNull { it.toCompletion() }
    }
}