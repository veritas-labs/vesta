package uk.veritaslabs.vesta.agent

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** ADR-009's AS2 `Accept` / `Reject` replies, as JSON text ready for `sendDirect`. */
/** A Home as an agent sees it: enough to pick one and address its Pantry. */
data class HomeRef(val id: String, val name: String)

/** A Pantry item as an agent sees it. Optional fields are null when the item has none. */
data class PantryItemRef(
    val key: String,
    val title: String,
    val primaryCategory: String,
    val secondaryCategory: String?,
    val packageSize: String,
    val barcode: String?,
    val stockCount: Int,
)

object AgentReplies {

    /**
     * Answer to [ReadPantry]: an `Accept` whose `result` is a `Collection` of `schema:Product`s. [totalItems] is how many
     * the Pantry holds; [items] may be fewer (see [AgentCommandParser.MAX_PANTRY_ITEMS]).
     */
    fun pantry(replyId: String, commandId: String, homeId: String, items: List<PantryItemRef>, totalItems: Int): String =
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
                    put("id", "urn:vesta:document:$homeId:pantry")
                    put("totalItems", JsonPrimitive(totalItems))
                    put(
                        "items",
                        buildJsonArray {
                            items.forEach { item ->
                                add(
                                    buildJsonObject {
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
                                        put("schema:amountOfThisGood", JsonPrimitive(item.stockCount))
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }.toString()

    /** Answer to [ListHomes]: an `Accept` whose `result` is a `Collection` of the Homes, each carrying its id. */
    fun homes(replyId: String, commandId: String, homes: List<HomeRef>): String =
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
                    put("id", AgentCommandParser.HOMES_COLLECTION_ID)
                    put("totalItems", JsonPrimitive(homes.size))
                    put(
                        "items",
                        buildJsonArray {
                            homes.forEach { home ->
                                add(
                                    buildJsonObject {
                                        put("type", "Object")
                                        put("id", "urn:vesta:home:${home.id}")
                                        put("schema:identifier", home.id)
                                        put("schema:name", home.name)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }.toString()

    /**
     * [stockAfter] is the item's stock count once the command has been applied; [packageSize] is the size as stored
     * (normalised, ADR-011) and is left out when the item has none.
     */
    fun accept(replyId: String, commandId: String, key: String, title: String, stockAfter: Int, packageSize: String = ""): String =
        buildJsonObject {
            put("@context", AgentCommandParser.CONTEXT)
            put("type", "Accept")
            put("id", replyId)
            put("inReplyTo", commandId)
            put("object", commandId)
            put(
                "result",
                buildJsonObject {
                    put("type", "schema:Product")
                    put("id", key)
                    put("schema:name", title)
                    put("schema:amountOfThisGood", JsonPrimitive(stockAfter))
                    if (packageSize.isNotBlank()) put("schema:size", packageSize)
                },
            )
        }.toString()

    fun reject(replyId: String, commandId: String, summary: String): String =
        buildJsonObject {
            put("@context", AgentCommandParser.CONTEXT)
            put("type", "Reject")
            put("id", replyId)
            put("inReplyTo", commandId)
            put("object", commandId)
            put("summary", summary)
        }.toString()
}
