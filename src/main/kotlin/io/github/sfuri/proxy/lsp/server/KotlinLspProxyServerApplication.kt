package io.github.sfuri.proxy.lsp.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KotlinLspProxyServerApplication

fun main(args: Array<String>) {
    runApplication<KotlinLspProxyServerApplication>(*args)
}
