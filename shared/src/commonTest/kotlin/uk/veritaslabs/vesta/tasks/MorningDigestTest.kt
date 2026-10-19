package uk.veritaslabs.vesta.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.LocalMoment
import uk.veritaslabs.vesta.calendar.localMoment
import uk.veritaslabs.vesta.calendar.utcMillisOf

class MorningDigestTest {
    private val today = CalendarDate(2026, 10, 19)
    private val me = "m:mark"
    private fun task(id: String, due: CalendarDate? = today, time: Int? = null, assignees: Set<String> = emptySet(), list: TaskList = TaskList.Home,
        repeat: EventRepeat = EventRepeat.None) = Task(id, list, id, due = due, timeMinute = time, assignees = assignees, repeat = repeat)
    private fun titles(tasks: List<Task>, done: List<TaskDone> = emptyList()) =
        untimedToday(tasks, DoneIndex(done), me, today).map { it.task.id }

    @Test
    fun aTaskDueTodayWithNoTimeIsIncludedAndOneWithATimeIsNot() {
        // Arrange
        val tasks = listOf(task("untimed"), task("timed", time = 17 * 60))

        // Act
        val result = titles(tasks)

        // Assert
        assertEquals(listOf("untimed"), result)
    }

    @Test
    fun tasksDueOnOtherDaysAndTasksWithNoDateAreNotIncluded() {
        // Arrange
        val tasks = listOf(task("tomorrow", due = today.plusDays(1)), task("yesterday", due = today.plusDays(-1)), task("anytime", due = null))

        // Act
        val result = titles(tasks)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun aDoneOccurrenceIsLeftOutButTheNextOccurrenceOfARepeatingChoreStillCounts() {
        // Arrange
        val chore = task("bins", due = today.plusDays(-7), repeat = EventRepeat.Weekly)
        val doneLastWeek = TaskDone("bins", today.plusDays(-7).isoText(), 0L, me)
        val doneToday = TaskDone("bins", today.isoText(), 0L, me)

        // Act
        val open = titles(listOf(chore), listOf(doneLastWeek))
        val ticked = titles(listOf(chore), listOf(doneToday))

        // Assert
        assertEquals(listOf("bins"), open)
        assertTrue(ticked.isEmpty())
    }

    @Test
    fun aTaskAssignedToSomeoneElseIsLeftOutAndOneForMeOrAnyoneIsIncluded() {
        // Arrange
        val tasks = listOf(task("sams", assignees = setOf("m:sam")), task("mine", assignees = setOf(me)), task("anyones"), task("private", list = TaskList.Private))

        // Act
        val result = titles(tasks)

        // Assert
        assertEquals(setOf("mine", "anyones", "private"), result.toSet())
    }

    @Test
    fun theDigestIsDueFromSevenAndOnlyOncePerDay() {
        // Arrange
        val sixFiftyNine = LocalMoment(today, MorningDigestMinute - 1)
        val seven = LocalMoment(today, MorningDigestMinute)

        // Act / Assert
        assertFalse(morningDigestDue(sixFiftyNine, null))
        assertTrue(morningDigestDue(seven, null))
        assertTrue(morningDigestDue(seven, today.plusDays(-1)))
        assertFalse(morningDigestDue(seven, today))
    }

    @Test
    fun theNextDigestIsTodaysSevenIfAheadAndTomorrowsIfNot() {
        // Arrange
        val noOffset = { _: Long -> 0L }
        val sixAm = utcMillisOf(today, 6 * 60, noOffset)
        val eightAm = utcMillisOf(today, 8 * 60, noOffset)

        // Act
        val before = nextMorningDigestMillis(sixAm, noOffset)
        val after = nextMorningDigestMillis(eightAm, noOffset)

        // Assert
        assertEquals(LocalMoment(today, MorningDigestMinute), localMoment(before, noOffset))
        assertEquals(LocalMoment(today.plusDays(1), MorningDigestMinute), localMoment(after, noOffset))
    }

    @Test
    fun theNextDigestFollowsTheDeviceOffset() {
        // Arrange
        val plusOneHour = { _: Long -> 3_600_000L }
        val midnightUtc = utcMillisOf(today, 0, { 0L })

        // Act
        val next = nextMorningDigestMillis(midnightUtc, plusOneHour)

        // Assert
        assertEquals(LocalMoment(today, MorningDigestMinute), localMoment(next, plusOneHour))
    }
}
