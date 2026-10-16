package uk.veritaslabs.vesta.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** "Show me the shopping list of Home [homeId]". Read-only; needs the Shopping list Read capability. */
data class ReadShoppingList(override val commandId: String, val homeId: String) : AgentCommand

/** A command that changes a Home's shopping list; needs the Shopping list Write capability. */
sealed interface ShoppingWriteCommand : AgentCommand {
    val homeId: String

    /** One line for the activity list and the Allow once card, e.g. "Add 2 × Milk to the shopping list". */
    val summary: String
}

/** "Put [quantity] of this product on the list"; a product already on it has the quantities added together. */
data class AddShoppingItem(
    override val commandId: String,
    override val homeId: String,
    val title: String,
    val primaryCategory: String?,
    val secondaryCategory: String?,
    val packageSize: String?,
    val barcode: String?,
    val quantity: Int,
) : ShoppingWriteCommand {
    override val summary get() = "Add $quantity × $title to the shopping list"
}

/** "Take this product off the list" (the whole line). The item is found by [barcode], else [title]; it must be on the list. */
data class RemoveShoppingItem(
    override val commandId: String,
    override val homeId: String,
    val title: String,
    val barcode: String?,
) : ShoppingWriteCommand {
    override val summary get() = "Take $title off the shopping list"
}

/** "[got] of this product have been picked up" (0 un-ticks it). The item must be on the list. */
data class SetShoppingProgress(
    override val commandId: String,
    override val homeId: String,
    val title: String,
    val barcode: String?,
    val got: Int,
) : ShoppingWriteCommand {
    override val summary get() = "Set $title to $got picked up on the shopping list"
}

/** A shopping-list line as an agent sees it. */
data class ShoppingItemRef(
    val key: String,
    val title: String,
    val primaryCategory: String,
    val secondaryCategory: String?,
    val packageSize: String,
    val barcode: String?,
    val quantity: Int,
    val got: Int,
    val outOfStock: Boolean,
)

/**
 * Parses the shopping-list commands and builds their replies. [AgentCommandParser] hands over any message whose
 * target (or, for a read, object) is a `urn:vesta:document:<homeId>:shopping-list`; the verbs `Add`, `Remove` and
 * `Update` mean the same kind of thing as on a Pantry.
 */
object ShoppingListCommandParser {

    /** The most items one [ReadShoppingList] reply carries; `totalItems` still says how many there are. */
    const val MAX_ITEMS = 200

    private const val PREFIX = "urn:vesta:document:"
    private const val SUFFIX = ":shopping-list"

    fun documentId(homeId: String) = "$PREFIX$homeId$SUFFIX"

    /** The Home of a shopping-list document id, or null if [documentId] is not one. */
    fun homeId(documentId: String?): String? =
        documentId?.takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)?.removeSuffix(SUFFIX)?.takeIf { it.isNotBlank() }

    /** [type] is `Add`, `Remove` or `Update`; [homeId] came from the target. */
    fun parseWrite(root: JsonObject, commandId: String, type: String, homeId: String): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)

        val node = root["object"] as? JsonObject
        if (node?.string("type") != "schema:TypeAndQuantityNode") return invalid("object must be a schema:TypeAndQuantityNode")
        val product = node["schema:typeOfGood"] as? JsonObject
        if (product?.string("type") != "schema:Product") return invalid("schema:typeOfGood must be a schema:Product")
        val title = product.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("title is required")
        val gtin = product.optional("schema:gtin") ?: return invalid("gtin must be a string")
        val barcode = gtin.value?.trim()

        if (type == "Remove") return ParseResult.Command(RemoveShoppingItem(commandId, homeId, title, barcode))

        val amount = node["schema:amountOfThisGood"]
        val quantity = when {
            amount == null && type == "Add" -> 1
            amount == null -> return invalid("quantity is required")
            amount is JsonPrimitive -> amount.takeUnless { it.isString }?.intOrNull ?: return invalid("quantity must be an integer")
            else -> return invalid("quantity must be an integer")
        }
        if (quantity !in (if (type == "Add") 1 else 0)..AgentCommandParser.MAX_QUANTITY) return invalid("quantity out of range")
        if (type == "Update") return ParseResult.Command(SetShoppingProgress(commandId, homeId, title, barcode, quantity))

        val categories = when (val category = product["schema:category"]) {
            null -> emptyList()
            is JsonPrimitive -> listOf(category.stringOrNull())
            is JsonArray -> category.map { it.stringOrNull() }
            else -> return invalid("category must be a string or an array of strings")
        }
        if (categories.size > 2 || categories.any { it.isNullOrBlank() }) return invalid("category must have one or two non-blank strings")
        val size = product.optional("schema:size") ?: return invalid("size must be a string")
        return ParseResult.Command(
            AddShoppingItem(
                commandId, homeId, title,
                categories.getOrNull(0)?.trim(), categories.getOrNull(1)?.trim(),
                size.value?.trim(), barcode, quantity,
            ),
        )
    }

    /** Answer to [ReadShoppingList]: an `Accept` whose `result` is a `Collection` of `schema:Product`s with their quantities. */
    fun readReply(replyId: String, commandId: String, homeId: String, items: List<ShoppingItemRef>, totalItems: Int): String =
        buildJsonObject {
            put("@context", AgentCommandParser.CONTEXT)
            put("type", "Accept")
            put("id", replyId)
            put("inReplyTo", commandId)
            put("object", commandId)
            put(
                "result",
                buildJsonObject {
                    put("type", "Collection")
                    put("id", documentId(homeId))
                    put("totalItems", JsonPrimitive(totalItems))
                    put("items", buildJsonArray { items.forEach { add(product(it)) } })
                },
            )
        }.toString()

    /** Answer to a write: an `Accept` whose `result` is the line as it is afterwards (for [RemoveShoppingItem], as it was). */
    fun writeReply(replyId: String, commandId: String, item: ShoppingItemRef): String =
        buildJsonObject {
            put("@context", AgentCommandParser.CONTEXT)
            put("type", "Accept")
            put("id", replyId)
            put("inReplyTo", commandId)
            put("object", commandId)
            put("result", product(item))
        }.toString()

    private fun product(item: ShoppingItemRef) = buildJsonObject {
        put("type", "schema:Product")
        put("id", item.key)
        put("schema:name", item.title)
        put(
            "schema:category",
            buildJsonArray {
                add(JsonPrimitive(item.primaryCategory))
                item.secondaryCategory?.let { add(JsonPrimitive(it)) }
            },
        )
        if (item.packageSize.isNotBlank()) put("schema:size", item.packageSize)
        item.barcode?.let { put("schema:gtin", it) }
        put("schema:amountOfThisGood", JsonPrimitive(item.quantity))
        put("got", JsonPrimitive(item.got))
        put("outOfStock", JsonPrimitive(item.outOfStock))
    }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.string(key: String): String? = this[key]?.stringOrNull()

    private class Optional(val value: String?)

    /** Absent/blank gives a null value; present but not a string gives null. */
    private fun JsonObject.optional(key: String): Optional? {
        val element = this[key] ?: return Optional(null)
        val s = element.stringOrNull() ?: return null
        return Optional(s.takeIf { it.isNotBlank() })
    }
}
