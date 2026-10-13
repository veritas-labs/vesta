package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class MealPlannerScreenTest {

    // 2026-10-14 is a Wednesday, so a Monday week is 12–18 October.
    private val today = CalendarDate(2026, 10, 14)
    private val monday = CalendarDate(2026, 10, 12)
    private val pie = Recipe(key = "pie", title = "Shepherd's Pie", type = RecipeType.Dinner)
    private val porridge = Recipe(key = "porridge", title = "Porridge", type = RecipeType.Breakfast)
    private val soup = Recipe(key = "soup", title = "Soup", type = RecipeType.Lunch, tags = listOf("Quick"))

    private fun planOf(vararg entries: Triple<CalendarDate, MealSlot, List<String>>) =
        MealPlan(entries.groupBy { it.first }.mapValues { (_, day) -> day.associate { it.second to it.third } })

    @Test
    fun showsTheWeekContainingTodayStartingOnTheChosenDay() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerScreen(recipes = listOf(pie), today = today, weekStart = WeekStart.Monday) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.WeekRange).assertTextEquals("12 Oct – 18 Oct 2026")
        onNodeWithTag(MealPlannerTestTags.day(CalendarDate(2026, 10, 12))).assertExists()
        onNodeWithTag(MealPlannerTestTags.day(CalendarDate(2026, 10, 18))).assertExists()
        onNodeWithTag(MealPlannerTestTags.day(CalendarDate(2026, 10, 19))).assertDoesNotExist()
    }

    @Test
    fun aWeekOffsetShowsAnotherWeekAndOffersAWayBack() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerScreen(recipes = emptyList(), today = today, weekOffset = 1) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.WeekRange).assertTextEquals("19 Oct – 25 Oct 2026")
        onNodeWithTag(MealPlannerTestTags.ThisWeek).assertIsDisplayed()
    }

    @Test
    fun thisWeekButtonIsHiddenOnTheCurrentWeek() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerScreen(recipes = emptyList(), today = today, weekOffset = 0) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.ThisWeek).assertDoesNotExist()
    }

    @Test
    fun theWeekControlsReportTheirTaps() = runComposeUiTest {
        // Arrange
        val taps = mutableListOf<String>()
        setContent {
            VestaTheme {
                MealPlannerScreen(
                    recipes = emptyList(), today = today, weekOffset = 2,
                    onPreviousWeek = { taps += "previous" }, onNextWeek = { taps += "next" }, onThisWeek = { taps += "this" },
                )
            }
        }

        // Act
        onNodeWithTag(MealPlannerTestTags.PreviousWeek).performClick()
        onNodeWithTag(MealPlannerTestTags.NextWeek).performClick()
        onNodeWithTag(MealPlannerTestTags.ThisWeek).performClick()

        // Assert
        assertEquals(listOf("previous", "next", "this"), taps)
    }

    @Test
    fun plannedRecipesShowInTheirDayAndSlotByTitle() = runComposeUiTest {
        // Arrange
        val plan = planOf(Triple(monday, MealSlot.Dinner, listOf("pie")))
        setContent { VestaTheme { MealPlannerScreen(recipes = listOf(pie), plan = plan, today = today) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Dinner, "pie"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Shepherd's Pie").assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.entry(monday, MealSlot.Lunch, "pie")).assertDoesNotExist()
    }

    @Test
    fun aRecipeNoLongerInTheCookbookShowsAsRemovedAndCanStillBeTakenOut() = runComposeUiTest {
        // Arrange
        var removed: Triple<CalendarDate, MealSlot, String>? = null
        val plan = planOf(Triple(monday, MealSlot.Lunch, listOf("gone")))
        setContent {
            VestaTheme {
                MealPlannerScreen(recipes = listOf(pie), plan = plan, today = today, onRemove = { d, s, k -> removed = Triple(d, s, k) })
            }
        }

        // Act
        onNodeWithText("Recipe removed").assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.removeButton(monday, MealSlot.Lunch, "gone")).performClick()

        // Assert
        assertEquals(Triple(monday, MealSlot.Lunch, "gone"), removed)
    }

    @Test
    fun tappingPlusOpensThePickerListingTheCookbookAndPickingReportsTheRecipe() = runComposeUiTest {
        // Arrange
        var added: Triple<CalendarDate, MealSlot, String>? = null
        setContent {
            VestaTheme {
                MealPlannerScreen(recipes = listOf(pie, porridge), today = today, onAdd = { d, s, k -> added = Triple(d, s, k) })
            }
        }

        // Act
        onNodeWithTag(MealPlannerTestTags.addButton(monday, MealSlot.Breakfast)).performClick()
        onNodeWithTag(MealPlannerTestTags.PickerDialog).assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.pickerRecipe("porridge")).performClick()

        // Assert
        assertEquals(Triple(monday, MealSlot.Breakfast, "porridge"), added)
        onNodeWithTag(MealPlannerTestTags.PickerDialog).assertDoesNotExist()
    }

    @Test
    fun thePickerOffersOnlyRecipesNotAlreadyInTheSlot() = runComposeUiTest {
        // Arrange
        val plan = planOf(Triple(monday, MealSlot.Dinner, listOf("pie")))
        setContent { VestaTheme { MealPlannerScreen(recipes = listOf(pie, soup), plan = plan, today = today) } }

        // Act
        onNodeWithTag(MealPlannerTestTags.addButton(monday, MealSlot.Dinner)).performClick()

        // Assert
        onNodeWithTag(MealPlannerTestTags.pickerRecipe("soup")).assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.pickerRecipe("pie")).assertDoesNotExist()
    }

    @Test
    fun thePickerSearchNarrowsTheList() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerScreen(recipes = listOf(pie, porridge, soup), today = today) } }
        onNodeWithTag(MealPlannerTestTags.addButton(monday, MealSlot.Dinner)).performClick()

        // Act
        onNodeWithTag(MealPlannerTestTags.PickerSearch).performTextInput("quick")

        // Assert
        onNodeWithTag(MealPlannerTestTags.pickerRecipe("soup")).assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.pickerRecipe("pie")).assertDoesNotExist()
    }

    @Test
    fun anEmptyCookbookExplainsThereIsNothingToPick() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerScreen(recipes = emptyList(), today = today) } }

        // Act
        onNodeWithTag(MealPlannerTestTags.addButton(monday, MealSlot.Dinner)).performClick()

        // Assert
        onNodeWithTag(MealPlannerTestTags.PickerEmpty).assertIsDisplayed()
        onNodeWithTag(MealPlannerTestTags.PickerSearch).assertDoesNotExist()
    }

    @Test
    fun cancellingThePickerAddsNothing() = runComposeUiTest {
        // Arrange
        var added = false
        setContent { VestaTheme { MealPlannerScreen(recipes = listOf(pie), today = today, onAdd = { _, _, _ -> added = true }) } }
        onNodeWithTag(MealPlannerTestTags.addButton(monday, MealSlot.Dinner)).performClick()

        // Act
        onNodeWithTag(MealPlannerTestTags.PickerCancel).performClick()

        // Assert
        assertEquals(false, added)
        onNodeWithTag(MealPlannerTestTags.PickerDialog).assertDoesNotExist()
    }

    @Test
    fun backReportsATap() = runComposeUiTest {
        // Arrange
        var backed = false
        setContent { VestaTheme { MealPlannerScreen(recipes = emptyList(), today = today, onBack = { backed = true }) } }

        // Act
        onNodeWithTag(MealPlannerTestTags.BackButton).performClick()

        // Assert
        assertEquals(true, backed)
    }
}
