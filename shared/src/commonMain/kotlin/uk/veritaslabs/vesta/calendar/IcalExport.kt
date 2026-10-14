package uk.veritaslabs.vesta.calendar

import kotlin.time.Clock

private const val MillisPerDay = 86_400_000L
private const val MaxLineOctets = 75

/** The MIME type of an iCalendar file. */
const val IcalMimeType = "text/calendar"

/** A file name for [this] event's `.ics` export: its title with anything a file system dislikes replaced, or "event" for none. */
fun CalendarEvent.icalFileName(): String = icalFileName(title, "event")

/** `<name>.ics` with anything a file system dislikes in [name] replaced, or [fallback] for a blank name. */
fun icalFileName(name: String, fallback: String): String {
    val safe = name.map { if (it.isLetterOrDigit() || it in " -_.()") it else '_' }.joinToString("").trim().trim('.').ifEmpty { fallback }
    return "$safe.ics"
}

/**
 * This event as an iCalendar (RFC 5545) file holding one VEVENT, which Proton, Google, Apple and Outlook calendars can import. A
 * timed event is written as UTC instants; an all-day one as dates (iCalendar ends those the day after the last). A repeat becomes an
 * RRULE with no end. The UID is the event's id, so importing it again updates the event instead of duplicating it. Participants are
 * not written: they are Home members, with no e-mail address to name. [nowMillis] stamps it.
 */
fun CalendarEvent.toIcal(nowMillis: Long = Clock.System.now().toEpochMilliseconds()): String = icalCalendar(listOf(this), nowMillis)

/** [events] as one iCalendar file with a VEVENT each (see [toIcal]); a file with no events is a valid, empty calendar. */
fun icalCalendar(events: List<CalendarEvent>, nowMillis: Long = Clock.System.now().toEpochMilliseconds()): String {
    val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Veritas Labs//Vesta//EN", "CALSCALE:GREGORIAN")
    events.forEach { lines += it.veventLines(nowMillis) }
    lines += "END:VCALENDAR"
    return lines.joinToString("") { fold(it) + "\r\n" }
}

private fun CalendarEvent.veventLines(nowMillis: Long): List<String> {
    val lines = mutableListOf("BEGIN:VEVENT")
    lines += "UID:$id@vesta"
    lines += "DTSTAMP:${utcStamp(nowMillis)}"
    if (allDay) {
        lines += "DTSTART;VALUE=DATE:${dateStamp(allDayDate(startMillis))}"
        lines += "DTEND;VALUE=DATE:${dateStamp(allDayDate(maxOf(startMillis, endMillis)).plusDays(1))}"
    } else {
        lines += "DTSTART:${utcStamp(startMillis)}"
        lines += "DTEND:${utcStamp(maxOf(startMillis, endMillis))}"
    }
    when (repeat) {
        EventRepeat.None -> Unit
        EventRepeat.Daily -> lines += "RRULE:FREQ=DAILY"
        EventRepeat.Weekly -> lines += "RRULE:FREQ=WEEKLY"
        EventRepeat.Monthly -> lines += "RRULE:FREQ=MONTHLY"
        EventRepeat.Yearly -> lines += "RRULE:FREQ=YEARLY"
    }
    lines += "SUMMARY:${escapeText(title)}"
    if (location.isNotBlank()) lines += "LOCATION:${escapeText(location)}"
    if (description.isNotBlank()) lines += "DESCRIPTION:${escapeText(description)}"
    lines += "END:VEVENT"
    return lines
}

private fun two(n: Int) = n.toString().padStart(2, '0')

private fun dateStamp(date: CalendarDate) = "${date.year.toString().padStart(4, '0')}${two(date.month)}${two(date.day)}"

private fun utcStamp(millis: Long): String {
    val seconds = millis.mod(MillisPerDay) / 1000
    return "${dateStamp(CalendarDate.fromEpochDay(millis.floorDiv(MillisPerDay)))}T${two((seconds / 3600).toInt())}${two((seconds / 60 % 60).toInt())}${two((seconds % 60).toInt())}Z"
}

private fun escapeText(text: String) = buildString {
    text.forEach {
        when (it) {
            '\\' -> append("\\\\")
            ';' -> append("\\;")
            ',' -> append("\\,")
            '\n' -> append("\\n")
            '\r' -> Unit
            else -> append(it)
        }
    }
}

/** Splits a line longer than 75 octets (UTF-8) into continuation lines that start with a space, never inside a character. */
private fun fold(line: String): String {
    val out = StringBuilder()
    var octets = 0
    var i = 0
    while (i < line.length) {
        val unit = if (line[i].isHighSurrogate() && i + 1 < line.length) line.substring(i, i + 2) else line[i].toString()
        val size = unit.encodeToByteArray().size
        if (octets + size > MaxLineOctets) {
            out.append("\r\n ")
            octets = 1
        }
        out.append(unit)
        octets += size
        i += unit.length
    }
    return out.toString()
}
