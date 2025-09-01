package io.github.sfuri.proxy.lsp.server

import io.github.sfuri.proxy.lsp.server.model.Project
import kotlinx.coroutines.runBlocking
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/compiler")
class CompletionController {

    @PostMapping(
        "/complete",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun complete(
        @RequestBody project: Project,
        @RequestParam(name = "line") line: Int,
        @RequestParam(name = "ch", required = true) ch: Int,
    ): List<CompletionEntry> = runBlocking {
        LspProxy.getCompletions(
            project = project,
            line = line,
            ch = ch,
        )
    }
}
