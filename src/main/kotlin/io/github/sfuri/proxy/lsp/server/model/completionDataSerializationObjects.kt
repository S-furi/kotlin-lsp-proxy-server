package io.github.sfuri.proxy.lsp.server.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImportingStrategy(
    val kind: String,
    val fqName: String? = null,
    val nameToImport: String? = null
)

@Serializable
data class Options(val importingStrategy: ImportingStrategy)

@Serializable
sealed interface AdditionalCompletionData {

    @Serializable
    data class ShortImport(
        val kind: String,
        val shortName: String,
        val importingStrategy: ImportingStrategy
    ) : AdditionalCompletionData

    @Serializable
    data class LookupObject(
        val options: Options,
        val renderedDeclaration: String,
    ) : AdditionalCompletionData

    @Serializable
    @SerialName("org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionCallLookupObject")
    data class FunctionCallLookupObject(
        val renderedDeclaration: String,
        val options: Options,
    ) : AdditionalCompletionData

    @Serializable
    @SerialName("org.jetbrains.kotlin.idea.completion.lookups.factories.ClassifierLookupObject")
    data class ClassifierLookupObject(
        val shortName: String,
        val importingStrategy: ImportingStrategy
    ): AdditionalCompletionData

    @Serializable
    @SerialName("org.jetbrains.kotlin.idea.completion.handlers.KeywordConstructLookupObject")
    data class KeywordConstructLookupObject(
        val kind: String,
        val keyword: String,
        val constructToInsert: String,
    ) : AdditionalCompletionData

    @Serializable
    @SerialName("org.jetbrains.kotlin.idea.completion.lookups.factories.VariableLookupObject")
    data class VariableLookupObject(
        val shortName: String,
        val options: Options,
    ) : AdditionalCompletionData

    @Serializable
    @SerialName("org.jetbrains.kotlin.idea.completion.KeywordLookupObject")
    data class KeywordLookupObject(
        val kind: String,
        val keyword: String? = null,
    ) : AdditionalCompletionData

    @Serializable
    @SerialName("org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupObject")
    data class NamedArgumentLookupObject(
        val kind: String,
        val shortName: String,
    ) : AdditionalCompletionData

    @Serializable
    @SerialName("org.jetbrains.kotlin.idea.completion.lookups.factories.PackagePartLookupObject")
    data class PackagePartLookupObject(
        val kind: String,
        val shortName: String,
    ) : AdditionalCompletionData
}
