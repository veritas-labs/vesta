package uk.veritaslabs.vesta.agent

import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.adjustStock
import uk.veritaslabs.vesta.automerge.pantryDedupKey
import uk.veritaslabs.vesta.automerge.readPantryItem
import uk.veritaslabs.vesta.automerge.upsertPantryItem
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

/** Category given to an item an agent adds without one; the Pantry UI otherwise requires a primary category. */
const val UncategorizedCategory = "Uncategorized"

/**
 * Acts on ADR-009 agent commands arriving as x0x direct messages: authorises, parses, applies the
 * add to the Home's Pantry, records the outcome and replies with `Accept`/`Reject`.
 *
 * [resolveSource] returns the Pantry source of a Home, or null if there is no such Home. Messages
 * are handled one at a time so a command and its duplicate can't race past the idempotency check.
 */
class PantryCommandHandler(
    private val resolveSource: suspend (homeId: String) -> PantryDocumentSource?,
    private val allowlist: AgentAllowlist,
    private val commandLog: CommandLog,
    private val client: X0xClient,
    private val newId: () -> String,
    private val log: (String) -> Unit = { System.err.println("[agent-commands] $it") },
) {
    private val lock = Mutex()

    suspend fun handle(message: X0xDirectMessage) = lock.withLock {
        try {
            process(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("failed to process a direct message from ${message.sender}: $e")
        }
    }

    private suspend fun process(message: X0xDirectMessage) {
        val sender = message.sender
        if (message.verified != true || message.trustDecision != "accept" || sender == null || !allowlist.allows(sender)) {
            log(
                "dropped message from ${sender ?: "unknown sender"}: verified=${message.verified}, " +
                    "trust=${message.trustDecision}, allowlisted=${sender?.let(allowlist::allows) ?: false}",
            )
            return
        }

        val payload = message.payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() }
        if (payload == null) {
            log("dropped message from $sender: payload is not base64")
            return
        }

        val parsed = AgentCommandParser.parse(payload)
        val commandId = when (parsed) {
            ParseResult.NotACommand -> return
            is ParseResult.Command -> parsed.command.commandId
            is ParseResult.Invalid -> parsed.commandId ?: run {
                log("dropped command from $sender: ${parsed.reason} (no id to reply to)")
                return
            }
        }

        commandLog.reply(sender, commandId)?.let { previous ->
            log("command $commandId from $sender already processed; re-sending its reply")
            send(sender, previous)
            return
        }

        val reply = when (parsed) {
            is ParseResult.Invalid -> reject(commandId, parsed.reason)
            is ParseResult.Command -> apply(parsed.command)
            ParseResult.NotACommand -> return
        }
        commandLog.record(sender, commandId, reply)
        send(sender, reply)
    }

    /** Applies [command] to its Home's Pantry and returns the reply JSON. */
    private suspend fun apply(command: AddPantryItem): String {
        val source = resolveSource(command.homeId) ?: return reject(command.commandId, "unknown home")

        val (key, item) = source.mutate { doc ->
            val key = pantryDedupKey(command.barcode, command.title)
            val existing = doc.readPantryItem(key)
            upsertPantryItem(
                doc,
                primaryCategory = command.primaryCategory ?: existing?.primaryCategory ?: UncategorizedCategory,
                secondaryCategory = command.secondaryCategory ?: existing?.secondaryCategory,
                title = command.title,
                packageSize = command.packageSize ?: existing?.packageSize.orEmpty(),
                barcode = command.barcode ?: existing?.barcode,
            )
            adjustStock(doc, key, command.quantity)
            key to checkNotNull(doc.readPantryItem(key))
        }
        return AgentReplies.accept(newId(), command.commandId, key, item.title, item.stockCount)
    }

    private fun reject(commandId: String, summary: String) = AgentReplies.reject(newId(), commandId, summary)

    /** Delivery failures are logged, never undone: the sender can re-send the command to get the reply again. */
    private suspend fun send(agentId: String, replyJson: String) {
        try {
            client.sendDirect(agentId, replyJson.encodeToByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("could not deliver reply to $agentId: $e")
        }
    }
}
