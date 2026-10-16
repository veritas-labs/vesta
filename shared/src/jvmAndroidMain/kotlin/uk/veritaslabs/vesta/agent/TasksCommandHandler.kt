package uk.veritaslabs.vesta.agent

import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import uk.veritaslabs.vesta.automerge.TaskDocumentSource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.today
import uk.veritaslabs.vesta.tasks.AnytimeOccurrence
import uk.veritaslabs.vesta.tasks.DoneIndex
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDone
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.tasks.TaskSnapshot
import uk.veritaslabs.vesta.tasks.isoText

/**
 * The shared-tasks half of [PantryCommandHandler] (ADR-027): reads answered fresh, writes applied once per command id.
 * Every command is gated by the assistant's Tasks Read or Write grant on that Home, as Pantry commands are: a missing grant
 * looks the same whether or not the Home exists, Ask holds the command until Allow once. It reaches only the Home's
 * shared tasks; the user's private list (ADR-029/031) is a different document the resolver never returns.
 *
 * [PantryCommandHandler] has already checked the daemon's trust and that the sender is an active assistant.
 */
@OptIn(ExperimentalUuidApi::class)
internal class TasksCommandHandler(
    private val resolveSource: suspend (homeId: String) -> TaskDocumentSource?,
    private val access: AssistantAccess,
    private val commandLog: CommandLog,
    private val send: suspend (agentId: String, replyJson: String) -> Unit,
    private val newId: () -> String,
    private val log: (String) -> Unit,
    private val today: () -> CalendarDate = { CalendarDate.today() },
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newTaskId: () -> String = { Uuid.random().toString() },
) {

    /** Answers a tasks read; false if [command] is not one. Not recorded in the command log: a repeat is answered afresh. */
    suspend fun read(sender: String, command: AgentCommand): Boolean {
        if (command !is ReadTasks) return false
        val capability = AssistantCapability.TasksRead
        val summary = "Read the tasks"
        if (!permitted(sender, command.homeId, capability, command.commandId, summary)) return true
        val source = resolveSource(command.homeId)
        if (source == null) {
            send(sender, reject(command.commandId, UnknownHome))
            access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Rejected, "$summary: $UnknownHome")
            return true
        }
        val snapshot = source.snapshot.first()
        val done = DoneIndex(snapshot.done)
        val refs = snapshot.tasks.sortedWith(compareBy({ it.due == null }, { it.due }, { it.title.lowercase() }, { it.id }))
            .take(TasksCommandParser.MAX_TASKS).map { it.toRef(done) }
        send(sender, TasksCommandParser.readReply(newId(), command.commandId, command.homeId, refs, snapshot.tasks.size))
        access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Applied, "Read ${snapshot.tasks.size} task(s)")
        return true
    }

    /**
     * Applies a tasks write; false if [command] is not one. The caller has already answered a repeated command id from the
     * log, so reaching here means a new command.
     */
    suspend fun write(sender: String, command: AgentCommand): Boolean {
        if (command !is TasksWriteCommand) return false
        val capability = AssistantCapability.TasksWrite
        if (!permitted(sender, command.homeId, capability, command.commandId, command.summary)) return true

        var rejection: String? = null
        val reply = apply(command) { rejection = it }
        commandLog.record(sender, command.commandId, reply)
        send(sender, reply)
        access.recordActivity(
            sender, command.homeId, capability, command.commandId,
            if (rejection == null) ActivityOutcome.Applied else ActivityOutcome.Rejected,
            rejection?.let { "${command.summary}: $it" } ?: command.summary,
        )
        return true
    }

    /** True when the command may go ahead; otherwise it has been rejected or held and answered. */
    private suspend fun permitted(sender: String, homeId: String, capability: AssistantCapability, commandId: String, summary: String): Boolean {
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

    private suspend fun apply(command: TasksWriteCommand, onReject: (String) -> Unit): String {
        val id = command.commandId
        fun rejected(reason: String) = reject(id, reason).also { onReject(reason) }

        val source = resolveSource(command.homeId) ?: return rejected(UnknownHome)
        val snapshot = source.snapshot.first()
        return when (command) {
            is AddTask -> {
                // A task with this name and day already there means a repeated or looping command, not a second chore.
                if (snapshot.tasks.any { it.title.equals(command.title, ignoreCase = true) && it.due == command.due }) {
                    return rejected("task already exists")
                }
                val task = Task(
                    id = newTaskId(), list = TaskList.Home, title = command.title, description = command.description,
                    due = command.due, createdMillis = nowMillis(),
                )
                source.save(task)
                TasksCommandParser.writeReply(newId(), id, task.toRef(DoneIndex(emptyList())))
            }
            is SetTaskDone -> {
                val task = find(snapshot, command.taskKey, command.title) ?: return rejected(notFoundReason(snapshot, command.taskKey, command.title))
                val occurrence = currentOccurrence(task)
                if (command.done) source.setDone(TaskDone(task.id, occurrence, nowMillis(), by = ""))
                else source.clearDone(task.id, occurrence)
                TasksCommandParser.writeReply(newId(), id, task.toRef(DoneIndex(source.snapshot.first().done)))
            }
            is RemoveTask -> {
                val task = find(snapshot, command.taskKey, command.title) ?: return rejected(notFoundReason(snapshot, command.taskKey, command.title))
                val before = task.toRef(DoneIndex(snapshot.done))
                source.delete(task.id)
                TasksCommandParser.writeReply(newId(), id, before)
            }
        }
    }

    /** The task for [key], else the one titled [title] ignoring case; null if none or (for a title) several. */
    private fun find(snapshot: TaskSnapshot, key: String?, title: String?): Task? =
        key?.let { k -> snapshot.tasks.firstOrNull { it.id == k } }
            ?: title?.takeIf { key == null }?.let { t -> snapshot.tasks.filter { it.title.equals(t, ignoreCase = true) }.singleOrNull() }

    /** Why [find] found nothing: "unknown task", or that a title names several tasks. */
    private fun notFoundReason(snapshot: TaskSnapshot, key: String?, title: String?): String {
        val several = key == null && title != null && snapshot.tasks.count { it.title.equals(title, ignoreCase = true) } > 1
        return if (several) "several tasks have that name: use schema:identifier" else "unknown task"
    }

    /** The occurrence a done tick applies to: the one a no-date task has, a one-off's due day, a repeating task's latest due day so far. */
    private fun currentOccurrence(task: Task): String {
        val due = task.due ?: return AnytimeOccurrence
        return (task.latestDueOnOrBefore(today()) ?: due).isoText()
    }

    private fun Task.toRef(done: DoneIndex) = TaskRef(
        key = id, title = title, description = description, due = due, repeat = repeat, every = every,
        assignees = assignees, done = done[id, currentOccurrence(this)] != null,
    )

    private fun reject(commandId: String, summary: String) = AgentReplies.reject(newId(), commandId, summary)

    private companion object {
        const val UnknownHome = "unknown home"
    }
}
