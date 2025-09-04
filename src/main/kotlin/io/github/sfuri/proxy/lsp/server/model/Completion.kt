package io.github.sfuri.proxy.lsp.server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import io.github.sfuri.proxy.lsp.server.model.AdditionalCompletionData.*
import org.eclipse.lsp4j.CompletionItem
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Completion(
    val text: String,
    val displayText: String,
    val tail: String? = null,
    val import: String? = null,
    val icon: Icon? = null,
    var hasOtherImports: Boolean? = null,
)

enum class Icon {
    @JsonProperty("class")
    CLASS,
    @JsonProperty("method")
    METHOD,
    @JsonProperty("property")
    PROPERTY,
    @JsonProperty("package")
    PACKAGE,
    @JsonProperty("genericValue")
    GENERIC_VALUE;

    companion object {
        fun tryParse(name: String?): Icon =
            try {
                valueOf(name!!.uppercase())
            } catch (_: Exception) {
                GENERIC_VALUE
            }
    }
}


object CompletionParser {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    fun CompletionItem.toCompletion(): Completion? {
        val root = json.parseToJsonElement(data.toString()).jsonObject

        val lookupJson = root["additionalData"]
            ?.jsonObject?.get("model")
            ?.jsonObject?.get("delegate")
            ?.jsonObject?.get("delegate")
            ?.jsonObject?.get("lookupObject")
            ?.jsonObject?.get("lookupObject") ?: return null

        return tryParseCompletionData(lookupJson)
            .map { completionData ->
                completionData.asDirectCompletionOrNull()?.let { return@map it }

                val lookup = completionData.asLookupObjectOrNull() ?: return@map null

                buildCompletionFromLookup(
                    label = label,
                    description = labelDetails?.description,
                    iconKind = kind?.name?.lowercase(),
                    lookup = lookup
                )
            }.getOrElse { e ->
                logger.info("Cannot parse {}: {}", lookupJson, e.message)
                null
            }
    }

    private fun AdditionalCompletionData.asLookupObjectOrNull(): LookupObject? = when (this) {
        is LookupObject -> this
        is FunctionCallLookupObject -> LookupObject(options = options, renderedDeclaration = renderedDeclaration)
        is ClassifierLookupObject -> LookupObject(options = Options(importingStrategy), renderedDeclaration = shortName)
        is VariableLookupObject -> LookupObject(options = options, renderedDeclaration = shortName)
        is ShortImport -> LookupObject(options = Options(importingStrategy), renderedDeclaration = shortName)
        else -> null
    }

    private fun AdditionalCompletionData.asDirectCompletionOrNull(): Completion? = when (this) {
        is KeywordConstructLookupObject -> Completion(text = keyword, displayText = keyword, tail = constructToInsert)
        is KeywordLookupObject -> keyword?.let { Completion(text = it, displayText = it, icon = Icon.GENERIC_VALUE) }
        is NamedArgumentLookupObject -> Completion(text = shortName, displayText = shortName, icon = Icon.GENERIC_VALUE)
        is PackagePartLookupObject -> Completion(text = shortName, displayText = shortName, icon = Icon.PACKAGE)
        else -> null
    }

    private fun buildCompletionFromLookup(
        label: String,
        description: String?,
        iconKind: String?,
        lookup: LookupObject
    ): Completion {
        val importName = with(lookup.options.importingStrategy) {
            if (needsImport(this)) fqName else null
        }

        val name = if (label != lookup.renderedDeclaration) "$label${lookup.renderedDeclaration}" else label
        val displayText = name + if (importName != null) "  ($importName)" else ""

        return Completion(
            text = label,
            displayText = displayText,
            tail = description,
            import = importName,
            icon = Icon.tryParse(iconKind),
        )
    }

    private fun tryParseCompletionData(data: JsonElement): Result<AdditionalCompletionData> {
        return try {
            val res = json.decodeFromJsonElement<AdditionalCompletionData>(data)
            Result.success(res)
        } catch (e: SerializationException) {
            Result.failure(e)
        }
    }

    private fun needsImport(importingStrategy: ImportingStrategy): Boolean =
        importingStrategy.fqName == null || !importingStrategy.kind.contains("DoNothing")

    private val logger: Logger = LoggerFactory.getLogger(Completion::class.java)
}