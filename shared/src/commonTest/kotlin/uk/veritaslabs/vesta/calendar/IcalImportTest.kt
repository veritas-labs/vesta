package uk.veritaslabs.vesta.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcalImportTest {

    private val utc: (Long) -> Long = { 0L }
    private val plusOne: (Long) -> Long = { 3_600_000L }
    private val day = CalendarDate(2026, 10, 14)

    /** Zones for the tests: London on +01:00 and New York on -04:00 (as in October), others unknown. */
    private val zones: (String, Long) -> Long? = { zone, _ ->
        when (zone) {
            "Europe/London" -> 3_600_000L
            "America/New_York" -> -4 * 3_600_000L
            else -> null
        }
    }

    private fun ical(vararg lines: String) = ("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + lines.joinToString("") { "$it\r\n" } + "END:VCALENDAR\r\n")

    private fun vevent(vararg lines: String) = ical("BEGIN:VEVENT", *lines, "END:VEVENT")

    private fun read(text: String, deviceOffset: (Long) -> Long = utc) = parseIcal(text, EventCalendar.Home, deviceOffset = deviceOffset, zoneOffset = zones)

    @Test
    fun aUtcEventKeepsItsInstants() {
        // Arrange
        val text = vevent("UID:abc", "DTSTART:20261014T090000Z", "DTEND:20261014T093000Z", "SUMMARY:Dentist")

        // Act
        val event = read(text).events.single()

        // Assert
        assertEquals("Dentist", event.title)
        assertEquals(utcMillisOf(day, 9 * 60, utc), event.startMillis)
        assertEquals(utcMillisOf(day, 9 * 60 + 30, utc), event.endMillis)
        assertEquals(false, event.allDay)
        assertEquals("ics-abc", event.id)
        assertEquals(EventCalendar.Home, event.calendar)
    }

    @Test
    fun aNamedZoneIsConvertedAndAFloatingTimeIsReadInTheDevicesZone() {
        // Arrange
        val newYork = vevent("DTSTART;TZID=America/New_York:20261014T090000", "DTEND;TZID=America/New_York:20261014T100000", "SUMMARY:Call")
        val floating = vevent("DTSTART:20261014T090000", "DTEND:20261014T100000", "SUMMARY:Local")
        val unknownZone = vevent("DTSTART;TZID=Customized Time Zone:20261014T090000", "SUMMARY:Odd")

        // Act
        val inNewYork = read(newYork).events.single()
        val local = read(floating, deviceOffset = plusOne).events.single()
        val odd = read(unknownZone, deviceOffset = plusOne).events.single()

        // Assert: 09:00 at -04:00 is 13:00 UTC; 09:00 at +01:00 is 08:00 UTC; an unknown zone is the device's
        assertEquals(utcMillisOf(day, 13 * 60, utc), inNewYork.startMillis)
        assertEquals(utcMillisOf(day, 8 * 60, utc), local.startMillis)
        assertEquals(utcMillisOf(day, 8 * 60, utc), odd.startMillis)
    }

    @Test
    fun anAllDayEventEndsTheDayBeforeIcalsExclusiveEnd() {
        // Arrange
        val trip = vevent("DTSTART;VALUE=DATE:20261014", "DTEND;VALUE=DATE:20261017", "SUMMARY:Trip")
        val oneDay = vevent("DTSTART;VALUE=DATE:20261014", "SUMMARY:Bins")
        val twoDays = vevent("DTSTART;VALUE=DATE:20261014", "DURATION:P2D", "SUMMARY:Away")

        // Act
        val a = read(trip).events.single()
        val b = read(oneDay).events.single()
        val c = read(twoDays).events.single()

        // Assert
        assertTrue(a.allDay)
        assertEquals(allDayMillis(day), a.startMillis)
        assertEquals(allDayMillis(day.plusDays(2)), a.endMillis)
        assertEquals(allDayMillis(day), b.endMillis)
        assertEquals(allDayMillis(day.plusDays(1)), c.endMillis)
    }

    @Test
    fun aTimedEventWithADurationOrNoEndGetsAnEnd() {
        // Arrange
        val duration = vevent("DTSTART:20261014T090000Z", "DURATION:PT1H30M", "SUMMARY:Long")
        val noEnd = vevent("DTSTART:20261014T090000Z", "SUMMARY:Instant")

        // Act
        val long = read(duration).events.single()
        val instant = read(noEnd).events.single()

        // Assert
        assertEquals(90 * 60_000L, long.endMillis - long.startMillis)
        assertEquals(instant.startMillis, instant.endMillis)
    }

    @Test
    fun aRepeatRuleBecomesItsFrequencyAndAComplexOneIsCountedAsSimplified() {
        // Arrange
        val plain = vevent("DTSTART:20261014T090000Z", "RRULE:FREQ=WEEKLY", "SUMMARY:Bins")
        val ending = vevent("DTSTART:20261014T090000Z", "RRULE:FREQ=MONTHLY;COUNT=6", "SUMMARY:Rent")
        val hourly = vevent("DTSTART:20261014T090000Z", "RRULE:FREQ=HOURLY", "SUMMARY:Pill")

        // Act
        val a = read(plain)
        val b = read(ending)
        val c = read(hourly)

        // Assert
        assertEquals(EventRepeat.Weekly, a.events.single().repeat)
        assertEquals(0, a.simplifiedRepeats)
        assertEquals(EventRepeat.Monthly, b.events.single().repeat)
        assertEquals(1, b.simplifiedRepeats)
        assertEquals(EventRepeat.None, c.events.single().repeat)
        assertEquals(1, c.simplifiedRepeats)
    }

    @Test
    fun textIsUnescapedAndFoldedLinesAreJoined() {
        // Arrange
        val text = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:20261014T090000Z\r\nSUMMARY:Lunch\\; dinner\\, tea\r\n" +
            "LOCATION:High St\\, Le\r\n eds\r\nDESCRIPTION:One\\nTwo\\\\\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"

        // Act
        val event = read(text).events.single()

        // Assert
        assertEquals("Lunch; dinner, tea", event.title)
        assertEquals("High St, Leeds", event.location)
        assertEquals("One\nTwo\\", event.description)
    }

    @Test
    fun parametersWithQuotedColonsDoNotBreakAProperty() {
        // Arrange
        val text = vevent("DTSTART:20261014T090000Z", "ORGANIZER;CN=\"Sam: the boss\":mailto:sam@example.com", "SUMMARY:Meeting")

        // Act
        val result = read(text)

        // Assert
        assertEquals("Meeting", result.events.single().title)
    }

    @Test
    fun alarmsInsideAnEventDoNotOverwriteItsFields() {
        // Arrange
        val text = ical(
            "BEGIN:VEVENT", "DTSTART:20261014T090000Z", "SUMMARY:Dentist", "BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:Reminder", "SUMMARY:Alarm", "END:VALARM",
            "END:VEVENT",
        )

        // Act
        val event = read(text).events.single()

        // Assert
        assertEquals("Dentist", event.title)
        assertEquals("", event.description)
    }

    @Test
    fun eventsWithNoStartAndOverridesOfOneOccurrenceAreSkippedAndTodosIgnored() {
        // Arrange
        val text = ical(
            "BEGIN:VEVENT", "SUMMARY:No start", "END:VEVENT",
            "BEGIN:VEVENT", "UID:r", "RECURRENCE-ID:20261021T090000Z", "DTSTART:20261021T100000Z", "SUMMARY:Moved", "END:VEVENT",
            "BEGIN:VTODO", "DTSTART:20261014T090000Z", "SUMMARY:A task", "END:VTODO",
            "BEGIN:VEVENT", "DTSTART:20261014T090000Z", "SUMMARY:Good", "END:VEVENT",
        )

        // Act
        val result = read(text)

        // Assert
        assertEquals(listOf("Good"), result.events.map { it.title })
        assertEquals(2, result.skipped)
    }

    @Test
    fun anUntitledEventGetsAName() {
        // Arrange / Act
        val event = read(vevent("DTSTART:20261014T090000Z")).events.single()

        // Assert
        assertEquals("Untitled event", event.title)
    }

    @Test
    fun garbageGivesNoEvents() {
        // Arrange / Act
        val result = read("this is not a calendar")

        // Assert
        assertEquals(0, result.events.size)
        assertEquals(0, result.skipped)
    }

    @Test
    fun aFileVestaExportedComesBackAsTheSameEventsWithTheSameIds() {
        // Arrange
        val events = listOf(
            CalendarEvent("e1", EventCalendar.Home, "Dentist; 9, sharp", false, utcMillisOf(day, 9 * 60, utc), utcMillisOf(day, 9 * 60 + 30, utc), EventRepeat.Weekly, location = "High St, Leeds", description = "Bring forms\nand a coat"),
            CalendarEvent("e2", EventCalendar.Home, "Trip", true, allDayMillis(day), allDayMillis(day.plusDays(2))),
        )

        // Act
        val back = read(icalCalendar(events, 0L)).events

        // Assert: the colour is the import's, the calendar the one asked for
        assertEquals(events.map { it.copy(color = back[0].color) }, back)
    }

    @Test
    fun theSameUidTwiceInOneFileIsOneEvent() {
        // Arrange
        val text = ical(
            "BEGIN:VEVENT", "UID:x", "DTSTART:20261014T090000Z", "SUMMARY:First", "END:VEVENT",
            "BEGIN:VEVENT", "UID:x", "DTSTART:20261014T100000Z", "SUMMARY:Second", "END:VEVENT",
        )

        // Act
        val events = read(text).events

        // Assert
        assertEquals(1, events.size)
    }
}
