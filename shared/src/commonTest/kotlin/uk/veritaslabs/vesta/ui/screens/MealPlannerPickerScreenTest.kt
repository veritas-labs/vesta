package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.blobs.MediaKind
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.kits.blobs.BlobReference
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class MealPlannerPickerScreenTest {

    private val monday = CalendarDate(2026, 10, 12)
    private val photo = BlobReference(hash = "a".repeat(64), size = 1_000, mime = "image/webp", kind = MediaKind.Photo, w = 4, h = 3, blur = "")
    private val pie = Recipe(
        key = "pie", title = "Shepherd's Pie", type = RecipeType.Dinner, prepMinutes = 20, cookMinutes = 50,
        media = listOf(RecipeMedia("", MediaKind.Photo, blob = photo)),
    )
    private val porridge = Recipe(key = "porridge", title = "Porridge", type = RecipeType.Breakfast)
    private val soup = Recipe(key = "soup", title = "Soup", type = RecipeType.Lunch, tags = listOf("Quick"))

    @Test
    fun saysWhichMealOfWhichDayIsBeingFilled() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerPickerScreen(listOf(pie), monday, MealSlot.Dinner) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("Dinner · Mon 12").assertIsDisplayed()
    }

    @Test
    fun listsTheRecipesWithTheirTypeAndTime() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerPickerScreen(listOf(pie), monday, MealSlot.Dinner) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithText("Shepherd's Pie").assertIsDisplayed()
        onNodeWithText("Dinner · 1 h 10 min").assertIsDisplayed()
    }

    @Test
    fun aRecipeWithAPhotoShowsItInItsRowAndOneWithoutDoesNot() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerPickerScreen(listOf(pie, porridge), monday, MealSlot.Dinner) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerPickerTestTags.thumbnail("pie"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(MealPlannerPickerTestTags.thumbnail("porridge"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun pickingARecipeReportsIt() = runComposeUiTest {
        // Arrange
        var picked: Recipe? = null
        setContent { VestaTheme { MealPlannerPickerScreen(listOf(pie, porridge), monday, MealSlot.Breakfast, onPick = { picked = it }) } }

        // Act
        onNodeWithTag(MealPlannerPickerTestTags.recipe("porridge")).performClick()

        // Assert
        assertEquals("porridge", picked?.key)
    }

    @Test
    fun offersOnlyRecipesNotAlreadyInTheMeal() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerPickerScreen(listOf(pie, soup), monday, MealSlot.Dinner, planned = listOf("pie")) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerPickerTestTags.recipe("soup")).assertIsDisplayed()
        onNodeWithTag(MealPlannerPickerTestTags.recipe("pie")).assertDoesNotExist()
    }

    @Test
    fun searchNarrowsTheList() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerPickerScreen(listOf(pie, porridge, soup), monday, MealSlot.Dinner) } }

        // Act
        onNodeWithTag(MealPlannerPickerTestTags.Search).performTextInput("quick")

        // Assert
        onNodeWithTag(MealPlannerPickerTestTags.recipe("soup")).assertIsDisplayed()
        onNodeWithTag(MealPlannerPickerTestTags.recipe("pie")).assertDoesNotExist()
    }

    @Test
    fun anEmptyCookbookExplainsThereIsNothingToPick() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerPickerScreen(emptyList(), monday, MealSlot.Dinner) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(MealPlannerPickerTestTags.Empty).assertIsDisplayed()
        onNodeWithTag(MealPlannerPickerTestTags.Search).assertDoesNotExist()
    }

    @Test
    fun backReportsATapWithoutPicking() = runComposeUiTest {
        // Arrange
        var backed = false
        var picked = false
        setContent {
            VestaTheme { MealPlannerPickerScreen(listOf(pie), monday, MealSlot.Dinner, onPick = { picked = true }, onBack = { backed = true }) }
        }

        // Act
        onNodeWithTag(MealPlannerPickerTestTags.BackButton).performClick()

        // Assert
        assertEquals(true, backed)
        assertEquals(false, picked)
    }
}
