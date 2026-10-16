package uk.veritaslabs.vesta.agent

import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.adjustStock
import uk.veritaslabs.vesta.automerge.pantryDedupKey
import uk.veritaslabs.vesta.automerge.readPantry
import uk.veritaslabs.vesta.automerge.readPantryItem
import uk.veritaslabs.vesta.automerge.upsertPantryItem
import uk.veritaslabs.vesta.pantry.PackageSize
import uk.veritaslabs.kits.x0x.X0xClient
import uk.veritaslabs.kits.x0x.X0xDirectMessage

/** Category given to an item an agent adds without one; the Pantry UI otherwise requires a primary category. */
const val UncategorizedCategory = "Uncategorized"

/** The `Reject` summary for a command from an agent the user hasn't approved (yet). */
const val NotApprovedSummary = "not approved: ask the user to approve this agent in the Vesta app, then retry"

/** The `Reject` summary for a command the agent holds no grant for (ADR-027). */
const val NotPermittedSummary = "not permitted for this home"

/** The `Reject` summary for a command held until the user taps Allow once; the agent re-sends it afterwards. */
const val WaitingForApprovalSummary = "waiting for approval: the user has been asked, retry once they have answered"

/**
 * Acts on ADR-009 agent commands arriving as x0x direct messages: checks the daemon vouches for the sender,
 * parses, checks the user has approved that agent (or queues it for approval), then either
 * answers a [ListHomes] request or applies an add to the Home's Pantry, recording the outcome, and
 * replies with `Accept`/`Reject`. [ListHomes] is read-only, so it is answered fresh every time and
 * not recorded in the idempotency log.
 *
 * [resolveSource] returns the Pantry source of a Home, or null if there is no such Home. Messages
 * are handled one at a time so a command and its duplicate can't race past the idempotency check.
 */
class PantryCommandHandler(
    private val resolveSource: suspend (homeId: String) -> PantryDocumentSource?,
    private val listHomes: suspend () -> List<HomeRef>,
    private val access: AssistantAccess,
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
        // Gate 1, the x0x daemon's: only a verified sender the user has trusted there. Silent, and it never
        // queues an approval request, so an unknown agent can't make the app prompt.
        val sender = message.sender
        if (message.verified != true || message.trustDecision != "accept" || sender == null) {
            log("dropped message from ${sender ?: "unknown sender"}: verified=${message.verified}, trust=${message.trustDecision}")
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

        // Gate 2, the app's: the sender must be an active assistant (ADR-027). Not recorded in the command log,
        // so the same command re-sent after approval is processed normally.
        if (!access.isActive(sender)) {
            access.requestApproval(sender)
            log("held command $commandId from $sender: waiting for the user to approve this agent")
            send(sender, reject(commandId, NotApprovedSummary))
            return
        }

        if (parsed is ParseResult.Command && parsed.command is ListHomes) {
            val visible = access.visibleHomes(sender)
            val homes = listHomes().filter { visible == null || it.id in visible }
            send(sender, AgentReplies.homes(newId(), commandId, homes))
            access.recordActivity(sender, null, AssistantCapability.ListHomes, commandId, ActivityOutcome.Applied, "Listed ${homes.size} home(s)")
            return
        }

        // Reading a Pantry is answered fresh every time, like ListHomes, so it is not in the command log. Under Ask the
        // agent re-sends the same id after the user's Allow once, which `decide` uses up.
        if (parsed is ParseResult.Command && parsed.command is ReadPantry) {
            readPantry(sender, parsed.command)
            return
        }

        // A repeat of a command already answered gets that answer again, whatever its grant says now: it tells the
        // agent nothing it was not told the first time, and under Ask it must not raise a second card.
        commandLog.reply(sender, commandId)?.let { previous ->
            log("command $commandId from $sender already processed; re-sending its reply")
            send(sender, previous)
            return
        }

        // Gate 3, the grant: this capability on this Home. Checked before anything is looked up, so a Home the
        // assistant has no grant for is indistinguishable from one that doesn't exist.
        if (parsed is ParseResult.Command && parsed.command is AddPantryItem) {
            val command = parsed.command
            val summary = "Add ${command.quantity} × ${command.title} to the Pantry"
            when (access.decide(sender, command.homeId, AssistantCapability.PantryWrite, commandId)) {
                AccessDecision.Allow -> Unit
                AccessDecision.NotPermitted -> {
                    log("rejected command $commandId from $sender: no PantryWrite grant for home ${command.homeId}")
                    send(sender, reject(commandId, NotPermittedSummary))
                    access.recordActivity(sender, command.homeId, AssistantCapability.PantryWrite, commandId, ActivityOutcome.Rejected, "$summary: $NotPermittedSummary")
                    return
                }
                AccessDecision.Ask -> {
                    log("held command $commandId from $sender: waiting for the user to allow it")
                    access.hold(sender, command.homeId, AssistantCapability.PantryWrite, commandId, summary)
                    send(sender, reject(commandId, WaitingForApprovalSummary))
                    return
                }
            }
        }

        var rejection: String? = null
        val reply = when (parsed) {
            is ParseResult.Invalid -> reject(commandId, parsed.reason).also { rejection = parsed.reason }
            is ParseResult.Command -> when (val command = parsed.command) {
                is AddPantryItem -> apply(command) { rejection = it }
                is ListHomes, is ReadPantry -> return // answered above
            }
            ParseResult.NotACommand -> return
        }
        commandLog.record(sender, commandId, reply)
        send(sender, reply)
        val command = (parsed as? ParseResult.Command)?.command as? AddPantryItem
        val summary = command?.let { "Add ${it.quantity} × ${it.title} to the Pantry" } ?: "Invalid command"
        access.recordActivity(
            sender, command?.homeId, AssistantCapability.PantryWrite, commandId,
            if (rejection == null) ActivityOutcome.Applied else ActivityOutcome.Rejected,
            rejection?.let { "$summary: $it" } ?: summary,
        )
    }

    /** Answers [command] with the Home's Pantry if [sender] holds Pantry Read for it, else rejects or holds it. */
    private suspend fun readPantry(sender: String, command: ReadPantry) {
        val capability = AssistantCapability.PantryRead
        val summary = "Read the Pantry"
        when (access.decide(sender, command.homeId, capability, command.commandId)) {
            AccessDecision.Allow -> Unit
            AccessDecision.NotPermitted -> {
                log("rejected command ${command.commandId} from $sender: no PantryRead grant for home ${command.homeId}")
                send(sender, reject(command.commandId, NotPermittedSummary))
                access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Rejected, "$summary: $NotPermittedSummary")
                return
            }
            AccessDecision.Ask -> {
                log("held command ${command.commandId} from $sender: waiting for the user to allow it")
                access.hold(sender, command.homeId, capability, command.commandId, summary)
                send(sender, reject(command.commandId, WaitingForApprovalSummary))
                return
            }
        }
        val source = resolveSource(command.homeId)
        if (source == null) {
            send(sender, reject(command.commandId, "unknown home"))
            access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Rejected, "$summary: unknown home")
            return
        }
        val items = source.inspect { it.readPantry() }
        val refs = items.take(AgentCommandParser.MAX_PANTRY_ITEMS).map {
            PantryItemRef(it.id, it.title, it.primaryCategory, it.secondaryCategory, it.packageSize, it.barcode, it.stockCount)
        }
        send(sender, AgentReplies.pantry(newId(), command.commandId, command.homeId, refs, items.size))
        access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Applied, "Read ${items.size} Pantry item(s)")
    }

    /** Applies [command] to its Home's Pantry and returns the reply JSON. */
    private suspend fun apply(command: AddPantryItem, onReject: (String) -> Unit): String {
        val source = resolveSource(command.homeId) ?: return reject(command.commandId, "unknown home").also { onReject("unknown home") }

        val (key, item) = source.mutate { doc ->
            val key = pantryDedupKey(command.barcode, command.title)
            val existing = doc.readPantryItem(key)
            upsertPantryItem(
                doc,
                primaryCategory = command.primaryCategory ?: existing?.primaryCategory ?: UncategorizedCategory,
                secondaryCategory = command.secondaryCategory ?: existing?.secondaryCategory,
                title = command.title,
                packageSize = command.packageSize?.let(PackageSize::normalise) ?: existing?.packageSize.orEmpty(),
                barcode = command.barcode ?: existing?.barcode,
            )
            adjustStock(doc, key, command.quantity)
            key to checkNotNull(doc.readPantryItem(key))
        }
        return AgentReplies.accept(newId(), command.commandId, key, item.title, item.stockCount, item.packageSize)
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
