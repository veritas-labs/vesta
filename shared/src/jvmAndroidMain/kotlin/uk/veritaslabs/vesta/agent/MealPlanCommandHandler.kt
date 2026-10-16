package uk.veritaslabs.vesta.agent

import kotlin.time.Clock
import uk.veritaslabs.vesta.automerge.MealPlanDocumentSource
import uk.veritaslabs.vesta.automerge.planMeal
import uk.veritaslabs.vesta.automerge.readMealPlan
import uk.veritaslabs.vesta.automerge.unplanMeal

/** The `Reject` summary for a `Remove` of a recipe that is not planned in that slot on that day. */
const val UnknownMealSummary = "unknown meal"

/**
 * Answers the meal planner commands ([ReadMealPlan] and [MealPlanWriteCommand]) for [PantryCommandHandler], which has
 * already checked the daemon, parsed, and found the sender to be an active assistant. This does the grant gate (same Ask /
 * Allow once / not permitted behaviour as the Pantry's), applies the command, and replies.
 *
 * Reads are answered fresh each time and are not in the command log; writes are recorded in it, so a re-sent id gets its
 * first reply (the handler checks the log before calling [write]). The plan is flat root keys, so unlike the Pantry and the
 * shopping list there is no unseeded state to wait for.
 */
class MealPlanCommandHandler(
    private val resolveSource: suspend (homeId: String) -> MealPlanDocumentSource?,
    private val access: AssistantAccess,
    private val commandLog: CommandLog,
    private val newId: () -> String,
    private val send: suspend (agentId: String, replyJson: String) -> Unit,
    private val log: (String) -> Unit,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    suspend fun read(sender: String, command: ReadMealPlan) {
        val capability = AssistantCapability.MealPlannerRead
        val summary = "Read the meal plan"
        if (!gate(sender, command.homeId, capability, command.commandId, summary)) return
        val source = resolveSource(command.homeId)
        if (source == null) {
            send(sender, reject(command.commandId, "unknown home"))
            access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Rejected, "$summary: unknown home")
            return
        }
        val plan = source.inspect { it.readMealPlan() }
        val meals = plan.days.entries
            .filter { (date, _) -> (command.from == null || date >= command.from) && (command.to == null || date <= command.to) }
            .sortedBy { it.key }
            .flatMap { (date, slots) ->
                slots.entries.sortedBy { it.key.ordinal }.flatMap { (slot, keys) -> keys.map { PlannedMealRef(date, slot, it) } }
            }
        send(sender, MealPlanCommandParser.readReply(newId(), command.commandId, command.homeId, meals.take(MealPlanCommandParser.MAX_ITEMS), meals.size))
        access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Applied, "Read ${meals.size} planned meal(s)")
    }

    suspend fun write(sender: String, command: MealPlanWriteCommand) {
        val capability = AssistantCapability.MealPlannerWrite
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

    private suspend fun apply(command: MealPlanWriteCommand, onReject: (String) -> Unit): String {
        val id = command.commandId
        fun rejected(reason: String) = reject(id, reason).also { onReject(reason) }

        val source = resolveSource(command.homeId) ?: return rejected("unknown home")
        val done = source.mutate { doc ->
            when (command) {
                is PlanMeal -> { planMeal(doc, command.date, command.slot, command.recipeKey, nowMillis()); true }
                is UnplanMeal -> unplanMeal(doc, command.date, command.slot, command.recipeKey)
            }
        }
        if (!done) return rejected(UnknownMealSummary)
        return MealPlanCommandParser.writeReply(newId(), id, PlannedMealRef(command.date, command.slot, command.recipeKey))
    }

    private fun reject(commandId: String, summary: String) = AgentReplies.reject(newId(), commandId, summary)
}
