package uk.veritaslabs.vesta.agent

/** A request an authorised agent can send; [commandId] is the sender's id for it (and, for changes, its idempotency key). */
sealed interface AgentCommand {
    val commandId: String
}

/** "List the Homes on this device" — the agent needs a Home's id before it can address a Pantry. Read-only. */
data class ListHomes(override val commandId: String) : AgentCommand

/** "Show me the Pantry of Home [homeId]" — the most-used items with their stock. Read-only; needs the Pantry Read capability. */
data class ReadPantry(override val commandId: String, val homeId: String) : AgentCommand

/** A command that changes a Home's Pantry; needs the Pantry Write capability. */
sealed interface PantryWriteCommand : AgentCommand {
    val homeId: String

    /** One line for the activity list and the Allow once card, e.g. "Add 2 × Milk to the Pantry". */
    val summary: String
}

/**
 * "Add [quantity] of this product to the Pantry of Home [homeId]" — the one command ADR-009 defines.
 * [commandId] is the sender's idempotency key. Optional product fields are null when the sender omitted them.
 */
data class AddPantryItem(
    override val commandId: String,
    override val homeId: String,
    val title: String,
    val primaryCategory: String?,
    val secondaryCategory: String?,
    val packageSize: String?,
    val barcode: String?,
    val quantity: Int,
) : PantryWriteCommand {
    override val summary get() = "Add $quantity × $title to the Pantry"
}

/**
 * "Take [quantity] of this item out of the Pantry of Home [homeId]" — lowers its stock, never below 0. The item is found by
 * [barcode] if given, else by [title]; it must already exist.
 */
data class ReducePantryStock(
    override val commandId: String,
    override val homeId: String,
    val title: String,
    val barcode: String?,
    val quantity: Int,
) : PantryWriteCommand {
    override val summary get() = "Take $quantity × $title out of the Pantry"
}

/** "Set this item's stock in the Pantry of Home [homeId] to exactly [stockCount]" (0 allowed). The item must already exist. */
data class SetPantryStock(
    override val commandId: String,
    override val homeId: String,
    val title: String,
    val barcode: String?,
    val stockCount: Int,
) : PantryWriteCommand {
    override val summary get() = "Set $title to $stockCount in the Pantry"
}

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
