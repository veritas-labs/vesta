package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.WeekStart
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class MealPlannerRequiredItemsTest {

    private val today = CalendarDate(2026, 10, 14)
    private val monday = CalendarDate(2026, 10, 12)
    private val week = weekDays(today, WeekStart.Monday)
    private val pie = Recipe(
        key = "pie",
        title = "Pie",
        ingredients = listOf(RecipeIngredient("Potato", 2.0, "kg"), RecipeIngredient("Salt"), RecipeIngredient("Butter", 50.0, "g")),
    )
    private val mash = Recipe(
        key = "mash",
        title = "Mash",
        ingredients = listOf(RecipeIngredient("potatoes", 1.0, "kg"), RecipeIngredient("Butter", 1.0, "tbsp"), RecipeIngredient("Salt")),
    )

    @Test
    fun addsTheSameIngredientAndUnitTogetherAcrossPlannedRecipes() {
        // Arrange
        val plan = MealPlan(mapOf(monday to mapOf(MealSlot.Lunch to listOf("pie"), MealSlot.Dinner to listOf("mash"))))

        // Act
        val items = requiredIngredients(week, plan, listOf(pie, mash))

        // Assert
        assertEquals(
            listOf(
                RequiredIngredient("Butter", 50.0, "g"),
                RequiredIngredient("Butter", 1.0, "tbsp"),
                RequiredIngredient("Potato", 3.0, "kg"),
                RequiredIngredient("Salt", null, ""),
            ),
            items,
        )
    }

    @Test
    fun ignoresRecipesPlannedOutsideTheWeekOrNoLongerInTheCookbook() {
        // Arrange
        val plan = MealPlan(
            mapOf(
                monday.plusDays(7) to mapOf(MealSlot.Dinner to listOf("pie")),
                monday to mapOf(MealSlot.Dinner to listOf("gone")),
            ),
        )

        // Act
        val items = requiredIngredients(week, plan, listOf(pie))

        // Assert
        assertEquals(emptyList(), items)
    }

    @Test
    fun menuOptionOpensTheRequiredItems() = runComposeUiTest {
        // Arrange
        var opened = false
        setContent { VestaTheme { MealPlannerScreen(recipes = listOf(pie), today = today, onViewRequiredItems = { opened = true }) } }

        // Act
        onNodeWithTag(MealPlannerTestTags.MoreOptions).performClick()
        onNodeWithTag(MealPlannerTestTags.ViewRequiredItems).performClick()

        // Assert
        assertEquals(true, opened)
    }
}
