package uk.veritaslabs.vesta.calendar

/** Which calendar an event is on (ADR-029): the Home's, shared with its members, or the user's own, which only their devices see. */
enum class EventCalendar { Home, Private }

/** How often an event comes round again, counting from its first day. */
enum class EventRepeat { None, Daily, Weekly, Monthly, Yearly }

/** How many colours an event can have: [CalendarEvent.color] is an index below this, into the palette the UI draws. */
const val EventColourCount = 8

/**
 * One event. A timed event is two UTC instants, [startMillis] and [endMillis], shown in each device's own time zone. An
 * [allDay] event has no time zone: its two values are [allDayMillis] of its first and last day (the last day is included).
 * [calendar] says which document it lives in and isn't stored in it. [participants] are Home member refs
 * (`MealPerson.ref`s) and only mean something on the Home calendar.
 */
data class CalendarEvent(
    val id: String,
    val calendar: EventCalendar,
    val title: String,
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val repeat: EventRepeat = EventRepeat.None,
    val color: Int = 0,
    val participants: Set<String> = emptySet(),
    val location: String = "",
    val description: String = "",
)

/** Where an event sits on the device's wall clock: its first and last day, and (timed events only) when it starts and ends. */
data class EventSpan(val firstDay: CalendarDate, val lastDay: CalendarDate, val startMinute: Int?, val endMinute: Int?)

fun CalendarEvent.span(offset: (Long) -> Long = ::deviceOffsetMillis): EventSpan {
    if (allDay) return EventSpan(allDayDate(startMillis), allDayDate(maxOf(startMillis, endMillis)), null, null)
    val start = localMoment(startMillis, offset)
    val end = localMoment(maxOf(startMillis, endMillis), offset)
    // Ending exactly at midnight ends the previous day, not the start of the next.
    val lastDay = if (end.minute == 0 && end.date > start.date) end.date.plusDays(-1) else end.date
    return EventSpan(start.date, lastDay, start.minute, end.minute)
}

/** An event on one particular [date]; [isFirstDay] and [isLastDay] say whether that is where this run of it starts or ends. */
data class EventOccurrence(
    val event: CalendarEvent,
    val date: CalendarDate,
    val isFirstDay: Boolean,
    val isLastDay: Boolean,
    val startMinute: Int?,
    val endMinute: Int?,
) {
    /** True when the event takes the whole of this day: an all-day event, or a long one on a day between its first and last. */
    val wholeDay: Boolean get() = startMinute == null || (!isFirstDay && !isLastDay)
}

private const val LongestLookBackDays = 400

/**
 * The events (and repeats of them) that take place on [date], whole-day ones first, then by start time, then by title. An
 * event that runs over several days appears on each, a repeating one on each day it comes round to (and the days it then
 * runs on). A day gets an event once however it matches.
 */
fun eventsOn(events: List<CalendarEvent>, date: CalendarDate, offset: (Long) -> Long = ::deviceOffsetMillis): List<EventOccurrence> =
    events.mapNotNull { event ->
        val span = event.span(offset)
        val length = (span.lastDay.epochDay - span.firstDay.epochDay).toInt()
        (0..minOf(length, LongestLookBackDays)).firstNotNullOfOrNull { back ->
            val runStart = date.plusDays(-back)
            if (event.repeat.recursOn(span.firstDay, runStart)) {
                EventOccurrence(event, date, isFirstDay = back == 0, isLastDay = back == length, span.startMinute, span.endMinute)
            } else {
                null
            }
        }
    }.sortedWith(compareBy({ !it.wholeDay }, { it.sortMinute }, { it.event.title.lowercase() }))

// A run that began on an earlier day is already under way when the day starts.
private val EventOccurrence.sortMinute: Int get() = if (wholeDay || !isFirstDay) 0 else startMinute ?: 0

/** The distinct colours of the events on [date], in the order they list, for the dots under a day. */
fun eventColoursOn(events: List<CalendarEvent>, date: CalendarDate, offset: (Long) -> Long = ::deviceOffsetMillis): List<Int> =
    eventsOn(events, date, offset).map { it.event.color }.distinct()
