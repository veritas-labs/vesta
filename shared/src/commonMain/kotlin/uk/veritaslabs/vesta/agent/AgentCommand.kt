package uk.veritaslabs.vesta.agent

/** A request an authorised agent can send; [commandId] is the sender's id for it (and, for changes, its idempotency key). */
sealed interface AgentCommand {
    val commandId: String
}

/** "List the Homes on this device" — the agent needs a Home's id before it can address a Pantry. Read-only. */
data class ListHomes(override val commandId: String) : AgentCommand

/**
 * "Add [quantity] of this product to the Pantry of Home [homeId]" — the one command ADR-009 defines.
 * [commandId] is the sender's idempotency key. Optional product fields are null when the sender omitted them.
 */
data class AddPantryItem(
    override val commandId: String,
    val homeId: String,
    val title: String,
    val primaryCategory: String?,
    val secondaryCategory: String?,
    val packageSize: String?,
    val barcode: String?,
    val quantity: Int,
) : AgentCommand

sealed interface ParseResult {
    data class Command(val command: AgentCommand) : ParseResult

    /**
     * Looks like one of our commands but is malformed. [commandId] is set when it could still be read,
     * so a `Reject` can be sent; [reason] is a short human-readable summary for that `Reject`.
     */
    data class Invalid(val commandId: String?, val reason: String) : ParseResult

    /** Not JSON, or JSON without the expected `@context`: not addressed to this handler. */
    data object NotACommand : ParseResult
}
