package uk.veritaslabs.vesta.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.allDayMillis

class CalendarCommandParserTest {

    private val context = """["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]"""

    private fun parse(json: String): ParseResult = AgentCommandParser.parse(json.encodeToByteArray())

    private fun write(type: String, node: String, target: String = "urn:vesta:document:home-1:calendar") =
        """{"@context": $context, "type": "$type", "id": "urn:uuid:e1", "target": {"type": "Collection", "id": "$target"}, "object": $node}"""

    private fun read(fields: String = "", objectId: String = "urn:vesta:document:home-1:calendar") =
        """{"@context": $context, "type": "Read", "id": "urn:uuid:r1", "object": {"type": "Collection", "id": "$objectId"$fields}}"""

    private fun command(result: ParseResult) = assertIs<ParseResult.Command>(result).command

    private val day = allDayMillis(CalendarDate(2026, 10, 20))
    private val millisPerDay = 86_400_000L

    @Test
    fun parsesAReadOfTheCalendarWithAndWithoutARange() {
        // Arrange
        val plain = read()
        val ranged = read(""", "schema:startDate": "2026-10-20", "schema:endDate": "2026-10-21"""")

        // Act
        val all = command(parse(plain))
        val some = command(parse(ranged))

        // Assert
        assertEquals(ReadCalendar("urn:uuid:r1", "home-1", null, null), all)
        assertEquals(ReadCalendar("urn:uuid:r1", "home-1", day, day + 2 * millisPerDay - 1), some)
    }

    @Test
    fun aReadRangeMustBeInOrderAndMadeOfDatesOrDateTimesWithAnOffset() {
        // Arrange
        val backwards = read(""", "schema:startDate": "2026-10-21", "schema:endDate": "2026-10-20"""")
        val floating = read(""", "schema:startDate": "2026-10-20T10:00:00"""")

        // Act
        val a = parse(backwards)
        val b = parse(floating)

        // Assert
        assertIs<ParseResult.Invalid>(a)
        assertIs<ParseResult.Invalid>(b)
    }

    @Test
    fun theUsersPrivateCalendarHasNoAddress() {
        // Arrange
        val payload = read(objectId = "urn:vesta:document:home-1:private-calendar")

        // Act
        val result = parse(payload)

        // Assert
        assertIs<ParseResult.Invalid>(result) // never a calendar command
    }

    @Test
    fun parsesATimedEventWithAnOffsetAsUtc() {
        // Arrange
        val payload = write(
            "Add",
            """{"type": "schema:Event", "schema:name": "Dentist", "schema:startDate": "2026-10-20T10:30:00+01:00",
                "schema:endDate": "2026-10-20T11:00:00Z", "schema:location": "High St",
                "schema:eventSchedule": {"type": "schema:Schedule", "schema:repeatFrequency": "P1W"}}""",
        )

        // Act
        val add = assertIs<AddCalendarEvent>(command(parse(payload)))

        // Assert
        assertEquals(CalendarMoment(day + 9 * 3_600_000L + 30 * 60_000L, allDay = false), add.start)
        assertEquals(CalendarMoment(day + 11 * 3_600_000L, allDay = false), add.end)
        assertEquals(listOf("Dentist", "High St", EventRepeat.Weekly), listOf(add.title, add.location, add.repeat))
    }

    @Test
    fun parsesAnAllDayEventFromPlainDates() {
        // Arrange
        val payload = write("Add", """{"type": "schema:Event", "schema:name": "Holiday", "schema:startDate": "2026-10-20", "schema:endDate": "2026-10-22"}""")

        // Act
        val add = assertIs<AddCalendarEvent>(command(parse(payload)))

        // Assert
        assertEquals(CalendarMoment(day, allDay = true), add.start)
        assertEquals(CalendarMoment(day + 2 * millisPerDay, allDay = true), add.end)
        assertEquals(EventRepeat.None, add.repeat)
    }

    @Test
    fun rejectsBadEvents() {
        // Arrange
        fun event(fields: String) = write("Add", """{"type": "schema:Event", $fields}""")
        val cases = listOf(
            "title is required" to event(""""schema:startDate": "2026-10-20""""),
            "schema:startDate is required" to event(""""schema:name": "X""""),
            "schema:startDate must be" to event(""""schema:name": "X", "schema:startDate": "next week""""),
            "schema:startDate must be" to event(""""schema:name": "X", "schema:startDate": "2026-02-30""""),
            "both be dates" to event(""""schema:name": "X", "schema:startDate": "2026-10-20", "schema:endDate": "2026-10-21T10:00:00Z""""),
            "before" to event(""""schema:name": "X", "schema:startDate": "2026-10-21", "schema:endDate": "2026-10-20""""),
            "repeatFrequency" to event(""""schema:name": "X", "schema:startDate": "2026-10-20", "schema:eventSchedule": {"schema:repeatFrequency": "P2D"}"""),
            "object must be a schema:Event" to write("Add", """{"type": "schema:Product"}"""),
        )

        // Act / Assert
        cases.forEach { (expected, payload) ->
            val result = assertIs<ParseResult.Invalid>(parse(payload), expected)
            assertEquals(true, result.reason.contains(expected), "'${result.reason}' should mention '$expected'")
        }
    }

    @Test
    fun parsesAnUpdateOfOnlyTheFieldsGivenAndRefusesAnEmptyOne() {
        // Arrange
        val rename = write("Update", """{"type": "schema:Event", "schema:identifier": "ev-1", "schema:name": "Dentist (moved)", "schema:description": ""}""")
        val nothing = write("Update", """{"type": "schema:Event", "schema:identifier": "ev-1"}""")

        // Act
        val update = assertIs<UpdateCalendarEvent>(command(parse(rename)))
        val empty = parse(nothing)

        // Assert
        assertEquals(listOf("ev-1", "Dentist (moved)", ""), listOf(update.eventId, update.title, update.description))
        assertNull(update.start)
        assertNull(update.location)
        assertEquals("nothing to change", assertIs<ParseResult.Invalid>(empty).reason)
    }

    @Test
    fun parsesARemoveByIdentifierOnly() {
        // Arrange
        val byId = write("Remove", """{"type": "schema:Event", "schema:identifier": "ev-1"}""")
        val byName = write("Remove", """{"type": "schema:Event", "schema:name": "x"}""")

        // Act
        val remove = command(parse(byId))
        val refused = parse(byName)

        // Assert
        assertEquals(RemoveCalendarEvent("urn:uuid:e1", "home-1", "ev-1"), remove)
        assertIs<ParseResult.Invalid>(refused)
    }

    @Test
    fun formatsInstantsAsUtc() {
        // Arrange
        val millis = day + 18 * 3_600_000L + 30 * 60_000L

        // Act
        val text = CalendarCommandParser.instant(millis)

        // Assert
        assertEquals("2026-10-20T18:30:00Z", text)
    }
}
