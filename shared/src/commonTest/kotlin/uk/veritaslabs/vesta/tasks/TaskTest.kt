package uk.veritaslabs.vesta.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat

class TaskTest {

    private val today = CalendarDate(2026, 10, 14)

    private fun task(
        title: String,
        due: CalendarDate? = today,
        repeat: EventRepeat = EventRepeat.None,
        every: Int = 1,
        time: Int? = null,
        assignees: Set<String> = emptySet(),
    ) = Task(id = title, list = TaskList.Home, title = title, due = due, repeat = repeat, every = every, timeMinute = time, assignees = assignees)

    private fun done(taskId: String, date: CalendarDate?, at: Long = 0L, by: String = "m:1") =
        TaskDone(taskId, date?.isoText() ?: AnytimeOccurrence, at, by)

    @Test
    fun aOneOffTaskIsOnlyDueOnItsDay() {
        // Arrange
        val bins = task("Bins")

        // Act / Assert
        assertTrue(bins.isDueOn(today))
        assertFalse(bins.isDueOn(today.plusDays(1)))
        assertFalse(bins.isDueOn(today.plusDays(-1)))
    }

    @Test
    fun aTaskWithNoDueDateIsOnNoDay() {
        // Arrange
        val someday = task("Sort the loft", due = null)

        // Act / Assert
        assertFalse(someday.isDueOn(today))
        assertNull(someday.latestDueOnOrBefore(today))
    }

    @Test
    fun aRepeatingChoreIsDueOnEveryOccurrenceAndCountsTheInterval() {
        // Arrange
        val hoover = task("Hoover", due = today, repeat = EventRepeat.Weekly, every = 2)

        // Act / Assert
        assertTrue(hoover.isDueOn(today.plusDays(14)))
        assertFalse(hoover.isDueOn(today.plusDays(7)))
    }

    @Test
    fun tickingOneOccurrenceOfAChoreLeavesTheNextOpen() {
        // Arrange
        val water = task("Water plants", repeat = EventRepeat.Daily)
        val index = DoneIndex(listOf(done("Water plants", today)))

        // Act
        val onToday = tasksOn(listOf(water), index, today).single()
        val onTomorrow = tasksOn(listOf(water), index, today.plusDays(1)).single()

        // Assert
        assertTrue(onToday.isDone)
        assertFalse(onTomorrow.isDone)
    }

    @Test
    fun aDayListsOpenTasksFirstThenByTimeThenTitle() {
        // Arrange
        val tasks = listOf(
            task("Zebra"), task("Apple"), task("Late", time = 20 * 60), task("Early", time = 7 * 60), task("Finished"),
        )
        val index = DoneIndex(listOf(done("Finished", today)))

        // Act
        val titles = tasksOn(tasks, index, today).map { it.task.title }

        // Assert
        assertEquals(listOf("Early", "Late", "Apple", "Zebra", "Finished"), titles)
    }

    @Test
    fun aOneOffTaskStaysOverdueUntilItIsDone() {
        // Arrange
        val old = task("Tax", due = today.plusDays(-40))

        // Act
        val open = overdue(listOf(old), DoneIndex(emptyList()), today)
        val ticked = overdue(listOf(old), DoneIndex(listOf(done("Tax", old.due))), today)

        // Assert
        assertEquals(listOf(old.due), open.map { it.date })
        assertTrue(ticked.isEmpty())
    }

    @Test
    fun aTaskDueTodayIsNotOverdue() {
        // Arrange
        val bins = task("Bins")

        // Act
        val result = overdue(listOf(bins), DoneIndex(emptyList()), today)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun aChoreIgnoredForAYearIsOneOverdueItemForItsLatestMissedOccurrence() {
        // Arrange
        val water = task("Water plants", due = today.plusDays(-365), repeat = EventRepeat.Daily)

        // Act
        val result = overdue(listOf(water), DoneIndex(emptyList()), today)

        // Assert
        assertEquals(listOf(today.plusDays(-1)), result.map { it.date })
    }

    @Test
    fun aChoreWhoseLatestMissedOccurrenceIsDoneIsNotOverdue() {
        // Arrange
        val water = task("Water plants", due = today.plusDays(-30), repeat = EventRepeat.Daily)
        val index = DoneIndex(listOf(done("Water plants", today.plusDays(-1))))

        // Act
        val result = overdue(listOf(water), index, today)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun aTaskNotYetStartedIsNotOverdue() {
        // Arrange
        val future = task("Later", due = today.plusDays(3), repeat = EventRepeat.Weekly)

        // Act
        val result = overdue(listOf(future), DoneIndex(emptyList()), today)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun overdueIsOldestFirst() {
        // Arrange
        val tasks = listOf(task("Newer", due = today.plusDays(-2)), task("Older", due = today.plusDays(-9)))

        // Act
        val titles = overdue(tasks, DoneIndex(emptyList()), today).map { it.task.title }

        // Assert
        assertEquals(listOf("Older", "Newer"), titles)
    }

    @Test
    fun upcomingListsOpenOccurrencesForTheNextDaysInOrder() {
        // Arrange
        val tasks = listOf(task("Tomorrow", due = today.plusDays(1)), task("Daily", repeat = EventRepeat.Daily), task("Far", due = today.plusDays(30)))
        val index = DoneIndex(listOf(done("Daily", today.plusDays(1))))

        // Act
        val listed = upcoming(tasks, index, today, days = 3).map { it.task.title to it.date }

        // Assert
        assertEquals(listOf("Daily" to today, "Tomorrow" to today.plusDays(1), "Daily" to today.plusDays(2)), listed)
    }

    @Test
    fun anytimeTasksAreListedByTitleUntilDone() {
        // Arrange
        val tasks = listOf(task("b task", due = null), task("A task", due = null), task("Dated"))
        val index = DoneIndex(listOf(done("b task", null)))

        // Act
        val titles = anytime(tasks, index).map { it.task.title }

        // Assert
        assertEquals(listOf("A task"), titles)
    }

    @Test
    fun recentlyDoneListsTheLastWeekNewestFirstAndDropsOrphans() {
        // Arrange
        val day = 86_400_000L
        val now = 100 * day
        val tasks = listOf(task("Bins"), task("Dishes"))
        val records = listOf(
            done("Bins", today, at = now - 2 * day), done("Dishes", today, at = now - day),
            done("Bins", today.plusDays(-9), at = now - 9 * day), done("Gone", today, at = now - day),
        )

        // Act
        val result = recentlyDone(tasks, records, now, days = 7).map { it.first.title }

        // Assert
        assertEquals(listOf("Dishes", "Bins"), result)
    }

    @Test
    fun aTaskWithNoAssigneesIsForAnyoneAndOtherwiseOnlyTheAssigned() {
        // Arrange
        val anyone = task("Anyone")
        val sam = task("Sam's", assignees = setOf("m:sam"))

        // Act / Assert
        assertTrue(anyone.isFor("m:me"))
        assertTrue(sam.isFor("m:sam"))
        assertFalse(sam.isFor("m:me"))
    }

    @Test
    fun anIsoDateRoundTripsAndBadTextIsRefused() {
        // Arrange
        val date = CalendarDate(2026, 2, 3)

        // Act / Assert
        assertEquals("2026-02-03", date.isoText())
        assertEquals(date, parseIsoDate("2026-02-03"))
        assertNull(parseIsoDate("anytime"))
        assertNull(parseIsoDate("2026-02-30"))
        assertNull(parseIsoDate("2026-13-01"))
    }
}
