package uk.veritaslabs.vesta.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcalExportTest {

    private val utc: (Long) -> Long = { 0L }
    private val day = CalendarDate(2026, 10, 14)
    private val stamp = utcMillisOf(CalendarDate(2026, 10, 1), 12 * 60 + 30, utc)

    private fun timed(title: String = "Dentist", repeat: EventRepeat = EventRepeat.None) = CalendarEvent(
        "e1", EventCalendar.Home, title, allDay = false,
        startMillis = utcMillisOf(day, 9 * 60, utc), endMillis = utcMillisOf(day, 9 * 60 + 30, utc), repeat = repeat,
    )

    private fun lines(event: CalendarEvent) = event.toIcal(stamp).split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun aTimedEventIsOneVeventWithUtcTimes() {
        // Arrange
        val event = timed()

        // Act
        val ical = lines(event)

        // Assert
        assertEquals(listOf("BEGIN:VCALENDAR", "VERSION:2.0"), ical.take(2))
        assertTrue("UID:e1@vesta" in ical)
        assertTrue("DTSTAMP:20261001T123000Z" in ical)
        assertTrue("DTSTART:20261014T090000Z" in ical)
        assertTrue("DTEND:20261014T093000Z" in ical)
        assertTrue("SUMMARY:Dentist" in ical)
        assertEquals(listOf("END:VEVENT", "END:VCALENDAR"), ical.takeLast(2))
        assertFalse(ical.any { it.startsWith("RRULE") || it.startsWith("LOCATION") || it.startsWith("DESCRIPTION") })
    }

    @Test
    fun anAllDayEventUsesDatesAndEndsTheDayAfterItsLastDay() {
        // Arrange
        val event = CalendarEvent("e1", EventCalendar.Home, "Trip", true, allDayMillis(day), allDayMillis(CalendarDate(2026, 10, 31)))

        // Act
        val ical = lines(event)

        // Assert
        assertTrue("DTSTART;VALUE=DATE:20261014" in ical)
        assertTrue("DTEND;VALUE=DATE:20261101" in ical)
    }

    @Test
    fun eachRepeatBecomesARecurrenceRule() {
        // Arrange / Act
        val rules = EventRepeat.entries.associateWith { repeat -> lines(timed(repeat = repeat)).firstOrNull { it.startsWith("RRULE") } }

        // Assert
        assertEquals(null, rules[EventRepeat.None])
        assertEquals("RRULE:FREQ=DAILY", rules[EventRepeat.Daily])
        assertEquals("RRULE:FREQ=WEEKLY", rules[EventRepeat.Weekly])
        assertEquals("RRULE:FREQ=MONTHLY", rules[EventRepeat.Monthly])
        assertEquals("RRULE:FREQ=YEARLY", rules[EventRepeat.Yearly])
    }

    @Test
    fun textIsEscapedAndLocationAndDescriptionAreWrittenWhenThereAreSome() {
        // Arrange
        val event = timed("Lunch; dinner, tea\\").copy(location = "High St, Leeds", description = "Line one\nLine two")

        // Act
        val ical = lines(event)

        // Assert
        assertTrue("SUMMARY:Lunch\\; dinner\\, tea\\\\" in ical)
        assertTrue("LOCATION:High St\\, Leeds" in ical)
        assertTrue("DESCRIPTION:Line one\\nLine two" in ical)
    }

    @Test
    fun longLinesAreFoldedAt75OctetsAndUnfoldBackToTheOriginal() {
        // Arrange
        val title = "Ünïcödé ".repeat(30).trim()
        val event = timed(title)

        // Act
        val raw = event.toIcal(stamp)
        val physical = raw.split("\r\n").filter { it.isNotEmpty() }
        val unfolded = raw.replace("\r\n ", "").split("\r\n").filter { it.isNotEmpty() }

        // Assert
        assertTrue(physical.all { it.encodeToByteArray().size <= 75 })
        assertTrue("SUMMARY:$title" in unfolded)
    }

    @Test
    fun everyLineEndsWithCrlf() {
        // Arrange / Act
        val raw = timed().toIcal(stamp)

        // Assert
        assertTrue(raw.endsWith("END:VCALENDAR\r\n"))
        assertFalse(raw.replace("\r\n", "").contains('\n'))
    }

    @Test
    fun theFileNameIsTheTitleWithUnsafeCharactersReplaced() {
        // Arrange / Act / Assert
        assertEquals("Dentist.ics", timed().icalFileName())
        assertEquals("a_b_c.ics", timed("a/b:c").icalFileName())
        assertEquals("___.ics", timed("///").icalFileName())
        assertEquals("event.ics", timed("").icalFileName())
    }

    @Test
    fun aWholeCalendarIsOneFileWithAVeventEach() {
        // Arrange
        val events = listOf(timed("Dentist"), timed("Bins").copy(id = "e2"))

        // Act
        val ical = icalCalendar(events, stamp).split("\r\n").filter { it.isNotEmpty() }

        // Assert
        assertEquals(1, ical.count { it == "BEGIN:VCALENDAR" })
        assertEquals(2, ical.count { it == "BEGIN:VEVENT" })
        assertEquals(listOf("UID:e1@vesta", "UID:e2@vesta"), ical.filter { it.startsWith("UID") })
        assertEquals("END:VCALENDAR", ical.last())
    }

    @Test
    fun anEmptyCalendarIsStillAValidFile() {
        // Arrange / Act
        val ical = icalCalendar(emptyList(), stamp).split("\r\n").filter { it.isNotEmpty() }

        // Assert
        assertEquals(listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Veritas Labs//Vesta//EN", "CALSCALE:GREGORIAN", "END:VCALENDAR"), ical)
    }
}
