package uk.veritaslabs.vesta.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.utcMillisOf

class MorningDigestRunnerTest {
    private val today = CalendarDate(2026, 10, 19)
    private val noOffset = { _: Long -> 0L }
    private val item = MorningDigestItem("Water the plants", "home-1", "Maple Street", false)

    private class Fixture(val today: CalendarDate, minute: Int, var last: CalendarDate? = null, val items: List<MorningDigestItem>) {
        val shown = mutableListOf<List<MorningDigestItem>>()
        var reads = 0
        val clock = utcMillisOf(today, minute, { 0L })
        val runner = MorningDigestRunner(
            read = { reads++; items }, lastShown = { last }, record = { last = it }, show = { shown += it },
            nowMillis = { clock }, offset = { 0L },
        )
    }

    @Test
    fun beforeSevenNothingIsReadOrShown() = runBlocking {
        // Arrange
        val f = Fixture(today, 6 * 60 + 59, items = listOf(item))

        // Act
        f.runner.checkOnce()

        // Assert
        assertEquals(0, f.reads)
        assertEquals(emptyList(), f.shown)
    }

    @Test
    fun afterSevenItShowsOnceAndRemembersTheDay() = runBlocking {
        // Arrange
        val f = Fixture(today, 8 * 60, items = listOf(item))

        // Act
        f.runner.checkOnce()
        f.runner.checkOnce()

        // Assert
        assertEquals(listOf(listOf(item)), f.shown)
        assertEquals(today, f.last)
        assertEquals(1, f.reads)
    }

    @Test
    fun aDayWithNothingDueShowsNothingAndIsNotRetried() = runBlocking {
        // Arrange
        val f = Fixture(today, 8 * 60, items = emptyList())

        // Act
        f.runner.checkOnce()
        f.runner.checkOnce()

        // Assert
        assertEquals(emptyList(), f.shown)
        assertEquals(1, f.reads)
    }

    @Test
    fun aDigestShownYesterdayDoesNotStopToday() = runBlocking {
        // Arrange
        val f = Fixture(today, 9 * 60, last = today.plusDays(-1), items = listOf(item))

        // Act
        f.runner.checkOnce()

        // Assert
        assertEquals(1, f.shown.size)
    }
}
