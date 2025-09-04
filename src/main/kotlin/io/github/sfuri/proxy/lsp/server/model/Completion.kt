package io.github.sfuri.proxy.lsp.server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.sfuri.proxy.lsp.server.model.Icon.*
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

data class ImportingStrategy(
    val kind: String,
    val fqName: String? = null,
    val nameToImport: String? = null
)

data class Options(val importingStrategy: ImportingStrategy)

private val mapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun CompletionItem.toCompletion(): Completion? {
    val root: JsonNode = try {
        mapper.readTree(data.toString())
    } catch (e: Exception) {
        logger.info("Cannot parse completion data root: {}", e.message)
        return null
    }

    val lookupJson =
        root.path("additionalData")
            .path("model")
            .path("delegate")
            .path("delegate")
            .path("lookupObject")
            .path("lookupObject")

    if (lookupJson.isMissingNode || lookupJson.isNull) return null

    val keyword = lookupJson.get("keyword")?.asText()
    val constructToInsert = lookupJson.get("constructToInsert")?.asText()
    val kind = lookupJson.get("kind")?.asText() // may hold FQNs like "...KeywordLookupObject"
    if (!keyword.isNullOrBlank() && !constructToInsert.isNullOrBlank()) {
        return Completion(
            text = keyword,
            displayText = keyword,
            tail = constructToInsert,
        )
    }

    if (!keyword.isNullOrBlank()) {
        return Completion(
            text = keyword,
            displayText = keyword,
            icon = GENERIC_VALUE
        )
    }

    val shortName = lookupJson.get("shortName")?.asText()
    if (!shortName.isNullOrBlank() && kind?.endsWith("NamedArgumentLookupObject") == true) {
        return Completion(
            text = shortName,
            displayText = shortName,
            icon = GENERIC_VALUE
        )
    }

    if (!shortName.isNullOrBlank() && kind?.endsWith("PackagePartLookupObject") == true) {
        return Completion(
            text = shortName,
            displayText = shortName,
            icon = PACKAGE
        )
    }

    val data = extractLookupCore(lookupJson) ?: return null
    val renderedDeclaration = data["renderedDeclaration"]!! as String
    val options = data["options"]!! as Options
    val hasReceiver = data["hasReceiver"] as Boolean?

    val importName = with(options.importingStrategy) {
        if (needsImport(this)) fqName else null
    }

    val name = extractNameForKind(label, renderedDeclaration, hasReceiver)

    val displayText = name + if (importName != null) "  ($importName)" else ""

    return Completion(
        text = label,
        displayText = displayText,
        tail = labelDetails?.description,
        import = importName,
        icon = Icon.tryParse(kind?.substringAfterLast('.')?.lowercase())
    )
}

private fun extractLookupCore(node: JsonNode): Map<String, Any?>? {
    val optionsNode = node.get("options")
    val importingStrategyNodeDirect = node.get("importingStrategy")
    val hasReceiver = runCatching {
        node.get("hasReceiver").asBoolean()
    }.getOrNull()

    val options: Options? = when {
        optionsNode != null && !optionsNode.isNull -> runCatching {
            mapper.treeToValue(optionsNode, Options::class.java)
        }.getOrNull()

        importingStrategyNodeDirect != null && !importingStrategyNodeDirect.isNull -> runCatching {
            Options(mapper.treeToValue(importingStrategyNodeDirect, ImportingStrategy::class.java))
        }.getOrNull()

        else -> null
    }

    val renderedDeclaration = node.get("renderedDeclaration")?.asText()
        ?: node.get("shortName")?.asText()

    return if (!renderedDeclaration.isNullOrBlank() && options != null) {
        mapOf(
            "renderedDeclaration" to renderedDeclaration,
            "options" to options,
            "hasReceiver" to hasReceiver,
        )
    } else {
        null
    }
}

fun extractNameForKind(label: String, renderedDeclaration: String, hasReceiver: Boolean?): String =
    if (hasReceiver == null) {
        renderedDeclaration + label
    } else  {
        if (label != renderedDeclaration) "$label$renderedDeclaration" else label
    }

private fun needsImport(importingStrategy: ImportingStrategy): Boolean =
    importingStrategy.fqName == null || !importingStrategy.kind.contains("DoNothing")

val logger: Logger = LoggerFactory.getLogger(Completion::class.java)