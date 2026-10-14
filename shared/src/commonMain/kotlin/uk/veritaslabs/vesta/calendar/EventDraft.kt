package uk.veritaslabs.vesta.calendar

private const val DefaultStartMinute = 9 * 60
private const val DefaultLengthMinutes = 30

/** The colour a new event on [calendar] starts with: the Home's is the first of the palette, the private calendar's the fifth. */
fun defaultEventColour(calendar: EventCalendar): Int = if (calendar == EventCalendar.Home) 0 else 4

/**
 * An event being typed into the form: wall-clock dates and minutes, which [toEvent] turns into the instants an event stores. For an
 * all-day draft the minutes are kept (so un-ticking "All day" brings the old times back) but not used.
 */
data class EventDraft(
    val title: String = "",
    val allDay: Boolean = false,
    val startDate: CalendarDate,
    val startMinute: Int = DefaultStartMinute,
    val endDate: CalendarDate = startDate,
    val endMinute: Int = DefaultStartMinute + DefaultLengthMinutes,
    val repeat: EventRepeat = EventRepeat.None,
    val calendar: EventCalendar = EventCalendar.Home,
    val color: Int = defaultEventColour(calendar),
    val participants: Set<String> = emptySet(),
    val location: String = "",
    val description: String = "",
) {
    private fun startInstant(offset: (Long) -> Long) = if (allDay) allDayMillis(startDate) else utcMillisOf(startDate, startMinute, offset)

    private fun endInstant(offset: (Long) -> Long) = if (allDay) allDayMillis(endDate) else utcMillisOf(endDate, endMinute, offset)

    /** A title, and an end that is not before the start. */
    fun isValid(offset: (Long) -> Long = ::deviceOffsetMillis): Boolean = title.isNotBlank() && endInstant(offset) >= startInstant(offset)

    /** The event this draft makes, with id [id]. */
    fun toEvent(id: String, offset: (Long) -> Long = ::deviceOffsetMillis) = CalendarEvent(
        id = id,
        calendar = calendar,
        title = title.trim(),
        allDay = allDay,
        startMillis = startInstant(offset),
        endMillis = endInstant(offset),
        repeat = repeat,
        color = color,
        participants = if (calendar == EventCalendar.Home) participants else emptySet(),
        location = location.trim(),
        description = description.trim(),
    )

    /** Moves the start to [date] and [minute], taking the end with it so the event keeps its length. */
    fun withStart(date: CalendarDate = startDate, minute: Int = startMinute): EventDraft {
        val shift = (date.epochDay - startDate.epochDay) * MinutesPerDay + (minute - startMinute)
        val end = endDate.epochDay * MinutesPerDay + endMinute + shift
        return copy(startDate = date, startMinute = minute, endDate = CalendarDate.fromEpochDay(end.floorDiv(MinutesPerDay.toLong())), endMinute = end.mod(MinutesPerDay.toLong()).toInt())
    }

    /** Moves the calendar, and the colour with it unless the colour was chosen. */
    fun withCalendar(to: EventCalendar): EventDraft =
        copy(calendar = to, color = if (color == defaultEventColour(calendar)) defaultEventColour(to) else color)

    companion object {
        /** A new event on [date], at 09:00 for half an hour. */
        fun on(date: CalendarDate) = EventDraft(startDate = date)

        /** The draft of [event], in the device's time zone. */
        fun of(event: CalendarEvent, offset: (Long) -> Long = ::deviceOffsetMillis): EventDraft {
            val span = event.span(offset)
            val end = if (event.allDay) null else localMoment(maxOf(event.startMillis, event.endMillis), offset)
            return EventDraft(
                title = event.title,
                allDay = event.allDay,
                startDate = span.firstDay,
                startMinute = span.startMinute ?: DefaultStartMinute,
                endDate = end?.date ?: span.lastDay,
                endMinute = end?.minute ?: (DefaultStartMinute + DefaultLengthMinutes),
                repeat = event.repeat,
                calendar = event.calendar,
                color = event.color,
                participants = event.participants,
                location = event.location,
                description = event.description,
            )
        }
    }
}
