package uk.veritaslabs.vesta.agent

import java.util.UUID
import kotlinx.coroutines.flow.first
import uk.veritaslabs.vesta.automerge.CalendarDocumentSource
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar

/**
 * The Home calendar half of [PantryCommandHandler] (ADR-027): reads answered fresh, adds, changes and removals applied once per
 * command id. Every command is gated by the assistant's Calendar Read or Write grant on that Home, as Pantry commands are: a missing
 * grant looks the same whether or not the Home exists, and Ask holds the command until Allow once.
 *
 * Only the Home's shared calendar is reachable: [resolveSource] is the shared source, never the user's private one (ADR-029).
 * [PantryCommandHandler] has already checked the daemon's trust and that the sender is an active assistant.
 */
internal class CalendarCommandHandler(
    private val resolveSource: suspend (homeId: String) -> CalendarDocumentSource?,
    private val access: AssistantAccess,
    private val commandLog: CommandLog,
    private val send: suspend (agentId: String, replyJson: String) -> Unit,
    private val newId: () -> String,
    private val log: (String) -> Unit,
) {

    /** Answers a calendar read; false if [command] is not one. Not recorded in the command log: a repeat is answered afresh. */
    suspend fun read(sender: String, command: AgentCommand): Boolean {
        if (command !is ReadCalendar) return false
        val capability = AssistantCapability.CalendarRead
        val summary = "Read the calendar"
        if (!permitted(sender, command.homeId, capability, command.commandId, summary)) return true
        val source = resolveSource(command.homeId)
        if (source == null) {
            send(sender, reject(command.commandId, UnknownHome))
            access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Rejected, "$summary: $UnknownHome")
            return true
        }
        val events = source.events.first()
            .filter { CalendarCommandParser.overlaps(it, command.from, command.until) }
            .sortedWith(compareBy({ it.startMillis }, { it.endMillis }, { it.title.lowercase() }, { it.id }))
        send(sender, CalendarCommandParser.readReply(newId(), command.commandId, command.homeId, events.take(CalendarCommandParser.MAX_EVENTS), events.size))
        access.recordActivity(sender, command.homeId, capability, command.commandId, ActivityOutcome.Applied, "Read ${events.size} calendar event(s)")
        return true
    }

    /**
     * Applies a calendar write; false if [command] is not one. The caller has already re-sent the reply of a command id it answered
     * before, so reaching here means a new command.
     */
    suspend fun write(sender: String, command: AgentCommand): Boolean {
        if (command !is CalendarWriteCommand) return false
        val capability = AssistantCapability.CalendarWrite
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

    private suspend fun apply(command: CalendarWriteCommand, onReject: (String) -> Unit): String {
        fun refuse(reason: String) = reject(command.commandId, reason).also { onReject(reason) }
        val source = resolveSource(command.homeId) ?: return refuse(UnknownHome)
        val events = source.events.first()
        return when (command) {
            is AddCalendarEvent -> {
                val end = command.end ?: CalendarCommandParser.defaultEnd(command.start)
                if (end.millis < command.start.millis) return refuse(EndBeforeStart)
                val duplicate = events.any {
                    it.title.trim().equals(command.title, ignoreCase = true) && it.allDay == command.start.allDay &&
                        it.startMillis == command.start.millis && it.endMillis == end.millis
                }
                if (duplicate) return refuse(EventExists)
                val event = CalendarEvent(
                    id = UUID.randomUUID().toString(),
                    calendar = EventCalendar.Home,
                    title = command.title,
                    allDay = command.start.allDay,
                    startMillis = command.start.millis,
                    endMillis = end.millis,
                    repeat = command.repeat,
                    location = command.location,
                    description = command.description,
                )
                source.save(event)
                CalendarCommandParser.writeReply(newId(), command.commandId, event)
            }
            is UpdateCalendarEvent -> {
                val existing = events.firstOrNull { it.id == command.eventId } ?: return refuse(UnknownEvent)
                val times = newTimes(existing, command) ?: return refuse(EndBeforeStart)
                val changed = existing.copy(
                    title = command.title ?: existing.title,
                    allDay = times.allDay,
                    startMillis = times.start,
                    endMillis = times.end,
                    repeat = command.repeat ?: existing.repeat,
                    location = command.location ?: existing.location,
                    description = command.description ?: existing.description,
                )
                source.save(changed)
                CalendarCommandParser.writeReply(newId(), command.commandId, changed)
            }
            is RemoveCalendarEvent -> {
                val existing = events.firstOrNull { it.id == command.eventId } ?: return refuse(UnknownEvent)
                source.delete(existing.id)
                CalendarCommandParser.writeReply(newId(), command.commandId, existing)
            }
        }
    }

    private class Times(val start: Long, val end: Long, val allDay: Boolean)

    /**
     * The start, end and kind of [existing] once [command] is applied, or null if they would end before they start. A new start with
     * no new end moves the event whole when it keeps its kind, and gets the default length when it changes kind; a new end alone must
     * be the event's own kind.
     */
    private fun newTimes(existing: CalendarEvent, command: UpdateCalendarEvent): Times? {
        val start = command.start
        val end = command.end
        if (start == null && end == null) return Times(existing.startMillis, existing.endMillis, existing.allDay)
        if (start == null) {
            if (end!!.allDay != existing.allDay || end.millis < existing.startMillis) return null
            return Times(existing.startMillis, end.millis, existing.allDay)
        }
        val newEnd = end ?: if (start.allDay == existing.allDay) {
            CalendarMoment(start.millis + (existing.endMillis - existing.startMillis), start.allDay)
        } else {
            CalendarCommandParser.defaultEnd(start)
        }
        if (newEnd.allDay != start.allDay || newEnd.millis < start.millis) return null
        return Times(start.millis, newEnd.millis, start.allDay)
    }

    /** True when the assistant may run [capability] in [homeId]; otherwise the agent has been told why (rejected or held). */
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

    private fun reject(commandId: String, summary: String) = AgentReplies.reject(newId(), commandId, summary)

    private companion object {
        const val UnknownHome = "unknown home"
        const val UnknownEvent = "unknown event"
        const val EventExists = "event already exists"
        const val EndBeforeStart = "the end would be before the start, or not the same kind (date or date-time) as the start"
    }
}
