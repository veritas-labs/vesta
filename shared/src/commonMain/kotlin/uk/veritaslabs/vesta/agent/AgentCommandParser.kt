package uk.veritaslabs.vesta.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Decodes ADR-009's JSON-LD (compact form) command payloads. There is deliberately no JSON-LD
 * processor: the `@context` must match [CONTEXT] exactly and the rest is read as plain JSON.
 */
object AgentCommandParser {

    const val MAX_PAYLOAD_BYTES = 16 * 1024
    const val MAX_QUANTITY = 9999

    /** The one accepted `@context`, also used on replies. */
    val CONTEXT: JsonArray = buildJsonArray {
        add(JsonPrimitive("https://www.w3.org/ns/activitystreams"))
        add(buildJsonObject { put("schema", "https://schema.org/") })
    }

    private const val TARGET_PREFIX = "urn:vesta:document:"
    private const val TARGET_SUFFIX = ":pantry"

    fun parse(payload: ByteArray): ParseResult {
        if (payload.size > MAX_PAYLOAD_BYTES) return ParseResult.Invalid(null, "payload too large")
        val root = runCatching { Json.parseToJsonElement(payload.decodeToString()) }.getOrNull() as? JsonObject
            ?: return ParseResult.NotACommand
        if (root["@context"] != CONTEXT) return ParseResult.NotACommand

        val commandId = (root["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)

        if (commandId == null) return invalid("id is required")
        if (root.string("type") != "Add") return invalid("type must be Add")

        val targetId = (root["target"] as? JsonObject)?.string("id") ?: return invalid("target.id is required")
        val homeId = targetId.takeIf { it.startsWith(TARGET_PREFIX) && it.endsWith(TARGET_SUFFIX) }
            ?.removePrefix(TARGET_PREFIX)?.removeSuffix(TARGET_SUFFIX)?.takeIf { it.isNotBlank() }
            ?: return invalid("target.id must be $TARGET_PREFIX<homeId>$TARGET_SUFFIX")

        val node = root["object"] as? JsonObject
        if (node?.string("type") != "schema:TypeAndQuantityNode") return invalid("object must be a schema:TypeAndQuantityNode")
        val product = node["schema:typeOfGood"] as? JsonObject
        if (product?.string("type") != "schema:Product") return invalid("schema:typeOfGood must be a schema:Product")

        val title = product.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("title is required")

        val categories = when (val category = product["schema:category"]) {
            null -> emptyList()
            is JsonPrimitive -> listOfNotNull(category.stringOrNull()).also { if (it.isEmpty()) return invalid("category must be a string") }
            is JsonArray -> category.map { it.stringOrNull() }
            else -> return invalid("category must be a string or an array of strings")
        }
        if ((product["schema:category"] != null && categories.isEmpty()) || categories.size > 2 || categories.any { it.isNullOrBlank() }) return invalid("category must have one or two non-blank strings")

        val size = product.optionalString("schema:size") ?: return invalid("size must be a string")
        val gtin = product.optionalString("schema:gtin") ?: return invalid("gtin must be a string")

        val quantity = when (val amount = node["schema:amountOfThisGood"]) {
            null -> 1
            is JsonPrimitive -> amount.takeUnless { it.isString }?.intOrNull ?: return invalid("quantity must be an integer")
            else -> return invalid("quantity must be an integer")
        }
        if (quantity !in 1..MAX_QUANTITY) return invalid("quantity out of range")

        return ParseResult.Command(
            AddPantryItem(
                commandId = commandId,
                homeId = homeId,
                title = title,
                primaryCategory = categories.getOrNull(0)?.trim(),
                secondaryCategory = categories.getOrNull(1)?.trim(),
                packageSize = size.value?.trim(),
                barcode = gtin.value?.trim(),
                quantity = quantity,
            ),
        )
    }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.string(key: String): String? = this[key]?.stringOrNull()

    /** Distinguishes "absent/blank" (value null) from "present but not a string" (result null). */
    private class Optional(val value: String?)

    private fun JsonObject.optionalString(key: String): Optional? {
        val element = this[key] ?: return Optional(null)
        val s = element.stringOrNull() ?: return null
        return Optional(s.takeIf { it.isNotBlank() })
    }
}
