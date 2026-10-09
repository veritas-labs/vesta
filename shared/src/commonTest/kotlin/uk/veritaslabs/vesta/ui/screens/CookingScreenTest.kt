package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class CookingScreenTest {

    private val recipe = RecipePreview

    @Test
    fun startCookingButtonOpensTheSlideshowOnTheFirstStep() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RecipeDetailScreen(recipe) } }

        // Act
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).performClick()

        // Assert
        onNodeWithTag(CookingTestTags.Screen).assertIsDisplayed()
        onNodeWithTag(CookingTestTags.Progress).assertTextEquals("Step 1 of 4")
        onNodeWithTag(CookingTestTags.StepText).assertTextEquals("Peel and boil the potatoes.")
        onNodeWithTag(CookingTestTags.Previous).assertIsNotEnabled()
    }

    @Test
    fun nextAndPreviousMoveBetweenSteps() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CookingScreen(recipe, servings = 4, onClose = {}) } }

        // Act
        onNodeWithTag(CookingTestTags.Next).performClick()

        // Assert
        onNodeWithTag(CookingTestTags.StepText).assertTextEquals("Brown the mince with the onion.")
        onNodeWithTag(CookingTestTags.Previous).assertIsEnabled().performClick()
        onNodeWithTag(CookingTestTags.StepText).assertTextEquals("Peel and boil the potatoes.")
    }

    @Test
    fun nextOnTheLastStepIsDoneAndCloses() = runComposeUiTest {
        // Arrange
        var closed = false
        setContent { VestaTheme { CookingScreen(recipe, servings = 4, onClose = { closed = true }) } }

        // Act
        repeat(recipe.steps.size - 1) { onNodeWithTag(CookingTestTags.Next).performClick() }
        onNodeWithTag(CookingTestTags.Next).assertTextEquals("Done")
        onNodeWithTag(CookingTestTags.Next).performClick()

        // Assert
        assertTrue(closed)
    }

    @Test
    fun ingredientsButtonShowsTheScaledListOnAnyStep() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CookingScreen(recipe, servings = 8, onClose = {}) } }
        onNodeWithTag(CookingTestTags.Next).performClick()

        // Act
        onNodeWithTag(CookingTestTags.Ingredients).performClick()

        // Assert
        onNodeWithTag(CookingTestTags.IngredientsDialog).assertIsDisplayed()
        onNodeWithText("1000 g Lamb mince").assertIsDisplayed()
    }

    @Test
    fun closeButtonLeavesTheSlideshowOnTheRecipe() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RecipeDetailScreen(recipe) } }
        onNodeWithTag(RecipeDetailTestTags.StartCookingButton).performClick()

        // Act
        onNodeWithTag(CookingTestTags.Close).performClick()

        // Assert
        onNodeWithTag(CookingTestTags.Screen).assertDoesNotExist()
    }
}
