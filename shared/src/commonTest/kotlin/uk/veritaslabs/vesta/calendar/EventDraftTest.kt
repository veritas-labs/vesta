package uk.veritaslabs.vesta.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventDraftTest {

    private val utc: (Long) -> Long = { 0L }
    private val day = CalendarDate(2026, 10, 14)

    @Test
    fun aNewDraftIsNineToHalfPastNineOnTheDay() {
        // Arrange / Act
        val draft = EventDraft.on(day)

        // Assert
        assertEquals(9 * 60, draft.startMinute)
        assertEquals(9 * 60 + 30, draft.endMinute)
        assertEquals(day, draft.endDate)
        assertEquals(EventCalendar.Home, draft.calendar)
    }

    @Test
    fun aDraftNeedsATitleAndAnEndNotBeforeItsStart() {
        // Arrange
        val titled = EventDraft.on(day).copy(title = "Dentist")

        // Act / Assert
        assertFalse(EventDraft.on(day).isValid(utc))
        assertTrue(titled.isValid(utc))
        assertFalse(titled.copy(endMinute = 8 * 60).isValid(utc))
        assertTrue(titled.copy(endMinute = titled.startMinute).isValid(utc))
    }

    @Test
    fun movingTheStartCarriesTheEndWithIt() {
        // Arrange
        val draft = EventDraft.on(day)

        // Act
        val later = draft.withStart(minute = 23 * 60 + 45)
        val nextWeek = draft.withStart(date = day.plusDays(7))

        // Assert: still 30 minutes long, across midnight in the first case
        assertEquals(day.plusDays(1), later.endDate)
        assertEquals(15, later.endMinute)
        assertEquals(day.plusDays(7), nextWeek.endDate)
        assertEquals(9 * 60 + 30, nextWeek.endMinute)
    }

    @Test
    fun anEventRoundTripsThroughADraft() {
        // Arrange
        val draft = EventDraft.on(day).copy(
            title = " Dentist ", repeat = EventRepeat.Yearly, color = 3, location = "High St", description = "Bring forms",
            participants = setOf("m:a"),
        )

        // Act
        val event = draft.toEvent("e1", utc)
        val back = EventDraft.of(event, utc)

        // Assert
        assertEquals("Dentist", event.title)
        assertEquals(draft.copy(title = "Dentist"), back)
    }

    @Test
    fun anAllDayDraftStoresDaysNotInstants() {
        // Arrange
        val draft = EventDraft.on(day).copy(title = "Trip", allDay = true, endDate = day.plusDays(2))

        // Act
        val event = draft.toEvent("e1", utc)

        // Assert
        assertEquals(allDayMillis(day), event.startMillis)
        assertEquals(allDayMillis(day.plusDays(2)), event.endMillis)
        assertEquals(day.plusDays(2), EventDraft.of(event, utc).endDate)
    }

    @Test
    fun aPrivateEventHasNoParticipants() {
        // Arrange
        val draft = EventDraft.on(day).copy(title = "Gym", participants = setOf("m:a")).withCalendar(EventCalendar.Private)

        // Act
        val event = draft.toEvent("e1", utc)

        // Assert
        assertTrue(event.participants.isEmpty())
    }

    @Test
    fun changingCalendarChangesTheColourOnlyWhileItIsTheDefault() {
        // Arrange
        val plain = EventDraft.on(day)
        val chosen = EventDraft.on(day).copy(color = 6)

        // Act / Assert
        assertEquals(defaultEventColour(EventCalendar.Private), plain.withCalendar(EventCalendar.Private).color)
        assertEquals(6, chosen.withCalendar(EventCalendar.Private).color)
    }
}
