package uk.veritaslabs.vesta.calendar

private const val MillisPerDay = 86_400_000L
private const val MillisPerSecond = 1_000L
private const val SecondsPerDay = 86_400L
private const val UntitledEvent = "Untitled event"

/**
 * What reading an iCalendar file found: the [events] it could turn into Vesta events (all on the calendar asked for), the number of VEVENTs
 * it had to [skipped] (no readable start, or an override of one occurrence of a repeating event), and the number of
 * [simplifiedRepeats], repeats whose end date, count or interval Vesta can't keep, so they repeat forever every day, week, month or year.
 */
class IcalImportResult(val events: List<CalendarEvent>, val skipped: Int, val simplifiedRepeats: Int)

private class Property(val name: String, val params: Map<String, String>, val value: String)

/**
 * Reads the events of an iCalendar (RFC 5545) file, as Proton, Google, Apple and Outlook write them, and Vesta's own export.
 *
 *  - Times may be UTC (`Z`), in a named zone (`TZID=Europe/London`, converted with the device's zone data; a name it doesn't know is read in the
 *    device's zone) or floating (read in the device's zone). Dates (`VALUE=DATE`) make an all-day event; iCalendar ends those the day after
 *    the last, so one day is taken off.
 *  - An event with no end lasts its `DURATION`, or is instant, or one day for an all-day one.
 *  - The repeat is the `FREQ` of the `RRULE` (daily, weekly, monthly or yearly; others, such as hourly, make no repeat and count as simplified).
 *  - Only title, location and description are kept besides the times, colour is [color]. A VTODO, alarms, attendees and the rest are ignored.
 *  - An event's id is its UID, with Vesta's own `@vesta` taken off, so importing a file Vesta exported updates those events rather than duplicating them. Another
 *    program's UID gets an `ics-` prefix.
 */
fun parseIcal(
    text: String,
    calendar: EventCalendar,
    color: Int = defaultEventColour(calendar),
    deviceOffset: (Long) -> Long = ::deviceOffsetMillis,
    zoneOffset: (String, Long) -> Long? = ::zoneOffsetMillis,
): IcalImportResult {
    val events = mutableListOf<CalendarEvent>()
    var skipped = 0
    var simplified = 0
    var current: MutableList<Property>? = null
    var depth = 0 // nested components inside the VEVENT (alarms), whose properties are not the event's
    unfold(text).forEach { line ->
        val property = parseProperty(line) ?: return@forEach
        when {
            property.name == "BEGIN" && property.value.equals("VEVENT", ignoreCase = true) -> current = mutableListOf()
            property.name == "BEGIN" && current != null -> depth++
            property.name == "END" && property.value.equals("VEVENT", ignoreCase = true) -> {
                current?.let { props ->
                    val event = props.toEvent(calendar, color, deviceOffset, zoneOffset)
                    if (event == null) skipped++ else {
                        events += event.first
                        if (event.second) simplified++
                    }
                }
                current = null
                depth = 0
            }
            property.name == "END" && current != null -> depth--
            current != null && depth == 0 -> current?.add(property)
        }
    }
    return IcalImportResult(events.distinctBy { it.id }, skipped, simplified)
}

private fun unfold(text: String): List<String> =
    text.replace("\r\n", "\n").replace('\r', '\n').replace("\n ", "").replace("\n\t", "").split('\n').filter { it.isNotBlank() }

/** `NAME;PARAM=value:value`, splitting at the first colon that isn't inside a quoted parameter value. */
private fun parseProperty(line: String): Property? {
    var quoted = false
    var colon = -1
    for (i in line.indices) {
        if (line[i] == '"') quoted = !quoted
        if (line[i] == ':' && !quoted) {
            colon = i
            break
        }
    }
    if (colon <= 0) return null
    val head = line.substring(0, colon)
    val parts = head.split(';')
    val params = parts.drop(1).mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) null else part.substring(0, eq).uppercase() to part.substring(eq + 1).trim('"')
    }.toMap()
    return Property(parts[0].uppercase(), params, line.substring(colon + 1))
}

private fun unescapeText(text: String) = buildString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length) {
            append(if (text[i + 1] == 'n' || text[i + 1] == 'N') '\n' else text[i + 1])
            i += 2
        } else {
            append(c)
            i++
        }
    }
}

/** A date or date-time as read: the day, and for a time the seconds into the day and whether it was UTC (`Z`). */
private class IcalTime(val date: CalendarDate, val seconds: Long?, val utc: Boolean)

private fun parseIcalTime(value: String): IcalTime? {
    val text = value.trim()
    fun digits(from: Int, length: Int) = text.drop(from).take(length).takeIf { it.length == length && it.all(Char::isDigit) }?.toInt()
    val year = digits(0, 4) ?: return null
    val month = digits(4, 2) ?: return null
    val day = digits(6, 2) ?: return null
    val date = runCatching { CalendarDate(year, month, day) }.getOrNull() ?: return null
    if (text.length == 8) return IcalTime(date, null, false)
    if (text.getOrNull(8) != 'T') return null
    val hour = digits(9, 2) ?: return null
    val minute = digits(11, 2) ?: return null
    val second = digits(13, 2) ?: 0
    if (hour > 23 || minute > 59 || second > 60) return null
    return IcalTime(date, hour * 3600L + minute * 60 + second, text.endsWith("Z"))
}

/** The UTC instant of [time] on the wall clock of [offset] (the zone it was written in). */
private fun IcalTime.toUtcMillis(offset: (Long) -> Long): Long {
    val seconds = seconds ?: 0L
    if (utc) return date.epochDay * MillisPerDay + seconds * MillisPerSecond
    val naive = date.epochDay * MillisPerDay + seconds * MillisPerSecond
    return naive - offset(naive - offset(naive))
}

/** `P1D`, `PT30M`, `P1W`, `P1DT2H` as seconds; null if it isn't one. */
private fun parseDuration(value: String): Long? {
    var text = value.trim()
    val negative = text.startsWith("-")
    text = text.removePrefix("-").removePrefix("+")
    if (!text.startsWith("P")) return null
    var total = 0L
    var number = ""
    var inTime = false
    text.drop(1).forEach { c ->
        when {
            c.isDigit() -> number += c
            c == 'T' -> inTime = true
            else -> {
                val n = number.toLongOrNull() ?: return null
                number = ""
                total += when (c) {
                    'W' -> n * 7 * SecondsPerDay
                    'D' -> n * SecondsPerDay
                    'H' -> n * 3600
                    'M' -> if (inTime) n * 60 else return null
                    'S' -> n
                    else -> return null
                }
            }
        }
    }
    return if (number.isNotEmpty()) null else if (negative) -total else total
}

/** The event these properties describe, and whether its repeat was simplified; null if it has no readable start or is an override of one occurrence. */
private fun List<Property>.toEvent(
    calendar: EventCalendar,
    color: Int,
    deviceOffset: (Long) -> Long,
    zoneOffset: (String, Long) -> Long?,
): Pair<CalendarEvent, Boolean>? {
    fun first(name: String) = firstOrNull { it.name == name }
    if (first("RECURRENCE-ID") != null) return null
    val startProperty = first("DTSTART") ?: return null
    val start = parseIcalTime(startProperty.value) ?: return null
    val allDay = start.seconds == null || startProperty.params["VALUE"].equals("DATE", ignoreCase = true)

    fun offsetFor(property: Property): (Long) -> Long = property.params["TZID"]?.let { zone ->
        { utc: Long -> zoneOffset(zone, utc) ?: deviceOffset(utc) }
    } ?: deviceOffset

    val endProperty = first("DTEND")
    val end = endProperty?.let { parseIcalTime(it.value) }
    val durationSeconds = first("DURATION")?.let { parseDuration(it.value) }

    val startMillis: Long
    val endMillis: Long
    if (allDay) {
        val last = when {
            end != null -> end.date.plusDays(-1).let { if (it < start.date) start.date else it }
            durationSeconds != null -> start.date.plusDays((durationSeconds / SecondsPerDay).toInt() - 1).let { if (it < start.date) start.date else it }
            else -> start.date
        }
        startMillis = allDayMillis(start.date)
        endMillis = allDayMillis(last)
    } else {
        startMillis = start.toUtcMillis(offsetFor(startProperty))
        val computed = when {
            end != null && endProperty != null -> if (end.seconds == null) allDayMillis(end.date) else end.toUtcMillis(offsetFor(endProperty))
            durationSeconds != null -> startMillis + durationSeconds * MillisPerSecond
            else -> startMillis
        }
        endMillis = maxOf(startMillis, computed)
    }

    val rule = first("RRULE")?.value?.split(';')?.mapNotNull { part -> part.indexOf('=').takeIf { it > 0 }?.let { part.substring(0, it).uppercase() to part.substring(it + 1) } }?.toMap()
    val repeat = when (rule?.get("FREQ")?.uppercase()) {
        "DAILY" -> EventRepeat.Daily
        "WEEKLY" -> EventRepeat.Weekly
        "MONTHLY" -> EventRepeat.Monthly
        "YEARLY" -> EventRepeat.Yearly
        else -> EventRepeat.None
    }
    // A rule with more than a plain frequency (an interval, an end, which weekdays) is kept only as its frequency; so is one Vesta has no repeat for.
    val simplified = rule != null && (repeat == EventRepeat.None || rule.keys.any { it != "FREQ" && !(it == "INTERVAL" && rule[it] == "1") && it != "WKST" })

    val uid = first("UID")?.value?.trim().orEmpty()
    val id = when {
        uid.endsWith("@vesta") && uid.length > "@vesta".length -> uid.removeSuffix("@vesta")
        uid.isNotEmpty() -> "ics-$uid"
        else -> "ics-${startMillis}-${first("SUMMARY")?.value.orEmpty().hashCode()}"
    }
    return CalendarEvent(
        id = id,
        calendar = calendar,
        title = first("SUMMARY")?.value?.let(::unescapeText)?.trim().orEmpty().ifEmpty { UntitledEvent },
        allDay = allDay,
        startMillis = startMillis,
        endMillis = endMillis,
        repeat = repeat,
        color = color,
        location = first("LOCATION")?.value?.let(::unescapeText)?.trim().orEmpty(),
        description = first("DESCRIPTION")?.value?.let(::unescapeText)?.trim().orEmpty(),
    ) to simplified
}
