package uk.veritaslabs.vesta.acceptance

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.CookbookDocumentSource
import uk.veritaslabs.vesta.automerge.MealPlanDocumentSource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.flows.CookbookFlow
import uk.veritaslabs.vesta.ui.flows.MealPlannerFlow
import uk.veritaslabs.vesta.ui.screens.CookbookViewModel
import uk.veritaslabs.vesta.ui.screens.MealPlannerCopyTestTags
import uk.veritaslabs.vesta.ui.screens.RecipeDetailTestTags
import uk.veritaslabs.vesta.ui.screens.HomeDashboardScreen
import uk.veritaslabs.vesta.ui.screens.HomeDashboardTestTags
import uk.veritaslabs.vesta.ui.screens.MealPlannerEatersTestTags
import uk.veritaslabs.vesta.ui.screens.MealPlannerPickerTestTags
import uk.veritaslabs.vesta.home.MemberListItem
import uk.veritaslabs.vesta.home.MemberRole
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
 *  4. Only recipes from the cookbook can be added, chosen on a screen of their own that I can leave without choosing.
 *  5. A meal can hold several recipes, and I can take any out.
 *  6. I can move to the next and previous week, and back to this one, and each week keeps its own plan.
 *  7. A recipe deleted from the cookbook shows as removed in the plan.
 *  8. The plan survives an app restart (and reaches paired devices: see DeviceDataSyncServiceTest).
 *  9. Each recipe planned for a meal says who is eating it: every Home member unless I change that.
 * 10. I can untick a member who is not eating a recipe, and add people who are not in the Home (children) and tick them;
 *     two recipes in the same meal can have different eaters.
 * 11. People I add are kept for the whole Home, available on every recipe, and I can rename or remove them.
 * 12. Tapping a planned recipe opens a sheet with View recipe and Copy to other days.
 * 13. View recipe opens the recipe's page, and back from it returns to where I was.
 * 14. Copy to other days plans the recipe, with its eaters, in the same meal on the days I tick, in this week and the next, and
 *     leaves days that already have it alone.
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
        MealPlanDocumentSource(bytes?.let { Document.load(it) } ?: Document(), persist, newGuestId = { "g1" })

    private val members = listOf(MemberListItem("mark", "Mark", MemberRole.Admin, isYou = true), MemberListItem("sam", "Sam", MemberRole.Member))

    private fun ComposeUiTest.showPlanner(cookbook: CookbookDocumentSource, plan: MealPlanDocumentSource) {
        setContent {
            val viewModel = remember { MealPlannerViewModel(plan, cookbook.recipes, flowOf(members)) }
            VestaTheme { MealPlannerFlow(viewModel, today = today, weekStart = WeekStart.Monday) }
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
        waitForTag(MealPlannerPickerTestTags.recipe(recipeKey))
        onNodeWithTag(MealPlannerPickerTestTags.recipe(recipeKey)).performClick()
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
        waitForTag(MealPlannerPickerTestTags.Screen)

        // Assert — Then it lists exactly those two, and has no field for a dish of my own
        val keys = runBlocking { cookbook.recipes.first().map { it.key } }
        keys.forEach { onNodeWithTag(MealPlannerPickerTestTags.recipe(it)).assertIsDisplayed() }
        onNodeWithTag(MealPlannerPickerTestTags.Search).assertIsDisplayed()
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
    fun theRecipePickerIsAScreenOfItsOwnThatBackLeavesWithoutPlanningAnything() = runComposeUiTest {
        // Arrange — Given a cookbook with Soup and the planner showing the week
        val cookbook = runBlocking { cookbook("Soup") }
        val plan = newPlan()
        showPlanner(cookbook, plan)
        waitForTag(MealPlannerTestTags.addButton(monday, MealSlot.Dinner))

        // Act — When I tap plus on Monday's dinner, then back
        onNodeWithTag(MealPlannerTestTags.addButton(monday, MealSlot.Dinner)).performClick()
        waitForTag(MealPlannerPickerTestTags.Screen)
        onNodeWithTag(MealPlannerTestTags.WeekRange).assertDoesNotExist()
        onNodeWithTag(MealPlannerPickerTestTags.BackButton).performClick()

        // Assert — Then the week is back and nothing was planned
        waitForTag(MealPlannerTestTags.WeekRange)
        assertEquals(emptyList(), runBlocking { plan.plan.first() }.recipes(monday, MealSlot.Dinner))
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
        waitForTag(MealPlannerPickerTestTags.Screen)

        // Assert — Then only Salad is offered
        onNodeWithTag(MealPlannerPickerTestTags.recipe(salad)).assertIsDisplayed()
        onNodeWithTag(MealPlannerPickerTestTags.recipe(soup)).assertDoesNotExist()
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

    private fun ComposeUiTest.openEaters(date: CalendarDate, slot: MealSlot, recipeKey: String) {
        waitForTag(MealPlannerTestTags.eaters(date, slot, recipeKey))
        onNodeWithTag(MealPlannerTestTags.eaters(date, slot, recipeKey)).performClick()
        waitForTag(MealPlannerEatersTestTags.Screen)
    }

    private fun ComposeUiTest.closeEaters() {
        onNodeWithTag(MealPlannerEatersTestTags.BackButton).performClick()
        waitForTag(MealPlannerTestTags.WeekRange)
    }

    private fun ComposeUiTest.waitForEaters(date: CalendarDate, slot: MealSlot, recipeKey: String, text: String) = waitUntil(timeoutMillis = 5_000) {
        runCatching { onNodeWithTag(MealPlannerTestTags.eaters(date, slot, recipeKey)).assertTextEquals(text) }.isSuccess
    }

    /** Soup planned for Monday's dinner, shown in the planner; returns the plan and Soup's key. */
    private fun ComposeUiTest.showPlannerWithSoupForMondayDinner(
        persist: suspend (ByteArray) -> Unit = {},
    ): Pair<MealPlanDocumentSource, String> {
        val cookbook = runBlocking { cookbook("Soup") }
        val plan = newPlan(persist = persist)
        showPlanner(cookbook, plan)
        val soup = runBlocking { cookbook.keyOf("Soup") }
        plan(monday, MealSlot.Dinner, soup)
        return plan to soup
    }

    @Test
    fun aPlannedRecipeIsEatenByEveryMemberUntilIChangeIt() = runComposeUiTest {
        // Arrange — Given a Home of Mark and Sam, with Soup planned for Monday's dinner
        val (_, soup) = showPlannerWithSoupForMondayDinner()

        // Act — When I look at the soup
        waitForTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, soup))

        // Assert — Then it says everyone is eating, and its screen has both members ticked
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, soup)).assertTextEquals("Everyone")
        openEaters(monday, MealSlot.Dinner, soup)
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:mark")).assertIsOn()
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:sam")).assertIsOn()
    }

    @Test
    fun iCanUntickAMemberWhoIsNotEatingARecipe() = runComposeUiTest {
        // Arrange — Given Soup planned for Monday's dinner
        val (plan, soup) = showPlannerWithSoupForMondayDinner()
        openEaters(monday, MealSlot.Dinner, soup)

        // Act — When I untick Sam and go back
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:sam")).performClick()
        closeEaters()

        // Assert — Then only Mark is eating the soup
        waitForEaters(monday, MealSlot.Dinner, soup, "Mark")
        assertEquals(setOf("m:mark"), runBlocking { plan.plan.first() }.chosenEaters(monday, MealSlot.Dinner, soup))
    }

    @Test
    fun twoRecipesForTheSameMealCanHaveDifferentEaters() = runComposeUiTest {
        // Arrange — Given Soup and Bread both planned for Monday's dinner
        val cookbook = runBlocking { cookbook("Soup", "Bread") }
        val plan = newPlan()
        showPlanner(cookbook, plan)
        val soup = runBlocking { cookbook.keyOf("Soup") }
        val bread = runBlocking { cookbook.keyOf("Bread") }
        plan(monday, MealSlot.Dinner, soup)
        plan(monday, MealSlot.Dinner, bread)

        // Act — When I make the bread just Sam's
        openEaters(monday, MealSlot.Dinner, bread)
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:mark")).performClick()
        closeEaters()

        // Assert — Then the bread is Sam's alone and the soup is still everyone's
        waitForEaters(monday, MealSlot.Dinner, bread, "Sam")
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, soup)).assertTextEquals("Everyone")
    }

    @Test
    fun iCanAddAChildWhoIsNotInTheHomeAndTickThemForARecipe() = runComposeUiTest {
        // Arrange — Given Soup planned for Monday's dinner
        val (_, soup) = showPlannerWithSoupForMondayDinner()
        openEaters(monday, MealSlot.Dinner, soup)

        // Act — When I add Ella and tick her
        onNodeWithTag(MealPlannerEatersTestTags.AddField).performTextInput("Ella")
        onNodeWithTag(MealPlannerEatersTestTags.AddButton).performClick()
        waitForTag(MealPlannerEatersTestTags.checkbox("p:g1"))
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("p:g1")).performClick()
        closeEaters()

        // Assert — Then the soup is eaten by Mark, Sam and Ella
        waitForEaters(monday, MealSlot.Dinner, soup, "Mark, Sam, Ella")
    }

    @Test
    fun aPersonIAddIsAvailableOnEveryOtherRecipe() = runComposeUiTest {
        // Arrange — Given Soup and Bread planned, and Ella added while editing the soup
        val cookbook = runBlocking { cookbook("Soup", "Bread") }
        val plan = newPlan()
        showPlanner(cookbook, plan)
        val soup = runBlocking { cookbook.keyOf("Soup") }
        val bread = runBlocking { cookbook.keyOf("Bread") }
        plan(monday, MealSlot.Dinner, soup)
        plan(tuesday, MealSlot.Lunch, bread)
        openEaters(monday, MealSlot.Dinner, soup)
        onNodeWithTag(MealPlannerEatersTestTags.AddField).performTextInput("Ella")
        onNodeWithTag(MealPlannerEatersTestTags.AddButton).performClick()
        closeEaters()

        // Act — When I edit the bread on Tuesday
        openEaters(tuesday, MealSlot.Lunch, bread)

        // Assert — Then Ella is there, unticked, to choose
        waitForTag(MealPlannerEatersTestTags.checkbox("p:g1"))
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("p:g1")).assertIsOff()
    }

    @Test
    fun iCanRenameAndRemoveAPersonIAdded() = runComposeUiTest {
        // Arrange — Given Ella added and ticked for the soup
        val (plan, soup) = showPlannerWithSoupForMondayDinner()
        runBlocking { plan.addGuest("Ella") }
        openEaters(monday, MealSlot.Dinner, soup)
        waitForTag(MealPlannerEatersTestTags.checkbox("p:g1"))
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("p:g1")).performClick()

        // Act — When I rename her to Ellie
        onNodeWithTag(MealPlannerEatersTestTags.rename("p:g1")).performClick()
        onNodeWithTag(MealPlannerEatersTestTags.RenameField).performTextReplacement("Ellie")
        onNodeWithTag(MealPlannerEatersTestTags.RenameSave).performClick()
        closeEaters()

        // Assert — Then the soup names Ellie; and when I remove her only the members are left, which reads as everyone
        waitForEaters(monday, MealSlot.Dinner, soup, "Mark, Sam, Ellie")
        openEaters(monday, MealSlot.Dinner, soup)
        onNodeWithTag(MealPlannerEatersTestTags.remove("p:g1")).performClick()
        closeEaters()
        waitForEaters(monday, MealSlot.Dinner, soup, "Everyone")
    }

    @Test
    fun takingARecipeOutAndPlanningItAgainStartsFromEveryone() = runComposeUiTest {
        // Arrange — Given the soup made Mark's alone
        val (_, soup) = showPlannerWithSoupForMondayDinner()
        openEaters(monday, MealSlot.Dinner, soup)
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:sam")).performClick()
        closeEaters()
        waitForEaters(monday, MealSlot.Dinner, soup, "Mark")

        // Act — When I take it out and plan it again
        onNodeWithTag(MealPlannerTestTags.removeButton(monday, MealSlot.Dinner, soup)).performClick()
        waitForNoTag(MealPlannerTestTags.entry(monday, MealSlot.Dinner, soup))
        plan(monday, MealSlot.Dinner, soup)

        // Assert — Then it is everyone's again
        onNodeWithTag(MealPlannerTestTags.eaters(monday, MealSlot.Dinner, soup)).assertTextEquals("Everyone")
    }

    @Test
    fun theChosenEatersSurviveARestart() = runComposeUiTest {
        // Arrange — Given a plan that is saved as it changes, with Sam unticked for the soup
        var saved = Document().save()
        val (_, soup) = showPlannerWithSoupForMondayDinner(persist = { saved = it })
        openEaters(monday, MealSlot.Dinner, soup)
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:sam")).performClick()
        closeEaters()
        waitForEaters(monday, MealSlot.Dinner, soup, "Mark")

        // Act — When the app is restarted from what was saved
        val reopened = newPlan(saved)

        // Assert — Then the soup is still Mark's alone
        assertEquals(setOf("m:mark"), runBlocking { reopened.plan.first() }.chosenEaters(monday, MealSlot.Dinner, soup))
    }

    @Test
    fun tappingAPlannedRecipeOffersToViewItOrCopyIt() = runComposeUiTest {
        // Arrange — Given Soup planned for Monday's dinner
        val (_, soup) = showPlannerWithSoupForMondayDinner()

        // Act — When I tap it
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Dinner, soup)).performClick()

        // Assert — Then a sheet offers View recipe and Copy to other days
        waitForTag(MealPlannerTestTags.Sheet)
        onNodeWithTag(MealPlannerTestTags.SheetView).assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.SheetCopy).assertIsDisplayed()
    }

    @Test
    fun copyToOtherDaysPlansTheRecipeWithItsEatersOnTheTickedDays() = runComposeUiTest {
        // Arrange — Given Soup planned for Monday's dinner for Mark alone
        val (plan, soup) = showPlannerWithSoupForMondayDinner()
        openEaters(monday, MealSlot.Dinner, soup)
        onNodeWithTag(MealPlannerEatersTestTags.checkbox("m:sam")).performClick()
        closeEaters()
        waitForEaters(monday, MealSlot.Dinner, soup, "Mark")
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Dinner, soup)).performClick()
        waitForTag(MealPlannerTestTags.Sheet)

        // Act — When I copy it to Wednesday and to next Monday
        onNodeWithTag(MealPlannerTestTags.SheetCopy).performClick()
        waitForTag(MealPlannerCopyTestTags.Screen)
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(CalendarDate(2026, 10, 14))).performClick()
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(nextMonday)).performClick()
        onNodeWithTag(MealPlannerCopyTestTags.Confirm).performClick()
        waitForTag(MealPlannerTestTags.WeekRange)

        // Assert — Then the soup is planned for dinner on both days, for Mark alone, and Monday still has it
        waitForTag(MealPlannerTestTags.entry(CalendarDate(2026, 10, 14), MealSlot.Dinner, soup))
        waitForEaters(CalendarDate(2026, 10, 14), MealSlot.Dinner, soup, "Mark")
        val saved = runBlocking { plan.plan.first() }
        assertEquals(listOf(soup), saved.recipes(nextMonday, MealSlot.Dinner))
        assertEquals(setOf("m:mark"), saved.chosenEaters(nextMonday, MealSlot.Dinner, soup))
        assertEquals(listOf(soup), saved.recipes(monday, MealSlot.Dinner))
        assertEquals(emptyList(), saved.recipes(CalendarDate(2026, 10, 14), MealSlot.Lunch))
    }

    @Test
    fun aDayThatAlreadyHasTheRecipeIsNotOfferedForCopying() = runComposeUiTest {
        // Arrange — Given Soup planned for dinner on both Monday and Tuesday
        val (_, soup) = showPlannerWithSoupForMondayDinner()
        plan(tuesday, MealSlot.Dinner, soup)
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Dinner, soup)).performClick()
        waitForTag(MealPlannerTestTags.Sheet)

        // Act — When I open Copy to other days for Monday's
        onNodeWithTag(MealPlannerTestTags.SheetCopy).performClick()
        waitForTag(MealPlannerCopyTestTags.Screen)

        // Assert — Then Tuesday can't be ticked and Wednesday can
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(tuesday)).assertIsNotEnabled()
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(CalendarDate(2026, 10, 14))).assertIsEnabled()
    }

    @Test
    fun backFromCopyToOtherDaysCopiesNothing() = runComposeUiTest {
        // Arrange — Given Soup planned for Monday's dinner and the copy screen open
        val (plan, soup) = showPlannerWithSoupForMondayDinner()
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Dinner, soup)).performClick()
        waitForTag(MealPlannerTestTags.Sheet)
        onNodeWithTag(MealPlannerTestTags.SheetCopy).performClick()
        waitForTag(MealPlannerCopyTestTags.Screen)
        onNodeWithTag(MealPlannerCopyTestTags.checkbox(tuesday)).performClick()

        // Act — When I go back
        onNodeWithTag(MealPlannerCopyTestTags.BackButton).performClick()
        waitForTag(MealPlannerTestTags.WeekRange)

        // Assert — Then Tuesday was not planned
        assertEquals(emptyList(), runBlocking { plan.plan.first() }.recipes(tuesday, MealSlot.Dinner))
    }

    @Test
    fun viewRecipeLeavesThePlannerForTheRecipesPage() = runComposeUiTest {
        // Arrange — Given Soup planned for Monday's dinner, in the planner wired to the cookbook as the app does
        val cookbook = runBlocking { cookbook("Soup") }
        val soup = runBlocking { cookbook.keyOf("Soup") }
        var viewing: String? = null
        setContent {
            val viewModel = remember { MealPlannerViewModel(newPlan(), cookbook.recipes, flowOf(members)) }
            VestaTheme { MealPlannerFlow(viewModel, today = today, weekStart = WeekStart.Monday, onViewRecipe = { viewing = it }) }
        }
        waitForTag(MealPlannerTestTags.addButton(monday, MealSlot.Dinner))
        plan(monday, MealSlot.Dinner, soup)

        // Act — When I tap the recipe and choose View recipe
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Dinner, soup)).performClick()
        waitForTag(MealPlannerTestTags.Sheet)
        onNodeWithTag(MealPlannerTestTags.SheetView).performClick()

        // Assert — Then the planner asks to open that recipe
        assertEquals(soup, viewing)
    }

    @Test
    fun theCookbookCanOpenStraightOnARecipeAndBackLeavesIt() = runComposeUiTest {
        // Arrange — Given a cookbook with Soup
        val cookbook = runBlocking { cookbook("Soup") }
        val soup = runBlocking { cookbook.keyOf("Soup") }
        var left = false
        setContent {
            val viewModel = remember { CookbookViewModel(cookbook, flowOf(emptyList())) }
            VestaTheme { CookbookFlow(viewModel, onBack = { left = true }, initialRecipeKey = soup) }
        }

        // Act — When it opens
        waitForTag(RecipeDetailTestTags.BackButton)

        // Assert — Then I am on Soup's page, and back leaves the cookbook rather than showing its list
        onNodeWithTag(RecipeDetailTestTags.Title).assertTextEquals("Soup")
        onNodeWithTag(RecipeDetailTestTags.BackButton).performClick()
        assertEquals(true, left)
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
