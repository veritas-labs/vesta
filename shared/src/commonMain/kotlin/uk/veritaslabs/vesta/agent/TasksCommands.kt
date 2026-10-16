package uk.veritaslabs.vesta.agent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.tasks.isoText
import uk.veritaslabs.vesta.tasks.parseIsoDate

/** "Show me the tasks of Home [homeId]". Read-only; needs the Tasks Read capability. Only the Home's shared tasks, never a private list. */
data class ReadTasks(override val commandId: String, val homeId: String) : AgentCommand

/** A command that changes a Home's shared tasks; needs the Tasks Write capability. */
sealed interface TasksWriteCommand : AgentCommand {
    val homeId: String

    /** One line for the activity list and the Allow once card, e.g. "Add the task Take the bins out". */
    val summary: String
}

/** "Add this task" ([due] null means to do whenever). */
data class AddTask(
    override val commandId: String,
    override val homeId: String,
    val title: String,
    val description: String,
    val due: CalendarDate?,
) : TasksWriteCommand {
    override val summary get() = "Add the task $title"
}

/** "Mark this task done / not done" (its current occurrence, for a repeating one). Found by [taskKey], else [title]. */
data class SetTaskDone(
    override val commandId: String,
    override val homeId: String,
    val taskKey: String?,
    val title: String?,
    val done: Boolean,
) : TasksWriteCommand {
    override val summary get() = "Mark the task ${taskKey ?: title} ${if (done) "done" else "not done"}"
}

/** "Delete this task" (its history stays). Found by [taskKey], else [title]. */
data class RemoveTask(
    override val commandId: String,
    override val homeId: String,
    val taskKey: String?,
    val title: String?,
) : TasksWriteCommand {
    override val summary get() = "Remove the task ${taskKey ?: title}"
}

/** A task as an agent sees it. [due] is the task's own first/only due day; [done] is whether its current occurrence is done. */
data class TaskRef(
    val key: String,
    val title: String,
    val description: String,
    val due: CalendarDate?,
    val repeat: EventRepeat,
    val every: Int,
    val assignees: Set<String>,
    val done: Boolean,
)

/**
 * Parses the shared-tasks commands and builds their replies. [AgentCommandParser] offers every message here first;
 * only a `urn:vesta:document:<homeId>:tasks` address is ours (the private list, `…:private-tasks`, is never addressable,
 * so a message for it falls through to "not a command we know" and is rejected). A task is a `schema:Action`.
 */
object TasksCommandParser {

    /** The most tasks one [ReadTasks] reply carries; `totalItems` still says how many there are. */
    const val MAX_TASKS = 200
    const val MAX_TITLE = 200
    const val MAX_DESCRIPTION = 2000

    const val COMPLETED = "schema:CompletedActionStatus"
    const val NOT_COMPLETED = "schema:PotentialActionStatus"

    private const val PREFIX = "urn:vesta:document:"
    private const val SUFFIX = ":tasks"

    fun documentId(homeId: String) = "$PREFIX$homeId$SUFFIX"

    /** The Home of a shared-tasks document id, or null if [documentId] is not one. */
    fun homeId(documentId: String?): String? =
        documentId?.takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)?.removeSuffix(SUFFIX)?.takeIf { it.isNotBlank() && ':' !in it }

    /** The result for a message addressed to a Home's tasks, or null if it is not addressed to them. */
    fun parse(root: JsonObject, commandId: String): ParseResult? {
        return when (val type = root.string("type")) {
            "Read" -> homeId((root["object"] as? JsonObject)?.string("id"))?.let { ParseResult.Command(ReadTasks(commandId, it)) }
            "Add", "Remove", "Update" -> {
                val homeId = homeId((root["target"] as? JsonObject)?.string("id")) ?: return null
                val node = root["object"] as? JsonObject
                if (node?.string("type") != "schema:Action") return ParseResult.Invalid(commandId, "object must be a schema:Action")
                when (type) {
                    "Add" -> parseAdd(node, homeId, commandId)
                    "Update" -> parseUpdate(node, homeId, commandId)
                    else -> parseRemove(node, homeId, commandId)
                }
            }
            else -> null
        }
    }

    private fun parseAdd(node: JsonObject, homeId: String, commandId: String): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)
        val title = node.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("title is required")
        if (title.length > MAX_TITLE) return invalid("title is too long")
        val description = when (val d = node["schema:description"]) {
            null -> ""
            else -> d.stringOrNull()?.trim() ?: return invalid("description must be a string")
        }
        if (description.length > MAX_DESCRIPTION) return invalid("description is too long")
        val due = when (val e = node["schema:endTime"]) {
            null -> null
            else -> e.stringOrNull()?.let(::parseIsoDate) ?: return invalid("endTime must be a date like 2026-10-14")
        }
        return ParseResult.Command(AddTask(commandId, homeId, title, description, due))
    }

    private fun parseUpdate(node: JsonObject, homeId: String, commandId: String): ParseResult {
        val (key, title) = identify(node) ?: return ParseResult.Invalid(commandId, "schema:identifier or schema:name is required")
        val done = when (node.string("schema:actionStatus")) {
            COMPLETED -> true
            NOT_COMPLETED -> false
            else -> return ParseResult.Invalid(commandId, "schema:actionStatus must be $COMPLETED or $NOT_COMPLETED")
        }
        return ParseResult.Command(SetTaskDone(commandId, homeId, key, title, done))
    }

    private fun parseRemove(node: JsonObject, homeId: String, commandId: String): ParseResult {
        val (key, title) = identify(node) ?: return ParseResult.Invalid(commandId, "schema:identifier or schema:name is required")
        return ParseResult.Command(RemoveTask(commandId, homeId, key, title))
    }

    private fun identify(node: JsonObject): Pair<String?, String?>? {
        val key = node.string("schema:identifier")?.trim()?.takeIf { it.isNotEmpty() }
        val title = node.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() }
        return if (key == null && title == null) null else key to title
    }

    /** Answer to [ReadTasks]: an `Accept` whose `result` is a `Collection` of `schema:Action`s. */
    fun readReply(replyId: String, commandId: String, homeId: String, tasks: List<TaskRef>, totalItems: Int): String =
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
                    put("id", documentId(homeId))
                    put("totalItems", JsonPrimitive(totalItems))
                    put("items", buildJsonArray { tasks.forEach { add(action(it)) } })
                },
            )
        }.toString()

    /** Answer to a write: an `Accept` whose `result` is the task as it is afterwards (for [RemoveTask], as it was). */
    fun writeReply(replyId: String, commandId: String, task: TaskRef): String =
        buildJsonObject {
            put("@context", AgentCommandParser.CONTEXT)
            put("type", "Accept")
            put("id", replyId)
            put("inReplyTo", commandId)
            put("object", commandId)
            put("result", action(task))
        }.toString()

    private fun action(task: TaskRef) = buildJsonObject {
        put("type", "schema:Action")
        put("id", task.key)
        put("schema:identifier", task.key)
        put("schema:name", task.title)
        if (task.description.isNotBlank()) put("schema:description", task.description)
        task.due?.let { put("schema:endTime", it.isoText()) }
        put("schema:actionStatus", if (task.done) COMPLETED else NOT_COMPLETED)
        repeatFrequency(task.repeat, task.every)?.let { put("schema:repeatFrequency", it) }
        if (task.assignees.isNotEmpty()) put("schema:agent", buildJsonArray { task.assignees.sorted().forEach { add(JsonPrimitive(it)) } })
    }

    /** ISO 8601 duration between repeats ("P1W", "P2D"), or null for a task that does not repeat. */
    internal fun repeatFrequency(repeat: EventRepeat, every: Int): String? = when (repeat) {
        EventRepeat.None -> null
        EventRepeat.Daily -> "P${every}D"
        EventRepeat.Weekly -> "P${every}W"
        EventRepeat.Monthly -> "P${every}M"
        EventRepeat.Yearly -> "P${every}Y"
    }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.string(key: String): String? = this[key]?.stringOrNull()
}
