package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskDone
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.tasks.isoText
import uk.veritaslabs.vesta.ui.components.EmptyStateTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class TasksScreenTest {

    // 2026-10-14 is a Wednesday, so a Monday week is 12-18 October.
    private val today = CalendarDate(2026, 10, 14)
    private val members = listOf(MemberListItem("a", "Alex", MemberRole.Admin, isYou = true), MemberListItem("b", "Sam", MemberRole.Member))

    private fun task(id: String, title: String, due: CalendarDate? = today, list: TaskList = TaskList.Home, assignees: Set<String> = emptySet()) =
        Task(id = id, list = list, title = title, due = due, assignees = assignees)

    @Test
    fun showsTheWeekContainingTodayStartingOnTheChosenDay() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { TasksScreen(tasks = emptyList(), today = today, weekStart = WeekStart.Monday) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TasksTestTags.WeekRange).assertTextEquals("12 Oct – 18 Oct 2026")
        onNodeWithTag(TasksTestTags.day(CalendarDate(2026, 10, 12))).assertExists()
        onNodeWithTag(TasksTestTags.day(CalendarDate(2026, 10, 19))).assertDoesNotExist()
    }

    @Test
    fun theWeekControlsMoveByAWeekAndOfferAWayBack() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { TasksScreen(tasks = emptyList(), today = today) } }
        onNodeWithTag(TasksTestTags.ThisWeek).assertDoesNotExist()

        // Act
        onNodeWithTag(TasksTestTags.NextWeek).performClick()

        // Assert
        onNodeWithTag(TasksTestTags.WeekRange).assertTextEquals("19 Oct – 25 Oct 2026")
        onNodeWithTag(TasksTestTags.ThisWeek).assertIsDisplayed()

        // Act
        onNodeWithTag(TasksTestTags.ThisWeek).performClick()

        // Assert
        onNodeWithTag(TasksTestTags.WeekRange).assertTextEquals("12 Oct – 18 Oct 2026")
        onNodeWithTag(TasksTestTags.PreviousWeek).performClick()
        onNodeWithTag(TasksTestTags.WeekRange).assertTextEquals("5 Oct – 11 Oct 2026")
    }

    @Test
    fun aDayCardCountsItsTasksAndSaysWhenThereAreNone() = runComposeUiTest {
        // Arrange
        val tasks = listOf(task("t1", "Bins"), task("t2", "Hoover"))
        val done = listOf(TaskDone("t1", today.isoText(), 1L, "m:a"))
        setContent { VestaTheme { TasksScreen(tasks = tasks, done = done, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TasksTestTags.daySummary(today)).assertTextEquals("1 of 2 done")
        onNodeWithTag(TasksTestTags.daySummary(today.plusDays(1))).assertTextEquals("Nothing to do")
    }

    @Test
    fun aRepeatingTaskShowsOnEachDayItComesRoundTo() = runComposeUiTest {
        // Arrange
        val tasks = listOf(task("t1", "Hoover").copy(repeat = EventRepeat.Daily))
        setContent { VestaTheme { TasksScreen(tasks = tasks, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert: from its first day on, not before
        onNodeWithTag(TasksTestTags.row("t1", today.isoText())).assertExists()
        onNodeWithTag(TasksTestTags.row("t1", today.plusDays(1).isoText())).assertExists()
        onNodeWithTag(TasksTestTags.row("t1", today.plusDays(-1).isoText())).assertDoesNotExist()
    }

    @Test
    fun theTickReportsTheOccurrenceTapped() = runComposeUiTest {
        // Arrange
        val ticked = mutableListOf<Pair<String, String>>()
        setContent {
            VestaTheme { TasksScreen(tasks = listOf(task("t1", "Bins")), today = today, onToggle = { ticked += it.task.id to it.occurrence }) }
        }

        // Act
        onNodeWithTag(TasksTestTags.tick("t1", today.isoText())).performClick()

        // Assert
        assertEquals(listOf("t1" to today.isoText()), ticked)
    }

    @Test
    fun aTaskSaysWhoItIsForAndAPrivateOneDoesNot() = runComposeUiTest {
        // Arrange
        val tasks = listOf(
            task("t1", "For Sam", assignees = setOf("m:b")),
            task("t2", "For anyone"),
            task("t3", "Mine alone", list = TaskList.Private),
        )
        setContent { VestaTheme { TasksScreen(tasks = tasks, members = members, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TasksTestTags.subtitle("t1", today.isoText()), useUnmergedTree = true).assertTextEquals("Sam")
        onNodeWithTag(TasksTestTags.subtitle("t2", today.isoText()), useUnmergedTree = true).assertTextEquals("Anyone")
        onNodeWithTag(TasksTestTags.subtitle("t3", today.isoText()), useUnmergedTree = true).assertTextEquals("")
    }

    @Test
    fun aTimedTaskShowsItsTime() = runComposeUiTest {
        // Arrange
        val tasks = listOf(task("t1", "Bins").copy(timeMinute = 7 * 60 + 30))
        setContent { VestaTheme { TasksScreen(tasks = tasks, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TasksTestTags.subtitle("t1", today.isoText()), useUnmergedTree = true).assertTextContains("07:30", substring = true)
    }

    @Test
    fun aDaysOwnPlusReportsThatDayInTheWeekViewAndTheFloatingPlusReportsNoDayInTheList() = runComposeUiTest {
        // Arrange
        val adds = mutableListOf<CalendarDate?>()
        setContent { VestaTheme { TasksScreen(tasks = emptyList(), today = today, onAdd = { adds += it }) } }

        // Act
        onNodeWithTag(TasksTestTags.dayAdd(today.plusDays(2))).assertIsDisplayed()
        onNodeWithTag(TasksTestTags.dayAdd(today.plusDays(2))).performClick()
        onNodeWithTag(TasksTestTags.AddButton).assertDoesNotExist()
        onNodeWithTag(TasksTestTags.ViewList).performClick()
        onNodeWithTag(TasksTestTags.AddButton).performClick()

        // Assert
        assertEquals(listOf(today.plusDays(2), null), adds)
    }

    @Test
    fun theListViewGroupsTasksAndLeavesOutEmptySections() = runComposeUiTest {
        // Arrange
        val tasks = listOf(
            task("old", "Overdue one", due = today.plusDays(-2)),
            task("now", "Today's"),
            task("soon", "Soon", due = today.plusDays(3)),
            task("later", "Much later", due = today.plusDays(30)),
            task("any", "Whenever", due = null),
        )
        setContent { VestaTheme { TasksScreen(tasks = tasks, today = today, initialView = TasksView.List) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        listOf("overdue", "today", "upcoming", "anytime").forEach { onNodeWithTag(TasksTestTags.section(it)).assertExists() }
        onNodeWithTag(TasksTestTags.section("done")).assertDoesNotExist()
        onNodeWithTag(TasksTestTags.row("later", today.plusDays(30).isoText())).assertDoesNotExist()
    }

    @Test
    fun anEmptyListShowsTheEmptyState() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { TasksScreen(tasks = emptyList(), today = today, initialView = TasksView.List) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TasksTestTags.Empty).assertExists()
        onNodeWithTag(EmptyStateTestTags.Title, useUnmergedTree = true).assertTextEquals("Nothing to do yet")
    }

    @Test
    fun theMineFilterNeedsToKnowWhoIAm() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { TasksScreen(tasks = emptyList(), me = null, today = today, initialView = TasksView.List) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(TasksTestTags.FilterMine).assertDoesNotExist()
    }

    @Test
    fun theSheetOffersEditAndDelete() = runComposeUiTest {
        // Arrange
        val edited = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        setContent {
            VestaTheme { TasksScreen(tasks = listOf(task("t1", "Bins")), today = today, onEdit = { edited += it.id }, onDelete = { deleted += it.id }) }
        }

        // Act
        onNodeWithTag(TasksTestTags.row("t1", today.isoText())).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTagCount(TasksTestTags.SheetEdit) > 0 }
        onNodeWithTag(TasksTestTags.SheetEdit).performClick()
        onNodeWithTag(TasksTestTags.row("t1", today.isoText())).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTagCount(TasksTestTags.SheetDelete) > 0 }
        onNodeWithTag(TasksTestTags.SheetDelete).performClick()

        // Assert
        assertEquals(listOf("t1"), edited)
        assertEquals(listOf("t1"), deleted)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTagCount(tag: String) =
        onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun theSummaryCountsOverdueAndOpenTasksDueToday() {
        // Arrange
        val tasks = listOf(
            task("old", "Overdue", due = today.plusDays(-2)),
            task("a", "Open today"),
            task("b", "Done today"),
            task("c", "Soon", due = today.plusDays(2)),
        )
        val state = TasksUiState(tasks = tasks, done = listOf(TaskDone("b", today.isoText(), 1L, "m:a")), isLoading = false)

        // Act
        val summary = tasksSummary(state, today)

        // Assert
        assertEquals(TasksSummary(overdue = 1, today = 1), summary)
    }
}
