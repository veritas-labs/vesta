package uk.veritaslabs.vesta.acceptance

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import uk.veritaslabs.vesta.ui.components.LocalUndo
import uk.veritaslabs.vesta.ui.components.UndoController
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.TaskDocumentSource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.EventRepeat
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskList
import uk.veritaslabs.vesta.tasks.isoText
import uk.veritaslabs.vesta.ui.flows.TasksFlow
import uk.veritaslabs.vesta.ui.screens.MemberListItem
import uk.veritaslabs.vesta.ui.screens.MemberRole
import uk.veritaslabs.vesta.ui.screens.TaskEditTestTags
import uk.veritaslabs.vesta.ui.screens.TasksTestTags
import uk.veritaslabs.vesta.ui.screens.TasksViewModel
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for tasks and chores (docs/adr/ADR-031-tasks-and-chores.md; see docs/testing/acceptance-tests.md). Each test is one
 * scenario driven through what a user can see and tap, over the real Automerge-backed Home and private task lists, the view model and
 * the tasks flow. The criteria:
 *
 *  1. The Tasks screen has a Week view and a List view of the same tasks. (The dashboard tile that opens it: HomeDashboardScreenTest.)
 *  2. I can add a task with a title only (it is for whenever), or with a due date, time, assignees and a repeat.
 *  3. A task due on a day shows on that day's card; a repeating one shows on each day it comes round to, counting "every N".
 *  4. I can tick a task done and untick it; a repeating chore ticked for one day is still due on the next occurrence.
 *  5. A task assigned to some members shows their names; one with none shows "Anyone"; any member can tick it, and it says who did.
 *  6. Overdue tasks are listed first in the List view.
 *  7. A private task is saved only in my private list, marked with a padlock, and has no assignees. (That it reaches only my own devices is
 *     DeviceDataSyncServiceTest.)
 *  8. Tasks and what was done survive an app restart. (That they reach the Home's other devices, and that two members ticking one
 *     occurrence merge to one, is DeviceDataSyncServiceTest.)
 *  9. I can open a task, change it, and see the change; and delete it. A task needs a title, and Cancel adds nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class TasksAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its writes run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // 2026-10-14 is a Wednesday, so a Monday week is 12-18 October.
    private val today = CalendarDate(2026, 10, 14)
    private val members = listOf(MemberListItem("mark", "Mark", MemberRole.Admin, isYou = true), MemberListItem("sam", "Sam", MemberRole.Member))

    private class Lists(home: ByteArray? = null, private: ByteArray? = null) {
        var homeBytes: ByteArray = home ?: Document().save()
        var privateBytes: ByteArray = private ?: Document().save()
        val home = TaskDocumentSource(Document.load(homeBytes), TaskList.Home, persist = { homeBytes = it })
        val private = TaskDocumentSource(Document.load(privateBytes), TaskList.Private, persist = { privateBytes = it })

        fun homeTasks() = runBlocking { home.snapshot.first().tasks }

        fun homeDone() = runBlocking { home.snapshot.first().done }

        fun privateTasks() = runBlocking { private.snapshot.first().tasks }
    }

    private fun ComposeUiTest.showTasks(lists: Lists) {
        setContent {
            val viewModel = remember { TasksViewModel(lists.home, lists.private, flowOf(members)) }
            val snackbars = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val undo = remember { UndoController(snackbars, scope, "Undo") }
            CompositionLocalProvider(LocalUndo provides undo) {
                VestaTheme {
                    Box {
                        TasksFlow(viewModel, homeName = "Maple Street", today = today)
                        SnackbarHost(snackbars)
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForNoTag(tag: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    /** Opens the form from the bar's plus, types [title], lets [configure] change the form and saves; returns once the form has closed. */
    private fun ComposeUiTest.addTask(title: String, configure: ComposeUiTest.() -> Unit = {}) {
        onNodeWithTag(TasksTestTags.AddButton).performClick()
        onNodeWithTag(TaskEditTestTags.TitleField).performTextInput(title)
        configure()
        onNodeWithTag(TaskEditTestTags.SaveButton).performClick()
        waitForNoTag(TaskEditTestTags.SaveButton)
    }

    private fun task(id: String, title: String, due: CalendarDate? = today, repeat: EventRepeat = EventRepeat.None, every: Int = 1) =
        Task(id = id, list = TaskList.Home, title = title, due = due, repeat = repeat, every = every, createdBy = "m:mark", createdMillis = 1L)

    @Test
    fun theTasksScreenHasAWeekViewAndAListViewOfTheSameTasks() = runComposeUiTest {
        // Arrange — Given a task due today and one for whenever
        val lists = Lists()
        runBlocking {
            lists.home.save(task("t1", "Bins"))
            lists.home.save(task("t2", "Sort the loft", due = null))
        }

        // Act — When I open Tasks and then switch to the List view
        showTasks(lists)
        waitForTag(TasksTestTags.row("t1", today.isoText()))
        onNodeWithTag(TasksTestTags.ViewList).performClick()

        // Assert — Then the week showed today's task, and the list shows it under Today and the other under Anytime
        waitForTag(TasksTestTags.section("anytime"))
        onNodeWithTag(TasksTestTags.section("today")).assertIsDisplayed()
        onNodeWithTag(TasksTestTags.row("t1", today.isoText())).assertIsDisplayed()
        onNodeWithTag(TasksTestTags.row("t2", "anytime")).assertIsDisplayed()
    }

    @Test
    fun aTaskWithOnlyATitleIsForWheneverAndListedUnderAnytime() = runComposeUiTest {
        // Arrange — Given the List view
        val lists = Lists()
        showTasks(lists)
        onNodeWithTag(TasksTestTags.ViewList).performClick()

        // Act — When I add "Sort the loft" from the plus in the bar
        addTask("Sort the loft")

        // Assert — Then it has no date, is saved on the Home list and shows under Anytime
        val saved = lists.homeTasks().single()
        assertEquals("Sort the loft", saved.title)
        assertEquals(null, saved.due)
        assertEquals("m:mark", saved.createdBy)
        waitForTag(TasksTestTags.row(saved.id, "anytime"))
        onNodeWithTag(TasksTestTags.section("anytime")).assertIsDisplayed()
        onNodeWithTag(TasksTestTags.subtitle(saved.id, "anytime"), useUnmergedTree = true).assertTextContains("Anyone", substring = true)
    }

    @Test
    fun aTaskAddedFromTheWeekViewIsDueTodayAndShownOnTodaysCard() = runComposeUiTest {
        // Arrange — Given the Week view
        val lists = Lists()
        showTasks(lists)

        // Act — When I add "Bins" from the plus in the bar
        addTask("Bins")

        // Assert — Then it is due today and listed on today's card, which now counts it
        val saved = lists.homeTasks().single()
        assertEquals(today, saved.due)
        waitForTag(TasksTestTags.row(saved.id, today.isoText()))
        onNodeWithTag(TasksTestTags.daySummary(today)).assertTextContains("0 of 1 done")
    }

    @Test
    fun aDaysOwnPlusAddsATaskForThatDay() = runComposeUiTest {
        // Arrange — Given the Week view
        val lists = Lists()
        showTasks(lists)
        val friday = CalendarDate(2026, 10, 16)

        // Act — When I tap Friday's plus and add "Pay the window cleaner"
        onNodeWithTag(TasksTestTags.dayAdd(friday)).performClick()
        onNodeWithTag(TaskEditTestTags.TitleField).performTextInput("Pay the window cleaner")
        onNodeWithTag(TaskEditTestTags.SaveButton).performClick()
        waitForNoTag(TaskEditTestTags.SaveButton)

        // Assert — Then it is due on Friday and shows on Friday's card
        assertEquals(friday, lists.homeTasks().single().due)
        waitForTag(TasksTestTags.row(lists.homeTasks().single().id, friday.isoText()))
    }

    @Test
    fun aTaskNeedsATitleAndCancellingAddsNothing() = runComposeUiTest {
        // Arrange — Given the add form open
        val lists = Lists()
        showTasks(lists)
        onNodeWithTag(TasksTestTags.AddButton).performClick()

        // Act — When I look at Save with no title, then type one and cancel
        onNodeWithTag(TaskEditTestTags.SaveButton).assertIsNotEnabled()
        onNodeWithTag(TaskEditTestTags.TitleField).performTextInput("Bins")
        onNodeWithTag(TaskEditTestTags.SaveButton).assertIsEnabled()
        onNodeWithTag(TaskEditTestTags.CancelButton).performClick()

        // Assert — Then nothing was added and I am back on the tasks
        waitForNoTag(TaskEditTestTags.TitleField)
        assertEquals(emptyList(), lists.homeTasks())
        onNodeWithTag(TasksTestTags.AddButton).assertIsDisplayed()
    }

    @Test
    fun tickingATaskMarksItDoneAndSaysWhoDidItAndTickingAgainUndoesIt() = runComposeUiTest {
        // Arrange — Given "Bins" due today
        val lists = Lists()
        runBlocking { lists.home.save(task("t1", "Bins")) }
        showTasks(lists)
        waitForTag(TasksTestTags.tick("t1", today.isoText()))

        // Act — When I tick it
        onNodeWithTag(TasksTestTags.tick("t1", today.isoText())).performClick()

        // Assert — Then it is done by me, and the day counts it
        waitUntil(timeoutMillis = 5_000) { lists.homeDone().size == 1 }
        assertEquals("m:mark", lists.homeDone().single().by)
        onNodeWithTag(TasksTestTags.subtitle("t1", today.isoText()), useUnmergedTree = true).assertTextContains("Done by Mark", substring = true)
        onNodeWithTag(TasksTestTags.daySummary(today)).assertTextContains("1 of 1 done")

        // Act — When I tick it again
        onNodeWithTag(TasksTestTags.tick("t1", today.isoText())).performClick()

        // Assert — Then it is open again
        waitUntil(timeoutMillis = 5_000) { lists.homeDone().isEmpty() }
        onNodeWithTag(TasksTestTags.daySummary(today)).assertTextContains("0 of 1 done")
    }

    @Test
    fun aChoreTickedTodayIsStillDueOnItsNextOccurrence() = runComposeUiTest {
        // Arrange — Given a weekly chore starting today
        val lists = Lists()
        runBlocking { lists.home.save(task("t1", "Hoover", repeat = EventRepeat.Weekly)) }
        showTasks(lists)
        waitForTag(TasksTestTags.tick("t1", today.isoText()))

        // Act — When I tick today's, then look at next week
        onNodeWithTag(TasksTestTags.tick("t1", today.isoText())).performClick()
        waitUntil(timeoutMillis = 5_000) { lists.homeDone().size == 1 }
        onNodeWithTag(TasksTestTags.NextWeek).performClick()

        // Assert — Then next Wednesday's is open, and only one occurrence was recorded
        val nextWednesday = today.plusDays(7)
        waitForTag(TasksTestTags.row("t1", nextWednesday.isoText()))
        onNodeWithTag(TasksTestTags.subtitle("t1", nextWednesday.isoText()), useUnmergedTree = true).assertTextContains("Anyone", substring = true)
        onNodeWithTag(TasksTestTags.daySummary(nextWednesday)).assertTextContains("0 of 1 done")
        assertEquals(listOf(today.isoText()), lists.homeDone().map { it.occurrence })
    }

    @Test
    fun aChoreEveryTwoWeeksSkipsTheWeekBetween() = runComposeUiTest {
        // Arrange — Given a chore every 2 weeks, set on the form
        val lists = Lists()
        showTasks(lists)

        // Act — When I add it and look at next week and the week after
        addTask("Change the bedding") {
            onNodeWithTag(TaskEditTestTags.Repeat).performClick()
            onNodeWithTag(TaskEditTestTags.repeatOption(EventRepeat.Weekly)).performClick()
            onNodeWithTag(TaskEditTestTags.EveryField).performTextReplacement("2")
        }
        val id = lists.homeTasks().single().id
        waitForTag(TasksTestTags.row(id, today.isoText()))
        onNodeWithTag(TasksTestTags.NextWeek).performClick()
        waitForTag(TasksTestTags.day(today.plusDays(7)))

        // Assert — Then next week has no occurrence, and the one after has
        assertEquals(0, onAllNodesWithTag(TasksTestTags.row(id, today.plusDays(7).isoText())).fetchSemanticsNodes().size)
        onNodeWithTag(TasksTestTags.NextWeek).performClick()
        waitForTag(TasksTestTags.row(id, today.plusDays(14).isoText()))
        assertEquals(2, lists.homeTasks().single().every)
    }

    @Test
    fun aRepeatNeedsAnIntervalOfAtLeastOne() = runComposeUiTest {
        // Arrange — Given the form with a title and a weekly repeat
        showTasks(Lists())
        onNodeWithTag(TasksTestTags.AddButton).performClick()
        onNodeWithTag(TaskEditTestTags.TitleField).performTextInput("Hoover")
        onNodeWithTag(TaskEditTestTags.Repeat).performClick()
        onNodeWithTag(TaskEditTestTags.repeatOption(EventRepeat.Weekly)).performClick()

        // Act — When I blank the interval
        onNodeWithTag(TaskEditTestTags.EveryField).performTextReplacement("")

        // Assert — Then I am told, and Save is off until it is a number again
        onNodeWithTag(TaskEditTestTags.EveryInvalid).assertIsDisplayed()
        onNodeWithTag(TaskEditTestTags.SaveButton).assertIsNotEnabled()
        onNodeWithTag(TaskEditTestTags.EveryField).performTextReplacement("3")
        onNodeWithTag(TaskEditTestTags.SaveButton).assertIsEnabled()
    }

    @Test
    fun aTaskAssignedToSamShowsSamsNameAndAnyoneCanTickIt() = runComposeUiTest {
        // Arrange — Given the form with a title
        val lists = Lists()
        showTasks(lists)

        // Act — When I assign it to Sam and save, then tick it myself
        addTask("Take Sam's bike in") {
            onNodeWithTag(TaskEditTestTags.AssigneesField).performClick()
            onNodeWithTag(TaskEditTestTags.assignee("m:sam")).performClick()
            onNodeWithTag(TaskEditTestTags.AssigneesDone).performClick()
        }
        val id = lists.homeTasks().single().id
        waitForTag(TasksTestTags.tick(id, today.isoText()))
        onNodeWithTag(TasksTestTags.subtitle(id, today.isoText()), useUnmergedTree = true).assertTextContains("Sam", substring = true)
        onNodeWithTag(TasksTestTags.tick(id, today.isoText())).performClick()

        // Assert — Then it is assigned to Sam, and done by me
        assertEquals(setOf("m:sam"), lists.homeTasks().single().assignees)
        waitUntil(timeoutMillis = 5_000) { lists.homeDone().size == 1 }
        onNodeWithTag(TasksTestTags.subtitle(id, today.isoText()), useUnmergedTree = true).assertTextContains("Done by Mark", substring = true)
        assertEquals("m:mark", lists.homeDone().single().by)
    }

    @Test
    fun anOverdueTaskIsListedFirstAndOnlyUntilItIsDone() = runComposeUiTest {
        // Arrange — Given a task that was due three days ago and one due today
        val lists = Lists()
        runBlocking {
            lists.home.save(task("old", "File the tax return", due = today.plusDays(-3)))
            lists.home.save(task("now", "Bins"))
        }
        showTasks(lists)
        onNodeWithTag(TasksTestTags.ViewList).performClick()
        waitForTag(TasksTestTags.section("overdue"))

        // Assert — Then Overdue is listed and holds the old one
        onNodeWithTag(TasksTestTags.row("old", today.plusDays(-3).isoText())).assertIsDisplayed()

        // Act — When I tick it
        onNodeWithTag(TasksTestTags.tick("old", today.plusDays(-3).isoText())).performClick()

        // Assert — Then Overdue is gone, and it shows under Done recently
        waitForNoTag(TasksTestTags.section("overdue"))
        waitForTag(TasksTestTags.section("done"))
    }

    @Test
    fun theMineFilterHidesTasksAssignedToSomeoneElse() = runComposeUiTest {
        // Arrange — Given a task for Sam, one for me and one for anyone
        val lists = Lists()
        runBlocking {
            lists.home.save(task("sam", "Sam's task").copy(assignees = setOf("m:sam")))
            lists.home.save(task("me", "My task").copy(assignees = setOf("m:mark")))
            lists.home.save(task("any", "Anyone's task"))
        }
        showTasks(lists)
        onNodeWithTag(TasksTestTags.ViewList).performClick()
        waitForTag(TasksTestTags.row("sam", today.isoText()))

        // Act — When I choose Mine
        onNodeWithTag(TasksTestTags.FilterMine).performClick()

        // Assert — Then Sam's is hidden and the other two stay
        waitForNoTag(TasksTestTags.row("sam", today.isoText()))
        onNodeWithTag(TasksTestTags.row("me", today.isoText())).assertIsDisplayed()
        onNodeWithTag(TasksTestTags.row("any", today.isoText())).assertIsDisplayed()
    }

    @Test
    fun aPrivateTaskIsSavedOnlyInThePrivateListAndMarkedWithAPadlock() = runComposeUiTest {
        // Arrange — Given the form with a title
        val lists = Lists()
        showTasks(lists)

        // Act — When I put it on my private tasks and save
        addTask("Order Sam's present") {
            onNodeWithTag(TaskEditTestTags.ListField).performClick()
            onNodeWithTag(TaskEditTestTags.listOption(TaskList.Private)).performClick()
            onNodeWithTag(TaskEditTestTags.PrivateNote).assertIsDisplayed()
        }

        // Assert — Then it is in the private document only, with no assignees, and its row has a padlock
        assertEquals(emptyList(), lists.homeTasks())
        val saved = lists.privateTasks().single()
        assertEquals("Order Sam's present", saved.title)
        assertEquals(TaskList.Private, saved.list)
        assertTrue(saved.assignees.isEmpty())
        waitForTag(TasksTestTags.row(saved.id, today.isoText()))
        onNodeWithTag(TasksTestTags.row(saved.id, today.isoText())).assertIsDisplayed()
    }

    @Test
    fun tasksAndWhatWasDoneSurviveARestart() = runComposeUiTest {
        // Arrange — Given a task, ticked, saved to the documents' bytes
        val first = Lists()
        runBlocking {
            first.home.save(task("t1", "Bins"))
            first.home.setDone(uk.veritaslabs.vesta.tasks.TaskDone("t1", today.isoText(), 5L, "m:mark"))
        }

        // Act — When the app is opened again over the saved bytes
        showTasks(Lists(first.homeBytes, first.privateBytes))

        // Assert — Then the task is there and still done
        waitForTag(TasksTestTags.row("t1", today.isoText()))
        onNodeWithTag(TasksTestTags.subtitle("t1", today.isoText()), useUnmergedTree = true).assertTextContains("Done by Mark", substring = true)
    }

    @Test
    fun anOpenedTaskCanBeChangedAndTheChangeShows() = runComposeUiTest {
        // Arrange — Given "Bins" due today
        val lists = Lists()
        runBlocking { lists.home.save(task("t1", "Bins")) }
        showTasks(lists)
        waitForTag(TasksTestTags.row("t1", today.isoText()))

        // Act — When I open it from the sheet, change the title and save
        onNodeWithTag(TasksTestTags.row("t1", today.isoText())).performClick()
        waitForTag(TasksTestTags.SheetEdit)
        onNodeWithTag(TasksTestTags.SheetEdit).performClick()
        onNodeWithTag(TaskEditTestTags.TitleField).performTextReplacement("Bins out")
        onNodeWithTag(TaskEditTestTags.SaveButton).performClick()
        waitForNoTag(TaskEditTestTags.SaveButton)

        // Assert — Then the same task has the new title (not a second one), and keeps who made it
        waitUntil(timeoutMillis = 5_000) { lists.homeTasks().singleOrNull()?.title == "Bins out" }
        assertEquals("t1", lists.homeTasks().single().id)
        assertEquals("m:mark", lists.homeTasks().single().createdBy)
    }

    @Test
    fun aTaskCanBeDeletedFromItsSheet() = runComposeUiTest {
        // Arrange — Given "Bins" due today
        val lists = Lists()
        runBlocking { lists.home.save(task("t1", "Bins")) }
        showTasks(lists)
        waitForTag(TasksTestTags.row("t1", today.isoText()))

        // Act — When I open its sheet and delete it
        onNodeWithTag(TasksTestTags.row("t1", today.isoText())).performClick()
        waitForTag(TasksTestTags.SheetDelete)
        onNodeWithTag(TasksTestTags.SheetDelete).performClick()

        // Assert — Then it is gone from the list and the document
        waitForNoTag(TasksTestTags.row("t1", today.isoText()))
        assertEquals(emptyList(), lists.homeTasks())
    }

    @Test
    fun aDeletedTaskCanBeUndone() = runComposeUiTest {
        // Arrange — Given "Bins" due today
        val lists = Lists()
        runBlocking { lists.home.save(task("t1", "Bins")) }
        showTasks(lists)
        waitForTag(TasksTestTags.row("t1", today.isoText()))

        // Act — When I delete it from its sheet and then press Undo
        onNodeWithTag(TasksTestTags.row("t1", today.isoText())).performClick()
        waitForTag(TasksTestTags.SheetDelete)
        onNodeWithTag(TasksTestTags.SheetDelete).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Undo").performClick()

        // Assert — Then the same task is back
        waitUntil(timeoutMillis = 5_000) { lists.homeTasks().singleOrNull()?.id == "t1" }
        assertEquals("Bins", lists.homeTasks().single().title)
    }

    @Test
    fun aRepeatingTaskSaysEditingChangesEveryOccurrence() = runComposeUiTest {
        // Arrange — Given a weekly chore
        val lists = Lists()
        runBlocking { lists.home.save(task("t1", "Hoover", repeat = EventRepeat.Weekly)) }
        showTasks(lists)
        waitForTag(TasksTestTags.row("t1", today.isoText()))

        // Act — When I open its sheet
        onNodeWithTag(TasksTestTags.row("t1", today.isoText())).performClick()

        // Assert — Then it says the change applies to every time it comes round
        waitForTag(TasksTestTags.RepeatNote)
        onNodeWithTag(TasksTestTags.RepeatNote).assertIsDisplayed()
    }
}
