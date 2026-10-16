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

    /** The most items one [ReadPantry] reply carries; the reply's `totalItems` still says how many there are. */
    const val MAX_PANTRY_ITEMS = 200

    /** The one accepted `@context`, also used on replies. */
    val CONTEXT: JsonArray = buildJsonArray {
        add(JsonPrimitive("https://www.w3.org/ns/activitystreams"))
        add(buildJsonObject { put("schema", "https://schema.org/") })
    }

    /** The `object.id` of a [ListHomes] request. */
    const val HOMES_COLLECTION_ID = "urn:vesta:homes"

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
        // A shopping-list target belongs to ShoppingListCommands.kt; the verbs mean the same kind of thing there.
        val type = root.string("type")
        if (type == "Add" || type == "Remove" || type == "Update") {
            ShoppingListCommandParser.homeId((root["target"] as? JsonObject)?.string("id"))
                ?.let { return ShoppingListCommandParser.parseWrite(root, commandId, type, it) }
        }
        CookbookCommandParser.parse(root, commandId)?.let { return it }
        CalendarCommandParser.parse(root, commandId)?.let { return it }
        return when (root.string("type")) {
            "Add" -> parseAdd(root, commandId)
            "Remove" -> parseStockChange(root, commandId, set = false)
            "Update" -> parseStockChange(root, commandId, set = true)
            "Read" -> parseRead(root, commandId)
            else -> invalid("type must be Add, Remove, Update or Read")
        }
    }

    private fun parseRead(root: JsonObject, commandId: String): ParseResult {
        val objectId = (root["object"] as? JsonObject)?.string("id")
        if (objectId == HOMES_COLLECTION_ID) return ParseResult.Command(ListHomes(commandId))
        ShoppingListCommandParser.homeId(objectId)?.let { return ParseResult.Command(ReadShoppingList(commandId, it)) }
        val homeId = objectId?.let(::pantryHomeId)
        if (homeId != null) return ParseResult.Command(ReadPantry(commandId, homeId))
        return ParseResult.Invalid(commandId, "object.id must be $HOMES_COLLECTION_ID or $TARGET_PREFIX<homeId>$TARGET_SUFFIX")
    }

    private fun pantryHomeId(documentId: String): String? =
        documentId.takeIf { it.startsWith(TARGET_PREFIX) && it.endsWith(TARGET_SUFFIX) }
            ?.removePrefix(TARGET_PREFIX)?.removeSuffix(TARGET_SUFFIX)?.takeIf { it.isNotBlank() }

    /**
     * `Remove` (reduce stock by an amount, 1..[MAX_QUANTITY], default 1) and `Update` (set stock to an exact amount,
     * 0..[MAX_QUANTITY], required). Both name the item with `schema:name` and optionally `schema:gtin`.
     */
    private fun parseStockChange(root: JsonObject, commandId: String, set: Boolean): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)

        val targetId = (root["target"] as? JsonObject)?.string("id") ?: return invalid("target.id is required")
        val homeId = pantryHomeId(targetId)
            ?: return invalid("target.id must be $TARGET_PREFIX<homeId>$TARGET_SUFFIX")

        val node = root["object"] as? JsonObject
        if (node?.string("type") != "schema:TypeAndQuantityNode") return invalid("object must be a schema:TypeAndQuantityNode")
        val product = node["schema:typeOfGood"] as? JsonObject
        if (product?.string("type") != "schema:Product") return invalid("schema:typeOfGood must be a schema:Product")
        val title = product.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("title is required")
        val gtin = product.optionalString("schema:gtin") ?: return invalid("gtin must be a string")

        val amount = node["schema:amountOfThisGood"]
        val quantity = when {
            amount == null && set -> return invalid("quantity is required")
            amount == null -> 1
            amount is JsonPrimitive -> amount.takeUnless { it.isString }?.intOrNull ?: return invalid("quantity must be an integer")
            else -> return invalid("quantity must be an integer")
        }
        if (quantity !in (if (set) 0 else 1)..MAX_QUANTITY) return invalid("quantity out of range")

        val barcode = gtin.value?.trim()
        return ParseResult.Command(
            if (set) SetPantryStock(commandId, homeId, title, barcode, quantity)
            else ReducePantryStock(commandId, homeId, title, barcode, quantity),
        )
    }

    private fun parseAdd(root: JsonObject, commandId: String): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)

        val targetId = (root["target"] as? JsonObject)?.string("id") ?: return invalid("target.id is required")
        val homeId = pantryHomeId(targetId)
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
