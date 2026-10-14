package uk.veritaslabs.vesta.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat

class TaskDraftTest {

    private val today = CalendarDate(2026, 10, 14)

    @Test
    fun aTitleAloneMakesAValidAnytimeTask() {
        // Arrange
        val draft = TaskDraft(title = "Sort the loft")

        // Act
        val task = draft.toTask("t1", 5L, "m:a")

        // Assert
        assertTrue(draft.isValid())
        assertNull(task.due)
        assertEquals(EventRepeat.None, task.repeat)
    }

    @Test
    fun aBlankTitleIsNotValid() {
        // Arrange / Act / Assert
        assertFalse(TaskDraft(title = "   ").isValid())
    }

    @Test
    fun aRepeatNeedsADateAndASensibleInterval() {
        // Arrange
        val base = TaskDraft(title = "Hoover", due = today, repeat = EventRepeat.Weekly)

        // Act / Assert
        assertTrue(base.isValid())
        assertFalse(base.copy(due = null).isValid())
        assertFalse(base.copy(everyText = "").isValid())
        assertFalse(base.copy(everyText = "0").isValid())
        assertFalse(base.copy(everyText = "1000").isValid())
        assertTrue(base.copy(everyText = " 3 ").isValid())
    }

    @Test
    fun clearingTheDateClearsTheTimeAndTheRepeat() {
        // Arrange
        val draft = TaskDraft(title = "Hoover", due = today, timeMinute = 9 * 60, repeat = EventRepeat.Weekly, everyText = "2")

        // Act
        val cleared = draft.withDue(null)

        // Assert
        assertNull(cleared.due)
        assertNull(cleared.timeMinute)
        assertEquals(EventRepeat.None, cleared.repeat)
        assertEquals("1", cleared.everyText)
    }

    @Test
    fun choosingARepeatWithNoDateStartsToday() {
        // Arrange
        val draft = TaskDraft(title = "Hoover")

        // Act
        val repeating = draft.withRepeat(EventRepeat.Daily, fallbackDue = today)
        val backToNone = repeating.withRepeat(EventRepeat.None, fallbackDue = today)

        // Assert
        assertEquals(today, repeating.due)
        assertEquals(today, backToNone.due)
        assertEquals("1", backToNone.everyText)
    }

    @Test
    fun movingToThePrivateListDropsTheAssignees() {
        // Arrange
        val draft = TaskDraft(title = "Present", assignees = setOf("m:a"))

        // Act
        val private = draft.withList(TaskList.Private)

        // Assert
        assertTrue(private.assignees.isEmpty())
        assertTrue(private.toTask("t1", 0L, "").assignees.isEmpty())
        assertEquals(setOf("m:a"), draft.withList(TaskList.Home).assignees)
    }

    @Test
    fun aTaskRoundTripsThroughTheDraftAndKeepsWhoMadeIt() {
        // Arrange
        val task = Task(
            "t1", TaskList.Home, "Bins", "Green", today, 8 * 60, EventRepeat.Weekly, 2, setOf("m:a"), createdMillis = 7L, createdBy = "m:a",
        )

        // Act
        val back = TaskDraft.of(task).toTask("t1", task.createdMillis, task.createdBy)

        // Assert
        assertEquals(task, back)
    }

    @Test
    fun theTitleAndDescriptionAreTrimmed() {
        // Arrange
        val draft = TaskDraft(title = "  Bins ", description = " Green one\n")

        // Act
        val task = draft.toTask("t1", 0L, "")

        // Assert
        assertEquals("Bins", task.title)
        assertEquals("Green one", task.description)
    }
}
