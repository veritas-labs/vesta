package uk.veritaslabs.vesta.agent

import org.automerge.Document
import uk.veritaslabs.vesta.automerge.ShoppingItem
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.addShoppingItem
import uk.veritaslabs.vesta.automerge.isShoppingListSeeded
import uk.veritaslabs.vesta.automerge.pantryDedupKey
import uk.veritaslabs.vesta.automerge.readShoppingItem
import uk.veritaslabs.vesta.automerge.readShoppingList
import uk.veritaslabs.vesta.automerge.removeShoppingItem
import uk.veritaslabs.vesta.automerge.setShoppingItemProgress
import uk.veritaslabs.vesta.pantry.PackageSize

/** The `Reject` summary for a write to a shopping list that has not arrived from the Home's creator yet. */
const val ShoppingListNotReadySummary = "the shopping list is not ready yet: retry shortly"

/**
 * Answers the shopping-list commands ([ReadShoppingList] and [ShoppingWriteCommand]) for [PantryCommandHandler], which
 * has already checked the daemon, parsed, and found the sender to be an active assistant. This does the grant gate
 * (same Ask / Allow once / not permitted behaviour as the Pantry's), applies the command, and replies.
 *
 * Reads are answered fresh each time and are not in the command log; writes are recorded in it, so a re-sent id gets
 * its first reply (the handler checks the log before calling [write]).
 */
class ShoppingListCommandHandler(
    private val resolveSource: suspend (homeId: String) -> ShoppingListDocumentSource?,
    private val access: AssistantAccess,
    private val commandLog: CommandLog,
    private val newId: () -> String,
    private val send: suspend (agentId: String, replyJson: String) -> Unit,
    private val log: (String) -> Unit,
) {

    suspend fun read(sender: String, command: ReadShoppingList) {
        val capability = AssistantCapability.ShoppingListRead
        val summary = "Read the shopping list"
        if (!gate(sender, command.homeId, capability, command.commandId, summary)) return
        val source = resolveSource(command.homeId)
        if (source == null) {
            send(sender, reject(command.commandId, "unknown home"))
            access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Rejected, "$summary: unknown home")
            return
        }
        val items = source.inspect { if (it.isShoppingListSeeded()) it.readShoppingList() else emptyList() }
        val refs = items.take(ShoppingListCommandParser.MAX_ITEMS).map { it.toRef() }
        send(sender, ShoppingListCommandParser.readReply(newId(), command.commandId, command.homeId, refs, items.size))
        access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Applied, "Read ${items.size} shopping list item(s)")
    }

    suspend fun write(sender: String, command: ShoppingWriteCommand) {
        val capability = AssistantCapability.ShoppingListWrite
        if (!gate(sender, command.homeId, capability, command.commandId, command.summary)) return

        var rejection: String? = null
        val reply = apply(command) { rejection = it }
        commandLog.record(sender, command.commandId, reply)
        send(sender, reply)
        access.recordActivity(
            sender, command.homeId, capability, command.commandId,
            if (rejection == null) ActivityOutcome.Applied else ActivityOutcome.Rejected,
            rejection?.let { "${command.summary}: $it" } ?: command.summary,
        )
    }

    /** True when the command may go ahead; otherwise it has been rejected or held and answered, and nothing more is to be done. */
    private suspend fun gate(sender: String, homeId: String, capability: AssistantCapability, commandId: String, summary: String): Boolean {
        when (access.decide(sender, homeId, capability, commandId)) {
            AccessDecision.Allow -> return true
            AccessDecision.NotPermitted -> {
                log("rejected command $commandId from $sender: no ${capability.name} grant for home $homeId")
                send(sender, reject(commandId, NotPermittedSummary))
                access.recordActivity(sender, homeId, capability, commandId, ActivityOutcome.Rejected, "$summary: $NotPermittedSummary")
            }
            AccessDecision.Ask -> {
                log("held command $commandId from $sender: waiting for the user to allow it")
                access.hold(sender, homeId, capability, commandId, summary)
                send(sender, reject(commandId, WaitingForApprovalSummary))
            }
        }
        return false
    }

    private suspend fun apply(command: ShoppingWriteCommand, onReject: (String) -> Unit): String {
        val id = command.commandId
        fun rejected(reason: String) = reject(id, reason).also { onReject(reason) }

        val source = resolveSource(command.homeId) ?: return rejected("unknown home")
        val outcome = source.mutate { doc ->
            if (!doc.isShoppingListSeeded()) return@mutate Outcome.NotReady
            when (command) {
                is AddShoppingItem -> {
                    val key = addShoppingItem(
                        doc,
                        command.primaryCategory ?: UncategorizedCategory,
                        command.secondaryCategory,
                        command.title,
                        command.quantity,
                        command.packageSize?.let(PackageSize::normalise).orEmpty(),
                        command.barcode,
                    )
                    Outcome.Done(checkNotNull(doc.readShoppingItem(key)))
                }
                is RemoveShoppingItem -> {
                    val item = doc.findShoppingItem(command.barcode, command.title) ?: return@mutate Outcome.NoSuchItem
                    removeShoppingItem(doc, item.id)
                    Outcome.Done(item)
                }
                is SetShoppingProgress -> {
                    val item = doc.findShoppingItem(command.barcode, command.title) ?: return@mutate Outcome.NoSuchItem
                    setShoppingItemProgress(doc, item.id, command.got, outOfStock = false)
                    Outcome.Done(checkNotNull(doc.readShoppingItem(item.id)))
                }
            }
        }
        return when (outcome) {
            Outcome.NotReady -> rejected(ShoppingListNotReadySummary)
            Outcome.NoSuchItem -> rejected("unknown item")
            is Outcome.Done -> ShoppingListCommandParser.writeReply(newId(), id, outcome.item.toRef())
        }
    }

    private sealed interface Outcome {
        data object NotReady : Outcome
        data object NoSuchItem : Outcome
        data class Done(val item: ShoppingItem) : Outcome
    }

    private fun reject(commandId: String, summary: String) = AgentReplies.reject(newId(), commandId, summary)

    private fun ShoppingItem.toRef() = ShoppingItemRef(id, title, primaryCategory, secondaryCategory, packageSize, barcode, quantity, got, outOfStock)
}

/** The list's item for a product: by [barcode], else by [pantryDedupKey] of the title, else any item titled alike ignoring case. */
private fun Document.findShoppingItem(barcode: String?, title: String): ShoppingItem? =
    barcode?.let { readShoppingItem(it) }
        ?: readShoppingItem(pantryDedupKey(null, title))
        ?: readShoppingList().firstOrNull { it.title.trim().equals(title.trim(), ignoreCase = true) }
