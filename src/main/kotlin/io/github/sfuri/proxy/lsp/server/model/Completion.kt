package io.github.sfuri.proxy.lsp.server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.eclipse.lsp4j.CompletionItem
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

fun CompletionItem.toCompletion(): Completion? {
    val data = parseData() ?: return null
    val importName = with(data.options.importingStrategy) {
        if (needsImport(this)) fqName else null
    }
    val displayText = "$label${data.renderedDeclaration}"
    val tail = labelDetails?.description
    val icon = Icon.tryParse(kind?.name?.lowercase())

    return Completion(
        text = label,
        displayText = "${displayText}${if (importName != null) "  ($importName)" else ""}",
        tail = tail,
        import = importName,
        icon = icon,
    )
}

private fun needsImport(importingStrategy: ImportData.ImportingStrategy): Boolean =
    importingStrategy.fqName == null || !importingStrategy.kind.contains("DoNothing")

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

@Serializable
sealed interface ImportData {
    @Serializable data class ImportingStrategy(
        val kind: String,
        val fqName: String? = null,
        val nameToImport: String? = null
    ) : ImportData
    @Serializable data class Options(val importingStrategy: ImportingStrategy): ImportData
    @Serializable data class LookupObject(val options: Options, val renderedDeclaration: String) : ImportData
}

private fun CompletionItem.parseData(): ImportData.LookupObject?  {
    val json = Json { ignoreUnknownKeys = true }
    val root = json.parseToJsonElement(data.toString()).jsonObject

    val lookupJson = root["additionalData"]
        ?.jsonObject?.get("model")
        ?.jsonObject?.get("delegate")
        ?.jsonObject?.get("delegate")
        ?.jsonObject?.get("lookupObject")
        ?.jsonObject?.get("lookupObject") ?: return null

    return try {
        json.decodeFromJsonElement<ImportData.LookupObject>(lookupJson)
    } catch (e: SerializationException) {
        logger.debug("Error parsing data $lookupJson: $e")
        return null
    }
}

private val logger = LoggerFactory.getLogger(Completion::class.java)
