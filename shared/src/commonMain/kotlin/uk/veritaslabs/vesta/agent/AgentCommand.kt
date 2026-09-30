package uk.veritaslabs.vesta.agent

/**
 * "Add [quantity] of this product to the Pantry of Home [homeId]" — the one command ADR-009 defines.
 * [commandId] is the sender's idempotency key. Optional product fields are null when the sender omitted them.
 */
data class AddPantryItem(
    val commandId: String,
    val homeId: String,
    val title: String,
    val primaryCategory: String?,
    val secondaryCategory: String?,
    val packageSize: String?,
    val barcode: String?,
    val quantity: Int,
)

sealed interface ParseResult {
    data class Command(val command: AddPantryItem) : ParseResult

    /**
     * Looks like one of our commands but is malformed. [commandId] is set when it could still be read,
     * so a `Reject` can be sent; [reason] is a short human-readable summary for that `Reject`.
     */
    data class Invalid(val commandId: String?, val reason: String) : ParseResult

    /** Not JSON, or JSON without the expected `@context`: not addressed to this handler. */
    data object NotACommand : ParseResult
}
