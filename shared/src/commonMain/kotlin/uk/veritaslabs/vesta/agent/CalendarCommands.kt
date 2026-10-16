package uk.veritaslabs.vesta.agent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.allDayDate
import uk.veritaslabs.vesta.calendar.allDayMillis

/** "List the events on the shared calendar of Home [homeId]", optionally those touching [from]..[until] (UTC ms, both ends inclusive). Needs Calendar Read. */
data class ReadCalendar(override val commandId: String, val homeId: String, val from: Long?, val until: Long?) : AgentCommand

/**
 * A moment on the calendar as an agent writes it: [millis] is a UTC instant for a timed event, or the UTC midnight that stands
 * for a date ([allDay], ADR-029) for an all-day one.
 */
data class CalendarMoment(val millis: Long, val allDay: Boolean)

/** A command that changes a Home's shared calendar; needs the Calendar Write capability. */
sealed interface CalendarWriteCommand : AgentCommand {
    val homeId: String

    /** One line for the activity list and the Allow once card, e.g. "Add the event Dentist to the calendar". */
    val summary: String
}

/** "Add this event". An omitted [end] means the same day for an all-day event and one hour for a timed one. */
data class AddCalendarEvent(
    override val commandId: String,
    override val homeId: String,
    val title: String,
    val start: CalendarMoment,
    val end: CalendarMoment?,
    val location: String,
    val description: String,
    val repeat: EventRepeat,
) : CalendarWriteCommand {
    override val summary get() = "Add the event $title to the calendar"
}

/** "Change event [eventId]": every field that is null is left as it is. */
data class UpdateCalendarEvent(
    override val commandId: String,
    override val homeId: String,
    val eventId: String,
    val title: String?,
    val start: CalendarMoment?,
    val end: CalendarMoment?,
    val location: String?,
    val description: String?,
    val repeat: EventRepeat?,
) : CalendarWriteCommand {
    override val summary get() = "Change the calendar event ${title ?: eventId}"
}

/** "Take event [eventId] off the calendar." */
data class RemoveCalendarEvent(
    override val commandId: String,
    override val homeId: String,
    val eventId: String,
) : CalendarWriteCommand {
    override val summary get() = "Remove the calendar event $eventId"
}

/**
 * Decodes the Home calendar half of the agent vocabulary (ADR-027): `Read` of the calendar, and `Add`, `Update` and
 * `Remove` of a `schema:Event`. [parse] returns null when the message is not about a Home calendar, so [AgentCommandParser]
 * carries on. The address is `urn:vesta:document:<homeId>:calendar`; the user's private calendar (ADR-029) has no address here.
 *
 * Times: a timed event's `schema:startDate` / `schema:endDate` are UTC instants such as `2026-10-20T18:30:00Z` (an offset such
 * as `+01:00` is accepted and converted; a time with no offset is refused, since Vesta shows each device's own zone). An
 * all-day event has plain dates `2026-10-20`, and its `schema:endDate` is the LAST day, inclusive.
 */
object CalendarCommandParser {

    const val MAX_EVENTS = 200
    const val MAX_TITLE = 200
    const val MAX_LOCATION = 500
    const val MAX_TEXT = 2_000

    private const val MillisPerDay = 86_400_000L
    private const val MillisPerHour = 3_600_000L
    private val Address = Regex("^urn:vesta:document:([^:]+):calendar$")
    private val DateTime = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})(?::(\\d{2}))?(?:\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$")
    private val Date = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val Frequencies = mapOf(
        "P1D" to EventRepeat.Daily, "P1W" to EventRepeat.Weekly, "P1M" to EventRepeat.Monthly, "P1Y" to EventRepeat.Yearly,
    )

    fun documentId(homeId: String) = "urn:vesta:document:$homeId:calendar"

    fun parse(root: JsonObject, commandId: String): ParseResult? {
        val type = root.string("type")
        return when (type) {
            "Read" -> {
                val node = root["object"] as? JsonObject ?: return null
                val homeId = Address.matchEntire(node.string("id") ?: return null)?.groupValues?.get(1) ?: return null
                parseRead(node, homeId, commandId)
            }
            "Add", "Update", "Remove" -> {
                val homeId = Address.matchEntire((root["target"] as? JsonObject)?.string("id") ?: return null)?.groupValues?.get(1) ?: return null
                val node = root["object"] as? JsonObject
                if (node?.string("type") != "schema:Event") return ParseResult.Invalid(commandId, "object must be a schema:Event")
                when (type) {
                    "Add" -> parseAdd(node, homeId, commandId)
                    "Update" -> parseUpdate(node, homeId, commandId)
                    else -> parseRemove(node, homeId, commandId)
                }
            }
            else -> null
        }
    }

    private fun parseRead(node: JsonObject, homeId: String, commandId: String): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)
        val from = node["schema:startDate"]?.let { moment(it, endOfDay = false) ?: return invalid("schema:startDate must be a date or a date-time with an offset") }
        val until = node["schema:endDate"]?.let { moment(it, endOfDay = true) ?: return invalid("schema:endDate must be a date or a date-time with an offset") }
        if (from != null && until != null && until.millis < from.millis) return invalid("schema:endDate is before schema:startDate")
        return ParseResult.Command(ReadCalendar(commandId, homeId, from?.millis, until?.millis))
    }

    private fun parseRemove(node: JsonObject, homeId: String, commandId: String): ParseResult {
        val id = node.string("schema:identifier")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ParseResult.Invalid(commandId, "schema:identifier is required")
        return ParseResult.Command(RemoveCalendarEvent(commandId, homeId, id))
    }

    private fun parseAdd(node: JsonObject, homeId: String, commandId: String): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)
        val title = node.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("title is required")
        if (title.length > MAX_TITLE) return invalid("title too long")
        val start = moment(node["schema:startDate"] ?: return invalid("schema:startDate is required"), endOfDay = false)
            ?: return invalid("schema:startDate must be a date or a date-time with an offset")
        val end = node["schema:endDate"]?.let { moment(it, endOfDay = false) ?: return invalid("schema:endDate must be a date or a date-time with an offset") }
        checkTimes(start, end)?.let { return invalid(it) }
        val location = text(node, "schema:location", MAX_LOCATION) ?: return invalid("location must be a string of at most $MAX_LOCATION characters")
        val description = text(node, "schema:description", MAX_TEXT) ?: return invalid("description must be a string of at most $MAX_TEXT characters")
        val repeat = repeat(node) ?: return invalid("schema:repeatFrequency must be one of ${Frequencies.keys.joinToString()}")
        return ParseResult.Command(
            AddCalendarEvent(commandId, homeId, title, start, end, location.value.orEmpty(), description.value.orEmpty(), repeat.value ?: EventRepeat.None),
        )
    }

    private fun parseUpdate(node: JsonObject, homeId: String, commandId: String): ParseResult {
        fun invalid(reason: String) = ParseResult.Invalid(commandId, reason)
        val id = node.string("schema:identifier")?.trim()?.takeIf { it.isNotEmpty() } ?: return invalid("schema:identifier is required")
        val title = when {
            "schema:name" !in node -> null
            else -> node.string("schema:name")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TITLE } ?: return invalid("title must be 1 to $MAX_TITLE characters")
        }
        val start = node["schema:startDate"]?.let { moment(it, endOfDay = false) ?: return invalid("schema:startDate must be a date or a date-time with an offset") }
        val end = node["schema:endDate"]?.let { moment(it, endOfDay = false) ?: return invalid("schema:endDate must be a date or a date-time with an offset") }
        checkTimes(start, end)?.let { return invalid(it) }
        val location = text(node, "schema:location", MAX_LOCATION) ?: return invalid("location must be a string of at most $MAX_LOCATION characters")
        val description = text(node, "schema:description", MAX_TEXT) ?: return invalid("description must be a string of at most $MAX_TEXT characters")
        val repeat = repeat(node) ?: return invalid("schema:repeatFrequency must be one of ${Frequencies.keys.joinToString()}")
        if (listOf(title, start, end, location.value, description.value, repeat.value).all { it == null }) return invalid("nothing to change")
        return ParseResult.Command(
            UpdateCalendarEvent(commandId, homeId, id, title, start, end, location.value, description.value, repeat.value),
        )
    }

    /** Both ends must be the same kind and in order, when both are given. */
    private fun checkTimes(start: CalendarMoment?, end: CalendarMoment?): String? {
        if (start == null || end == null) return null
        if (start.allDay != end.allDay) return "schema:startDate and schema:endDate must both be dates or both be date-times"
        if (end.millis < start.millis) return "schema:endDate is before schema:startDate"
        return null
    }

    /** An optional string field: [Optional.value] is null when absent; the result is null when present but not a string or too long. */
    private fun text(node: JsonObject, key: String, max: Int): Optional<String>? {
        val element = node[key] ?: return Optional(null)
        val s = (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() ?: return null
        return if (s.length > max) null else Optional(s)
    }

    /** `schema:eventSchedule` → `schema:repeatFrequency`; an eventSchedule with no frequency means "does not repeat". */
    private fun repeat(node: JsonObject): Optional<EventRepeat>? {
        val schedule = node["schema:eventSchedule"] ?: return Optional(null)
        val frequency = (schedule as? JsonObject)?.string("schema:repeatFrequency") ?: return if (schedule is JsonObject) Optional(EventRepeat.None) else null
        return Frequencies[frequency]?.let { Optional(it) }
    }

    private class Optional<T>(val value: T?)

    /**
     * A date (`2026-10-20`) or a date-time with an offset. With [endOfDay], a date means the last millisecond of that day, which is
     * what an inclusive range end wants; otherwise it is the day's UTC midnight, which is how an all-day event is stored.
     */
    private fun moment(element: JsonElement, endOfDay: Boolean): CalendarMoment? {
        val s = (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() ?: return null
        Date.matchEntire(s)?.let { m ->
            val date = date(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: return null
            val midnight = allDayMillis(date)
            return CalendarMoment(if (endOfDay) midnight + MillisPerDay - 1 else midnight, allDay = true)
        }
        val m = DateTime.matchEntire(s) ?: return null
        val date = date(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: return null
        val hour = m.groupValues[4].toInt()
        val minute = m.groupValues[5].toInt()
        val second = m.groupValues[6].ifEmpty { "0" }.toInt()
        if (hour > 23 || minute > 59 || second > 59) return null
        val offset = m.groupValues[7].let { zone ->
            if (zone == "Z") 0L
            else {
                val h = zone.substring(1, 3).toInt()
                val min = zone.substring(4, 6).toInt()
                if (h > 23 || min > 59) return null
                (h * 60L + min) * 60_000L * (if (zone[0] == '-') -1 else 1)
            }
        }
        return CalendarMoment(allDayMillis(date) + hour * MillisPerHour + minute * 60_000L + second * 1000L - offset, allDay = false)
    }

    private fun date(year: String, month: String, day: String): CalendarDate? {
        val y = year.toInt()
        if (y !in 1970..2200) return null
        return runCatching { CalendarDate(y, month.toInt(), day.toInt()) }.getOrNull()
    }

    /** The default end of an [AddCalendarEvent] with none given. */
    fun defaultEnd(start: CalendarMoment): CalendarMoment =
        if (start.allDay) start else CalendarMoment(start.millis + MillisPerHour, allDay = false)

    /** Whether [event] touches `[from, until]` (either bound may be open). An all-day event covers the whole of its last day. */
    fun overlaps(event: CalendarEvent, from: Long?, until: Long?): Boolean {
        val last = if (event.allDay) event.endMillis + MillisPerDay - 1 else event.endMillis
        if (until != null && event.startMillis > until) return false
        if (from == null) return true
        return last > from || (last == from && last == event.startMillis)
    }

    /** A UTC instant as `2026-10-20T18:30:00Z`. */
    fun instant(millis: Long): String {
        val date = allDayDate(millis)
        val seconds = millis.mod(MillisPerDay) / 1000
        fun two(n: Long) = n.toString().padStart(2, '0')
        return "${dateText(date)}T${two(seconds / 3600)}:${two(seconds / 60 % 60)}:${two(seconds % 60)}Z"
    }

    private fun dateText(date: CalendarDate) =
        "${date.year.toString().padStart(4, '0')}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"

    /** Answer to [ReadCalendar]: an `Accept` whose `result` is a `Collection` of `schema:Event`s, earliest first. */
    fun readReply(replyId: String, commandId: String, homeId: String, events: List<CalendarEvent>, totalItems: Int): String =
        accept(replyId, commandId) {
            put("type", "Collection")
            put("id", documentId(homeId))
            put("totalItems", JsonPrimitive(totalItems))
            put("items", buildJsonArray { events.forEach { add(event(it)) } })
        }

    /** Answer to a write: an `Accept` whose `result` is the event as it is afterwards (for a remove, as it was). */
    fun writeReply(replyId: String, commandId: String, event: CalendarEvent): String =
        accept(replyId, commandId) { event(event).forEach { (k, v) -> put(k, v) } }

    private fun accept(replyId: String, commandId: String, result: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        buildJsonObject {
            put("@context", AgentCommandParser.CONTEXT)
            put("type", "Accept")
            put("id", replyId)
            put("inReplyTo", commandId)
            put("object", commandId)
            put("result", buildJsonObject(result))
        }.toString()

    private fun event(e: CalendarEvent) = buildJsonObject {
        put("type", "schema:Event")
        put("id", e.id)
        put("schema:identifier", e.id)
        put("schema:name", e.title)
        put("schema:startDate", if (e.allDay) dateText(allDayDate(e.startMillis)) else instant(e.startMillis))
        put("schema:endDate", if (e.allDay) dateText(allDayDate(e.endMillis)) else instant(e.endMillis))
        put("allDay", JsonPrimitive(e.allDay))
        if (e.location.isNotBlank()) put("schema:location", e.location)
        if (e.description.isNotBlank()) put("schema:description", e.description)
        Frequencies.entries.firstOrNull { it.value == e.repeat }?.let {
            put("schema:eventSchedule", buildJsonObject { put("type", "schema:Schedule"); put("schema:repeatFrequency", it.key) })
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
