package uk.veritaslabs.vesta.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CalendarDateTest {

    @Test
    fun theEpochIsAThursday() {
        // Arrange
        val epoch = CalendarDate(1970, 1, 1)

        // Act
        val dayOfWeek = epoch.dayOfWeek

        // Assert
        assertEquals(3, dayOfWeek)
        assertEquals(0L, epoch.epochDay)
    }

    @Test
    fun knownDatesFallOnTheRightWeekday() {
        // Arrange
        val newYear2000 = CalendarDate(2000, 1, 1)
        val today = CalendarDate(2026, 10, 9)

        // Act
        val saturday = newYear2000.dayOfWeek
        val friday = today.dayOfWeek

        // Assert
        assertEquals(5, saturday)
        assertEquals(4, friday)
    }

    @Test
    fun epochDayRoundTrips() {
        // Arrange
        val dates = listOf(CalendarDate(1899, 12, 31), CalendarDate(2000, 2, 29), CalendarDate(2100, 3, 1), CalendarDate(2026, 10, 9))

        // Act
        val roundTripped = dates.map { CalendarDate.fromEpochDay(it.epochDay) }

        // Assert
        assertEquals(dates, roundTripped)
    }

    @Test
    fun leapYearsFollowTheGregorianRule() {
        // Arrange
        val years = listOf(1900, 2000, 2024, 2026)

        // Act
        val februaryLengths = years.map { daysInMonth(it, 2) }

        // Assert
        assertEquals(listOf(28, 29, 29, 28), februaryLengths)
    }

    @Test
    fun plusMonthsClampsToTheEndOfAShorterMonth() {
        // Arrange
        val thirtyFirst = CalendarDate(2026, 1, 31)

        // Act
        val later = thirtyFirst.plusMonths(1)
        val earlier = CalendarDate(2026, 3, 15).plusMonths(-3)

        // Assert
        assertEquals(CalendarDate(2026, 2, 28), later)
        assertEquals(CalendarDate(2025, 12, 15), earlier)
    }

    @Test
    fun anImpossibleDateIsRejected() {
        // Arrange
        // (nothing to set up)

        // Act
        val failure = runCatching { CalendarDate(2026, 2, 30) }

        // Assert
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertFailsWith<IllegalArgumentException> { CalendarDate(2026, 13, 1) }
    }

    @Test
    fun monthGridIsSixMondayFirstWeeksCoveringTheMonth() {
        // Arrange
        // October 2026 starts on a Thursday.

        // Act
        val grid = monthGrid(2026, 10)

        // Assert
        assertEquals(6, grid.size)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(CalendarDate(2026, 9, 28), grid.first().first())
        assertEquals(CalendarDate(2026, 11, 8), grid.last().last())
        assertTrue(grid.all { it.first().dayOfWeek == 0 })
        assertEquals(31, grid.flatten().count { it.month == 10 })
    }

    @Test
    fun monthGridStartsOnTheChosenDay() {
        // Arrange
        // October 2026 starts on a Thursday.

        // Act
        val sunday = monthGrid(2026, 10, WeekStart.Sunday)
        val saturday = monthGrid(2026, 10, WeekStart.Saturday)

        // Assert
        assertEquals(CalendarDate(2026, 9, 27), sunday.first().first())
        assertEquals(CalendarDate(2026, 9, 26), saturday.first().first())
        assertEquals(31, sunday.flatten().count { it.month == 10 })
    }

    @Test
    fun weekNumbersAreIsoWhicheverDayTheWeekStartsOn() {
        // Arrange
        val monday = monthGrid(2026, 10, WeekStart.Monday)
        val sunday = monthGrid(2026, 10, WeekStart.Sunday)

        // Act
        val mondayNumbers = monday.map(::weekNumber)
        val sundayNumbers = sunday.map(::weekNumber)

        // Assert
        assertEquals(listOf(40, 41, 42, 43, 44, 45), mondayNumbers)
        assertEquals(mondayNumbers, sundayNumbers)
        assertEquals(53, weekNumber(monthGrid(2026, 12).last { it.any { d -> d.month == 12 } }.let { monthGrid(2026, 12)[4] }))
    }
}
