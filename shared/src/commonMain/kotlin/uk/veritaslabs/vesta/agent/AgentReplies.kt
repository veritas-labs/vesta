package uk.veritaslabs.vesta.agent

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** ADR-009's AS2 `Accept` / `Reject` replies, as JSON text ready for `sendDirect`. */
object AgentReplies {

    /** [stockAfter] is the item's stock count once the command has been applied. */
    fun accept(replyId: String, commandId: String, key: String, title: String, stockAfter: Int): String =
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
