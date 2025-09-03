package io.github.sfuri.proxy.lsp.server.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
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
    data class ShortImport( // TODO: better name?
        val kind: String,
        val shortName: String,
        val importingStrategy: ImportingStrategy
    ) : AdditionalCompletionData {
        fun toLookupObject(): LookupObject =
            LookupObject(
                renderedDeclaration = shortName,
                options = Options(importingStrategy),
            )
    }

    @Serializable
    data class LookupObject(
        val options: Options,
        val renderedDeclaration: String,
    ) : AdditionalCompletionData

    @Serializable
    data class KeywordConstructLookupObject(
        val kind: String,
        val keyword: String,
        val constructToInsert: String,
    ) : AdditionalCompletionData {
        fun toCompletion(): Completion =
            Completion(
                text = keyword,
                displayText = keyword,
                tail = constructToInsert,
            )
    }

    companion object Serializer :
        JsonContentPolymorphicSerializer<AdditionalCompletionData>(AdditionalCompletionData::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<AdditionalCompletionData> {
            val jsonObject = element.jsonObject
            return with(jsonObject) {
                when {
                    containsKey("keyword") && containsKey("constructorToInsert") -> KeywordConstructLookupObject.serializer()
                    containsKey("shortName") && containsKey("importingStrategy") -> ShortImport.serializer()
                    containsKey("options") && containsKey("renderedDeclaration") -> LookupObject.serializer()
                    else -> throw SerializationException("Unknown AdditionalCompletionData type: $element")
                }
            }
        }
    }
}

val json = Json { ignoreUnknownKeys = true }

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
            val elem: AdditionalCompletionData.LookupObject = when (completionData) {
                is AdditionalCompletionData.LookupObject -> completionData
                is AdditionalCompletionData.ShortImport -> completionData.toLookupObject()
                is AdditionalCompletionData.KeywordConstructLookupObject -> return@map completionData.toCompletion()
            }

            val importName = with(elem.options.importingStrategy) {
                if (needsImport(this)) fqName else null
            }

            val displayText = "$label${elem.renderedDeclaration}" +
                    if (importName != null) "  ($importName)" else ""

            Completion(
                text = label,
                displayText = displayText,
                tail = labelDetails?.description,
                import = importName,
                icon = Icon.tryParse(kind?.name?.lowercase()),
            )
        }.getOrElse { e ->
            logger.error("Error parsing lookupObject $lookupJson: ${e.message}")
            null
        }
}

private fun tryParseCompletionData(data: JsonElement): Result<AdditionalCompletionData> {
    return try {
        val res = json.decodeFromJsonElement(AdditionalCompletionData.Serializer,data)
        Result.success(res)
    } catch (e: SerializationException) {
        Result.failure(e)
    }
}

private fun needsImport(importingStrategy: ImportingStrategy): Boolean =
    importingStrategy.fqName == null || !importingStrategy.kind.contains("DoNothing")

val logger: Logger = LoggerFactory.getLogger(Completion::class.java)