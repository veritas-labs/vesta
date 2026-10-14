package uk.veritaslabs.vesta.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarEventTest {

    private val utc: (Long) -> Long = { 0L }
    private val plusTwo: (Long) -> Long = { 2 * 3_600_000L }

    private fun timed(
        title: String,
        date: CalendarDate,
        from: Int,
        to: Int,
        repeat: EventRepeat = EventRepeat.None,
        toDate: CalendarDate = date,
        offset: (Long) -> Long = utc,
    ) = CalendarEvent(
        id = title, calendar = EventCalendar.Home, title = title, allDay = false,
        startMillis = utcMillisOf(date, from, offset), endMillis = utcMillisOf(toDate, to, offset), repeat = repeat,
    )

    private fun allDay(title: String, first: CalendarDate, last: CalendarDate = first, repeat: EventRepeat = EventRepeat.None) = CalendarEvent(
        id = title, calendar = EventCalendar.Home, title = title, allDay = true,
        startMillis = allDayMillis(first), endMillis = allDayMillis(last), repeat = repeat,
    )

    private val monday = CalendarDate(2026, 10, 12)

    @Test
    fun aLocalMomentAndItsInstantRoundTripInAnotherTimeZone() {
        // Arrange
        val date = CalendarDate(2026, 10, 14)

        // Act
        val instant = utcMillisOf(date, 9 * 60, plusTwo)
        val back = localMoment(instant, plusTwo)

        // Assert
        assertEquals(LocalMoment(date, 9 * 60), back)
        assertEquals(localMoment(instant, utc), LocalMoment(date, 7 * 60))
    }

    @Test
    fun anEventShowsOnTheDayItsLocalClockPutsItOn() {
        // Arrange
        val lateEvening = timed("Late", monday, 23 * 60, 23 * 60 + 30, offset = plusTwo)

        // Act
        val onMonday = eventsOn(listOf(lateEvening), monday, plusTwo)
        val onMondayInUtc = eventsOn(listOf(lateEvening), monday, utc)
        val onTuesdayInUtc = eventsOn(listOf(lateEvening), monday.plusDays(1), utc)

        // Assert: 23:00 at +02:00 is 21:00 UTC, still Monday on a UTC clock
        assertEquals(1, onMonday.size)
        assertEquals(1, onMondayInUtc.size)
        assertTrue(onTuesdayInUtc.isEmpty())
    }

    @Test
    fun anEventThatEndsAtMidnightDoesNotSpillOntoTheNextDay() {
        // Arrange
        val event = timed("Party", monday, 20 * 60, 0, toDate = monday.plusDays(1))

        // Act
        val next = eventsOn(listOf(event), monday.plusDays(1), utc)

        // Assert
        assertTrue(next.isEmpty())
        assertEquals(monday, event.span(utc).lastDay)
    }

    @Test
    fun aMultiDayEventShowsOnEachDayWithItsFirstAndLastMarked() {
        // Arrange
        val trip = allDay("Trip", monday, monday.plusDays(2))

        // Act
        val days = (0..3).map { eventsOn(listOf(trip), monday.plusDays(it), utc).singleOrNull() }

        // Assert
        assertTrue(days[0]!!.isFirstDay && !days[0]!!.isLastDay)
        assertTrue(!days[1]!!.isFirstDay && !days[1]!!.isLastDay)
        assertTrue(days[2]!!.isLastDay)
        assertEquals(null, days[3])
    }

    @Test
    fun weeklyRepeatsLandOnTheSameWeekdayFromTheFirstDayOnly() {
        // Arrange
        val standup = timed("Standup", monday, 9 * 60, 9 * 60 + 15, EventRepeat.Weekly)

        // Act
        val before = eventsOn(listOf(standup), monday.plusDays(-7), utc)
        val next = eventsOn(listOf(standup), monday.plusDays(14), utc)
        val offDay = eventsOn(listOf(standup), monday.plusDays(15), utc)

        // Assert
        assertTrue(before.isEmpty())
        assertEquals(1, next.size)
        assertTrue(offDay.isEmpty())
    }

    @Test
    fun monthlyAndYearlyRepeatsKeepTheDayOfTheMonth() {
        // Arrange
        val rent = allDay("Rent", CalendarDate(2026, 1, 31), repeat = EventRepeat.Monthly)
        val birthday = allDay("Birthday", CalendarDate(2024, 2, 29), repeat = EventRepeat.Yearly)

        // Act / Assert
        assertEquals(1, eventsOn(listOf(rent), CalendarDate(2026, 3, 31), utc).size)
        assertTrue(eventsOn(listOf(rent), CalendarDate(2026, 2, 28), utc).isEmpty())
        assertEquals(1, eventsOn(listOf(birthday), CalendarDate(2028, 2, 29), utc).size)
        assertTrue(eventsOn(listOf(birthday), CalendarDate(2027, 2, 28), utc).isEmpty())
    }

    @Test
    fun aRepeatingMultiDayEventRunsOnForItsLengthEachTime() {
        // Arrange
        val weekend = allDay("Away", CalendarDate(2026, 10, 10), CalendarDate(2026, 10, 11), EventRepeat.Weekly)

        // Act
        val secondSunday = eventsOn(listOf(weekend), CalendarDate(2026, 10, 18), utc)

        // Assert
        assertEquals(1, secondSunday.size)
        assertTrue(secondSunday.single().isLastDay)
    }

    @Test
    fun wholeDayEventsListFirstThenByStartTime() {
        // Arrange
        val events = listOf(
            timed("Lunch", monday, 12 * 60, 13 * 60),
            timed("Breakfast", monday, 8 * 60, 9 * 60),
            allDay("Holiday", monday),
        )

        // Act
        val titles = eventsOn(events, monday, utc).map { it.event.title }

        // Assert
        assertEquals(listOf("Holiday", "Breakfast", "Lunch"), titles)
    }

    @Test
    fun coloursUnderADayAreDistinct() {
        // Arrange
        val events = listOf(
            timed("A", monday, 8 * 60, 9 * 60).copy(color = 2),
            timed("B", monday, 10 * 60, 11 * 60).copy(color = 2),
            timed("C", monday, 12 * 60, 13 * 60).copy(color = 5),
        )

        // Act
        val colours = eventColoursOn(events, monday, utc)

        // Assert
        assertEquals(listOf(2, 5), colours)
        assertFalse(colours.isEmpty())
    }
}
