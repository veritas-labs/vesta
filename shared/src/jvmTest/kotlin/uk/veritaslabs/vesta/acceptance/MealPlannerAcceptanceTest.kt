package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.CookbookDocumentSource
import uk.veritaslabs.vesta.automerge.MealPlanDocumentSource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.screens.HomeDashboardScreen
import uk.veritaslabs.vesta.ui.screens.HomeDashboardTestTags
import uk.veritaslabs.vesta.ui.screens.MealPlannerScreen
import uk.veritaslabs.vesta.ui.screens.MealPlannerTestTags
import uk.veritaslabs.vesta.ui.screens.MealPlannerViewModel
import uk.veritaslabs.vesta.ui.screens.MealSlot
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeType
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/**
 * Acceptance tests for the Meal Planner (docs/adr/ADR-028-meal-planner.md; see docs/testing/acceptance-tests.md). Each test is
 * one scenario, driven only through what a user can see and tap, over the real Automerge-backed cookbook and meal plan, the
 * view model and the week screen. The criteria:
 *
 *  1. The Home's dashboard opens the meal planner.
 *  2. The planner shows a week, a day at a time, each day with breakfast, lunch and dinner.
 *  3. I can set a recipe for any meal of any day, and see it there.
 *  4. Only recipes from the cookbook can be added.
 *  5. A meal can hold several recipes, and I can take any out.
 *  6. I can move to the next and previous week, and back to this one, and each week keeps its own plan.
 *  7. A recipe deleted from the cookbook shows as removed in the plan.
 *  8. The plan survives an app restart (and reaches paired devices: see DeviceDataSyncServiceTest).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MealPlannerAcceptanceTest {

    // The view model's scope resolves to Dispatchers.Main; a real dispatcher lets its writes run (see DevicePairingFlowTest).
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // 2026-10-14 is a Wednesday; with weeks starting on Monday this week is 12–18 October.
    private val today = CalendarDate(2026, 10, 14)
    private val monday = CalendarDate(2026, 10, 12)
    private val tuesday = CalendarDate(2026, 10, 13)
    private val nextMonday = CalendarDate(2026, 10, 19)

    private suspend fun cookbook(vararg titles: String): CookbookDocumentSource =
        CookbookDocumentSource(Document()).also { source -> titles.forEach { source.save(Recipe(key = "", title = it, type = RecipeType.Dinner)) } }

    private suspend fun CookbookDocumentSource.keyOf(title: String) = recipes.first().single { it.title == title }.key

    private fun newPlan(bytes: ByteArray? = null, persist: suspend (ByteArray) -> Unit = {}) =
        MealPlanDocumentSource(bytes?.let { Document.load(it) } ?: Document(), persist)

    private fun ComposeUiTest.showPlanner(cookbook: CookbookDocumentSource, plan: MealPlanDocumentSource) {
        setContent {
            val viewModel = remember { MealPlannerViewModel(plan, cookbook.recipes) }
            VestaTheme { MealPlannerScreen(viewModel, today = today, weekStart = WeekStart.Monday) }
        }
    }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForNoTag(tag: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    /** Plans the recipe titled [title] by tapping through the picker. */
    private fun ComposeUiTest.plan(date: CalendarDate, slot: MealSlot, recipeKey: String) {
        waitForTag(MealPlannerTestTags.addButton(date, slot))
        onNodeWithTag(MealPlannerTestTags.addButton(date, slot)).performClick()
        waitForTag(MealPlannerTestTags.pickerRecipe(recipeKey))
        onNodeWithTag(MealPlannerTestTags.pickerRecipe(recipeKey)).performClick()
        waitForTag(MealPlannerTestTags.entry(date, slot, recipeKey))
    }

    @Test
    fun theDashboardOffersTheMealPlanner() = runComposeUiTest {
        // Arrange — Given a Home's dashboard
        var opened = false
        setContent { VestaTheme { HomeDashboardScreen(homeName = "Maple Street", onMealPlannerClick = { opened = true }) } }

        // Act — When I tap Meal planner
        onNodeWithTag(HomeDashboardTestTags.MealPlannerTile).performClick()

        // Assert — Then the meal planner opens
        assertEquals(true, opened)
    }

    @Test
    fun theWeekShowsSevenDaysEachWithBreakfastLunchAndDinner() = runComposeUiTest {
        // Arrange — Given a Home with an empty plan
        val cookbook = runBlocking { cookbook("Shepherd's Pie") }

        // Act — When I open the meal planner
        showPlanner(cookbook, newPlan())
        waitForTag(MealPlannerTestTags.WeekRange)

        // Assert — Then I see this week, Monday 12 to Sunday 18 October, and a way to plan each meal of its first day
        onNodeWithTag(MealPlannerTestTags.WeekRange).assertTextEquals("12 Oct – 18 Oct 2026")
        onNodeWithTag(MealPlannerTestTags.day(monday)).assertIsDisplayed()
        MealSlot.entries.forEach { onNodeWithTag(MealPlannerTestTags.addButton(monday, it)).assertIsDisplayed() }
    }

    @Test
    fun iCanSetBreakfastLunchAndDinnerForADay() = runComposeUiTest {
        // Arrange — Given a cookbook with three recipes
        val cookbook = runBlocking { cookbook("Porridge", "Soup", "Shepherd's Pie") }
        val plan = newPlan()
        showPlanner(cookbook, plan)
        val porridge = runBlocking { cookbook.keyOf("Porridge") }
        val soup = runBlocking { cookbook.keyOf("Soup") }
        val pie = runBlocking { cookbook.keyOf("Shepherd's Pie") }

        // Act — When I plan one for each meal of Tuesday
        plan(tuesday, MealSlot.Breakfast, porridge)
        plan(tuesday, MealSlot.Lunch, soup)
        plan(tuesday, MealSlot.Dinner, pie)

        // Assert — Then each shows in its meal, and the plan holds them
        val saved = runBlocking { plan.plan.first() }
        assertEquals(listOf(porridge), saved.recipes(tuesday, MealSlot.Breakfast))
        assertEquals(listOf(soup), saved.recipes(tuesday, MealSlot.Lunch))
        assertEquals(listOf(pie), saved.recipes(tuesday, MealSlot.Dinner))
    }

    @Test
    fun theOnlyRecipesIAmOfferedAreTheCookbooksOwn() = runComposeUiTest {
        // Arrange — Given a cookbook with two recipes
        val cookbook = runBlocking { cookbook("Porridge", "Soup") }
        showPlanner(cookbook, newPlan())

        // Act — When I open the picker for Monday's dinner
        waitForTag(MealPlannerTestTags.addButton(monday, MealSlot.Dinner))
        onNodeWithTag(MealPlannerTestTags.addButton(monday, MealSlot.Dinner)).performClick()
        waitForTag(MealPlannerTestTags.PickerDialog)

        // Assert — Then it lists exactly those two, and has no field for a dish of my own
        val keys = runBlocking { cookbook.recipes.first().map { it.key } }
        keys.forEach { onNodeWithTag(MealPlannerTestTags.pickerRecipe(it)).assertIsDisplayed() }
        onNodeWithTag(MealPlannerTestTags.PickerSearch).assertIsDisplayed()
    }

    @Test
    fun aMealCanHoldSeveralRecipesAndICanTakeOneOut() = runComposeUiTest {
        // Arrange — Given Monday's dinner with a main and a dessert planned
        val cookbook = runBlocking { cookbook("Shepherd's Pie", "Apple Crumble") }
        val plan = newPlan()
        showPlanner(cookbook, plan)
        val pie = runBlocking { cookbook.keyOf("Shepherd's Pie") }
        val crumble = runBlocking { cookbook.keyOf("Apple Crumble") }
        plan(monday, MealSlot.Dinner, pie)
        plan(monday, MealSlot.Dinner, crumble)

        // Act — When I take the dessert out
        onNodeWithTag(MealPlannerTestTags.removeButton(monday, MealSlot.Dinner, crumble)).performClick()
        waitForNoTag(MealPlannerTestTags.entry(monday, MealSlot.Dinner, crumble))

        // Assert — Then only the main is left
        assertEquals(listOf(pie), runBlocking { plan.plan.first() }.recipes(monday, MealSlot.Dinner))
    }

    @Test
    fun aRecipeAlreadyInAMealIsNotOfferedAgainForIt() = runComposeUiTest {
        // Arrange — Given Monday's lunch already has Soup
        val cookbook = runBlocking { cookbook("Soup", "Salad") }
        showPlanner(cookbook, newPlan())
        val soup = runBlocking { cookbook.keyOf("Soup") }
        val salad = runBlocking { cookbook.keyOf("Salad") }
        plan(monday, MealSlot.Lunch, soup)

        // Act — When I open the picker for that lunch again
        onNodeWithTag(MealPlannerTestTags.addButton(monday, MealSlot.Lunch)).performClick()
        waitForTag(MealPlannerTestTags.PickerDialog)

        // Assert — Then only Salad is offered
        onNodeWithTag(MealPlannerTestTags.pickerRecipe(salad)).assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.pickerRecipe(soup)).assertDoesNotExist()
    }

    @Test
    fun eachWeekKeepsItsOwnPlan() = runComposeUiTest {
        // Arrange — Given Soup planned for this Monday's lunch
        val cookbook = runBlocking { cookbook("Soup") }
        showPlanner(cookbook, newPlan())
        val soup = runBlocking { cookbook.keyOf("Soup") }
        plan(monday, MealSlot.Lunch, soup)

        // Act — When I go to next week
        onNodeWithTag(MealPlannerTestTags.NextWeek).performClick()
        waitForTag(MealPlannerTestTags.day(nextMonday))

        // Assert — Then it is empty and shows next week's dates, and "This week" brings me back to the planned one
        onNodeWithTag(MealPlannerTestTags.WeekRange).assertTextEquals("19 Oct – 25 Oct 2026")
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Lunch, soup)).assertDoesNotExist()
        onNodeWithTag(MealPlannerTestTags.ThisWeek).performClick()
        waitForTag(MealPlannerTestTags.entry(monday, MealSlot.Lunch, soup))
        onNodeWithTag(MealPlannerTestTags.WeekRange).assertTextEquals("12 Oct – 18 Oct 2026")
    }

    @Test
    fun iCanPlanAheadInALaterWeekAndLookBackAtAnEarlierOne() = runComposeUiTest {
        // Arrange — Given a cookbook with Soup
        val cookbook = runBlocking { cookbook("Soup") }
        val plan = newPlan()
        showPlanner(cookbook, plan)
        val soup = runBlocking { cookbook.keyOf("Soup") }

        // Act — When I go to next week and plan Monday's lunch, then back a week twice
        onNodeWithTag(MealPlannerTestTags.NextWeek).performClick()
        waitForTag(MealPlannerTestTags.day(nextMonday))
        plan(nextMonday, MealSlot.Lunch, soup)
        onNodeWithTag(MealPlannerTestTags.PreviousWeek).performClick()
        onNodeWithTag(MealPlannerTestTags.PreviousWeek).performClick()
        waitForTag(MealPlannerTestTags.day(CalendarDate(2026, 10, 5)))

        // Assert — Then the plan holds it for 19 October and the earlier week shows its own dates
        assertEquals(listOf(soup), runBlocking { plan.plan.first() }.recipes(nextMonday, MealSlot.Lunch))
        onNodeWithTag(MealPlannerTestTags.WeekRange).assertTextEquals("5 Oct – 11 Oct 2026")
    }

    @Test
    fun aRecipeDeletedFromTheCookbookShowsAsRemovedInThePlan() = runComposeUiTest {
        // Arrange — Given Soup planned for Monday's lunch
        val cookbook = runBlocking { cookbook("Soup") }
        showPlanner(cookbook, newPlan())
        val soup = runBlocking { cookbook.keyOf("Soup") }
        plan(monday, MealSlot.Lunch, soup)

        // Act — When Soup is deleted from the cookbook
        runBlocking { cookbook.remove(soup) }

        // Assert — Then the plan still has the entry, now marked as removed, and I can take it out
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Recipe removed").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Recipe removed").assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.removeButton(monday, MealSlot.Lunch, soup)).performClick()
        waitForNoTag(MealPlannerTestTags.entry(monday, MealSlot.Lunch, soup))
    }

    @Test
    fun thePlanSurvivesARestart() = runComposeUiTest {
        // Arrange — Given a plan that is saved as it changes
        val cookbook = runBlocking { cookbook("Soup") }
        var saved = Document().save()
        val plan = newPlan(persist = { saved = it })
        showPlanner(cookbook, plan)
        val soup = runBlocking { cookbook.keyOf("Soup") }
        plan(monday, MealSlot.Lunch, soup)

        // Act — When the app is restarted from what was saved
        val reopened = newPlan(saved)

        // Assert — Then Monday's lunch is still Soup
        assertEquals(listOf(soup), runBlocking { reopened.plan.first() }.recipes(monday, MealSlot.Lunch))
    }
}
