package uk.veritaslabs.vesta.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceTest {

    private val first = CalendarDate(2026, 1, 31)

    @Test
    fun nothingRecursBeforeItsFirstDay() {
        // Arrange
        val day = first.plusDays(-7)

        // Act
        val recurs = EventRepeat.entries.filter { it != EventRepeat.None }.map { it.recursOn(first, day) }

        // Assert
        assertTrue(recurs.none { it })
    }

    @Test
    fun aDailyRepeatEveryThreeDaysLandsOnEveryThirdDay() {
        // Arrange
        val days = (0..9).map { first.plusDays(it) }

        // Act
        val landed = days.filter { EventRepeat.Daily.recursOn(first, it, every = 3) }

        // Assert
        assertEquals(listOf(first, first.plusDays(3), first.plusDays(6), first.plusDays(9)), landed)
    }

    @Test
    fun aWeeklyRepeatEveryTwoWeeksSkipsTheWeekBetween() {
        // Arrange
        val oneWeek = first.plusDays(7)
        val twoWeeks = first.plusDays(14)

        // Act / Assert
        assertFalse(EventRepeat.Weekly.recursOn(first, oneWeek, every = 2))
        assertTrue(EventRepeat.Weekly.recursOn(first, twoWeeks, every = 2))
        assertFalse(EventRepeat.Weekly.recursOn(first, first.plusDays(15), every = 2))
    }

    @Test
    fun aMonthlyRepeatOnThe31stSkipsShortMonthsAndCountsEveryNthMonth() {
        // Arrange
        val march31 = CalendarDate(2026, 3, 31)
        val may31 = CalendarDate(2026, 5, 31)

        // Act / Assert
        assertTrue(EventRepeat.Monthly.recursOn(first, march31))
        assertFalse(EventRepeat.Monthly.recursOn(first, CalendarDate(2026, 2, 28)))
        assertTrue(EventRepeat.Monthly.recursOn(first, march31, every = 2))
        assertTrue(EventRepeat.Monthly.recursOn(first, may31, every = 2))
        assertFalse(EventRepeat.Monthly.recursOn(first, CalendarDate(2026, 4, 30), every = 2))
    }

    @Test
    fun aYearlyRepeatOn29FebruarySkipsNonLeapYears() {
        // Arrange
        val leapDay = CalendarDate(2024, 2, 29)

        // Act / Assert
        assertTrue(EventRepeat.Yearly.recursOn(leapDay, CalendarDate(2028, 2, 29)))
        assertFalse(EventRepeat.Yearly.recursOn(leapDay, CalendarDate(2025, 2, 28)))
        assertTrue(EventRepeat.Yearly.recursOn(leapDay, CalendarDate(2028, 2, 29), every = 4))
        assertFalse(EventRepeat.Yearly.recursOn(leapDay, CalendarDate(2028, 2, 29), every = 3))
    }

    @Test
    fun anIntervalBelowOneCountsAsOne() {
        // Arrange
        val next = first.plusDays(1)

        // Act
        val recurs = EventRepeat.Daily.recursOn(first, next, every = 0)

        // Assert
        assertTrue(recurs)
    }

    @Test
    fun latestOnFindsTheLastLandingOnOrBeforeADay() {
        // Arrange
        val start = CalendarDate(2026, 10, 1)
        val limit = CalendarDate(2026, 10, 14)

        // Act / Assert
        assertEquals(CalendarDate(2026, 10, 14), EventRepeat.Daily.latestOn(start, limit))
        assertEquals(CalendarDate(2026, 10, 13), EventRepeat.Daily.latestOn(start, limit, every = 3))
        assertEquals(CalendarDate(2026, 10, 8), EventRepeat.Weekly.latestOn(start, limit))
        assertEquals(CalendarDate(2026, 10, 1), EventRepeat.Weekly.latestOn(start, limit, every = 2))
        assertEquals(CalendarDate(2026, 10, 1), EventRepeat.Monthly.latestOn(start, limit))
        assertEquals(start, EventRepeat.None.latestOn(start, limit))
    }

    @Test
    fun latestOnWalksBackPastMonthsWithoutTheDay() {
        // Arrange
        val limit = CalendarDate(2026, 4, 30)

        // Act
        val monthly = EventRepeat.Monthly.latestOn(first, limit)
        val yearly = EventRepeat.Yearly.latestOn(CalendarDate(2024, 2, 29), CalendarDate(2027, 12, 31))

        // Assert: no 31 April, so the last one is 31 March; the last 29 February before 2028 is 2024's
        assertEquals(CalendarDate(2026, 3, 31), monthly)
        assertEquals(CalendarDate(2024, 2, 29), yearly)
    }

    @Test
    fun latestOnIsNullBeforeTheFirstDay() {
        // Arrange
        val before = first.plusDays(-1)

        // Act / Assert
        assertNull(EventRepeat.Daily.latestOn(first, before))
        assertNull(EventRepeat.None.latestOn(first, before))
    }

    @Test
    fun latestOnAgreesWithRecursOnForEveryRepeat() {
        // Arrange
        val start = CalendarDate(2026, 1, 29)
        val limits = (0..800 step 7).map { start.plusDays(it) }

        // Act / Assert
        for (repeat in EventRepeat.entries) for (every in 1..3) for (limit in limits) {
            val brute = (0..1500).map { limit.plusDays(-it) }.firstOrNull { repeat.recursOn(start, it, every) }
            assertEquals(brute, repeat.latestOn(start, limit, every), "$repeat every $every up to $limit")
        }
    }
}
