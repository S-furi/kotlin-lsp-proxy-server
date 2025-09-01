package io.github.sfuri.proxy.lsp.server

data class CompletionEntry(
    val text: String,
    val displayText: String,
    val tail: String,
    val icon: String
)