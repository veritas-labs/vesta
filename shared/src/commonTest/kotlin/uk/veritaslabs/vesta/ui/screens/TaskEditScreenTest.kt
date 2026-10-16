package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.spaces.MemberRole
import uk.veritaslabs.kits.spaces.MemberListItem
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class TaskEditScreenTest {

    private val today = CalendarDate(2026, 10, 14)
    private val members = listOf(MemberListItem("a", "Alex", MemberRole.Admin, isYou = true), MemberListItem("b", "Sam", MemberRole.Member))

    @Test
    fun aNewTaskOnADayStartsDueThenWithNoTimeAndSaveIsOffUntilThereIsATitle() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { TaskEditScreen(task = null, date = CalendarDate(2026, 10, 20), today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TaskEditTestTags.DueDate).assertTextContains("20 Oct 2026")
        onNodeWithTag(TaskEditTestTags.DueTime).assertTextContains("No time")
        onNodeWithTag(TaskEditTestTags.SaveButton).assertIsNotEnabled()
    }

    @Test
    fun aNewTaskForWheneverHasNoDateAndNoTimeRow() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { TaskEditScreen(task = null, date = null, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TaskEditTestTags.DueDate).assertTextContains("No date")
        onNodeWithTag(TaskEditTestTags.DueTime).assertDoesNotExist()
        onNodeWithTag(TaskEditTestTags.ClearDate).assertDoesNotExist()
    }

    @Test
    fun clearingTheDateHidesTheTimeAndTheRepeat() = runComposeUiTest {
        // Arrange
        val task = Task("t1", TaskList.Home, "Hoover", due = today, timeMinute = 9 * 60, repeat = EventRepeat.Weekly)
        setContent { VestaTheme { TaskEditScreen(task = task, date = null, today = today) } }
        onNodeWithTag(TaskEditTestTags.DueTime).assertTextContains("09:00")

        // Act
        onNodeWithTag(TaskEditTestTags.ClearDate).performClick()

        // Assert
        onNodeWithTag(TaskEditTestTags.DueDate).assertTextContains("No date")
        onNodeWithTag(TaskEditTestTags.DueTime).assertDoesNotExist()
        onNodeWithTag(TaskEditTestTags.EveryField).assertDoesNotExist()
    }

    @Test
    fun clearingTheTimeKeepsTheDate() = runComposeUiTest {
        // Arrange
        val task = Task("t1", TaskList.Home, "Bins", due = today, timeMinute = 9 * 60)
        setContent { VestaTheme { TaskEditScreen(task = task, date = null, today = today) } }

        // Act
        onNodeWithTag(TaskEditTestTags.ClearTime).performClick()

        // Assert
        onNodeWithTag(TaskEditTestTags.DueTime).assertTextContains("No time")
        onNodeWithTag(TaskEditTestTags.DueDate).assertTextContains("14 Oct 2026")
    }

    @Test
    fun choosingARepeatOnATaskWithNoDateStartsItToday() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { TaskEditScreen(task = null, date = null, today = today) } }

        // Act
        onNodeWithTag(TaskEditTestTags.Repeat).performClick()
        onNodeWithTag(TaskEditTestTags.repeatOption(EventRepeat.Monthly)).performClick()

        // Assert
        onNodeWithTag(TaskEditTestTags.DueDate).assertTextContains("14 Oct 2026")
        onNodeWithTag(TaskEditTestTags.EveryField).assertIsDisplayed()
    }

    @Test
    fun savingReportsTheTaskTheFormDescribes() = runComposeUiTest {
        // Arrange
        val saved = mutableListOf<Task>()
        setContent {
            VestaTheme { TaskEditScreen(task = null, date = today, members = members, today = today, newId = { "new-id" }, onSave = { saved += it }) }
        }

        // Act
        onNodeWithTag(TaskEditTestTags.TitleField).performTextInput("Hoover")
        onNodeWithTag(TaskEditTestTags.Repeat).performClick()
        onNodeWithTag(TaskEditTestTags.repeatOption(EventRepeat.Weekly)).performClick()
        onNodeWithTag(TaskEditTestTags.EveryField).performTextReplacement("2")
        onNodeWithTag(TaskEditTestTags.AssigneesField).performClick()
        onNodeWithTag(TaskEditTestTags.assignee("m:b")).performClick()
        onNodeWithTag(TaskEditTestTags.AssigneesDone).performClick()
        onNodeWithTag(TaskEditTestTags.DescriptionField).performTextInput("Stairs too")
        onNodeWithTag(TaskEditTestTags.SaveButton).assertIsEnabled()
        onNodeWithTag(TaskEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(
            listOf(Task("new-id", TaskList.Home, "Hoover", "Stairs too", today, null, EventRepeat.Weekly, 2, setOf("m:b"))),
            saved,
        )
    }

    @Test
    fun editingKeepsTheTaskIdAndWhoMadeIt() = runComposeUiTest {
        // Arrange
        val saved = mutableListOf<Task>()
        val task = Task("t1", TaskList.Home, "Bins", due = today, createdMillis = 7L, createdBy = "m:a")
        setContent { VestaTheme { TaskEditScreen(task = task, date = null, today = today, newId = { "other" }, onSave = { saved += it }) } }

        // Act
        onNodeWithTag(TaskEditTestTags.TitleField).performTextReplacement("Bins out")
        onNodeWithTag(TaskEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(listOf(task.copy(title = "Bins out")), saved)
    }

    @Test
    fun editingShowsDeleteAndAddingDoesNot() = runComposeUiTest {
        // Arrange
        val deleted = mutableListOf<Task>()
        val task = Task("t1", TaskList.Home, "Bins")
        setContent { VestaTheme { TaskEditScreen(task = task, date = null, today = today, onDelete = { deleted += it }) } }

        // Act
        onNodeWithTag(TaskEditTestTags.DeleteButton).performClick()

        // Assert
        assertEquals(listOf(task), deleted)
    }

    @Test
    fun aNewTaskHasNoDeleteButton() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { TaskEditScreen(task = null, date = null, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TaskEditTestTags.DeleteButton).assertDoesNotExist()
    }

    @Test
    fun thePrivateListHidesAssigneesAndSaysWhoSeesIt() = runComposeUiTest {
        // Arrange
        val saved = mutableListOf<Task>()
        val task = Task("t1", TaskList.Home, "Present", assignees = setOf("m:b"))
        setContent { VestaTheme { TaskEditScreen(task = task, date = null, members = members, today = today, onSave = { saved += it }) } }
        onNodeWithTag(TaskEditTestTags.assigneeChip("m:b")).assertIsDisplayed()

        // Act
        onNodeWithTag(TaskEditTestTags.ListField).performClick()
        onNodeWithTag(TaskEditTestTags.listOption(TaskList.Private)).performClick()

        // Assert
        onNodeWithTag(TaskEditTestTags.PrivateNote).assertIsDisplayed()
        onNodeWithTag(TaskEditTestTags.AssigneesField).assertDoesNotExist()
        onNodeWithTag(TaskEditTestTags.SaveButton).performClick()
        assertEquals(TaskList.Private, saved.single().list)
        assertEquals(emptySet(), saved.single().assignees)
    }

    @Test
    fun withNobodyChosenTheFormSaysAnyoneCanDoIt() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { TaskEditScreen(task = null, date = null, members = members, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TaskEditTestTags.AssigneesHint).assertTextEquals("Nobody chosen means anyone can do it.")
    }

    @Test
    fun aChosenAssigneeCanBeRemovedFromItsChip() = runComposeUiTest {
        // Arrange
        val saved = mutableListOf<Task>()
        val task = Task("t1", TaskList.Home, "Bins", assignees = setOf("m:b"))
        setContent { VestaTheme { TaskEditScreen(task = task, date = null, members = members, today = today, onSave = { saved += it }) } }

        // Act
        onNodeWithTag(TaskEditTestTags.assigneeChip("m:b")).performClick()
        onNodeWithTag(TaskEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(emptySet(), saved.single().assignees)
        assertNull(saved.single().due)
    }
}
