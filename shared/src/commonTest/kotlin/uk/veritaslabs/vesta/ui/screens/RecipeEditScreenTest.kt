package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class RecipeEditScreenTest {

    @Test
    fun saveIsDisabledUntilThereIsATitle() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RecipeEditScreen(recipe = null) } }
        onNodeWithTag(RecipeEditTestTags.SaveButton).assertIsNotEnabled()

        // Act
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Soup")

        // Assert
        onNodeWithTag(RecipeEditTestTags.SaveButton).assertIsEnabled()
    }

    @Test
    fun savingReportsEveryFieldTheUserFilledIn() = runComposeUiTest {
        // Arrange
        var saved: Recipe? = null
        setContent { VestaTheme { RecipeEditScreen(recipe = null, onSave = { saved = it }) } }

        // Act
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Apple Crumble")
        onNodeWithTag(RecipeEditTestTags.typeChip(RecipeType.Dessert)).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.ServingsIncrease).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.PrepField).performScrollTo().performTextInput("15")
        onNodeWithTag(RecipeEditTestTags.CookField).performScrollTo().performTextInput("40")
        onNodeWithTag(RecipeEditTestTags.star(3)).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.TagsField).performScrollTo().performTextInput("Quick, quick,  Autumn ")
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        val recipe = saved!!
        assertEquals("Apple Crumble", recipe.title)
        assertEquals(RecipeType.Dessert, recipe.type)
        assertEquals(5, recipe.servings)
        assertEquals(15 to 40, recipe.prepMinutes to recipe.cookMinutes)
        assertEquals(3, recipe.rating)
        assertEquals(listOf("Quick", "Autumn"), recipe.tags)
    }

    @Test
    fun blankIngredientsAndStepsAreDroppedOnSave() = runComposeUiTest {
        // Arrange
        var saved: Recipe? = null
        setContent { VestaTheme { RecipeEditScreen(recipe = null, onSave = { saved = it }) } }
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Toast")

        // Act
        onNodeWithTag(RecipeEditTestTags.AddIngredientButton).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.AddIngredientButton).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.ingredientName(1)).performScrollTo().performTextInput("Bread")
        onNodeWithTag(RecipeEditTestTags.ingredientQuantity(1)).performScrollTo().performTextInput("2")
        onNodeWithTag(RecipeEditTestTags.ingredientUnit(1)).performScrollTo().performTextInput("slices")
        onNodeWithTag(RecipeEditTestTags.AddStepButton).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(listOf(RecipeIngredient("Bread", 2.0, "slices")), saved!!.ingredients)
        assertEquals(emptyList(), saved!!.steps)
    }

    @Test
    fun aStepCanBeMarkedToComeAfterAnEarlierOneAndRemovingThatStepUnmarksIt() = runComposeUiTest {
        // Arrange
        var saved: Recipe? = null
        setContent { VestaTheme { RecipeEditScreen(recipe = null, onSave = { saved = it }) } }
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("Pasta")
        repeat(3) { onNodeWithTag(RecipeEditTestTags.AddStepButton).performScrollTo().performClick() }
        listOf("Boil water", "Make sauce", "Combine").forEachIndexed { index, text ->
            onNodeWithTag(RecipeEditTestTags.stepText(index)).performScrollTo().performTextInput(text)
        }

        // Act
        onNodeWithTag(RecipeEditTestTags.stepAfter(2, 0)).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.stepAfter(2, 1)).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        val steps = saved!!.steps
        assertEquals(listOf(steps[0].id, steps[1].id), steps[2].after)
        assertEquals(planSteps(steps).map { stage -> stage.map { it.step.text } }, listOf(listOf("Boil water", "Make sauce"), listOf("Combine")))
    }

    @Test
    fun removingAStepDropsItFromTheStepsThatCameAfterIt() = runComposeUiTest {
        // Arrange
        var saved: Recipe? = null
        setContent {
            VestaTheme {
                RecipeEditScreen(
                    recipe = Recipe(key = "r", title = "Pasta", steps = listOf(RecipeStep("a", "Boil"), RecipeStep("b", "Serve", after = listOf("a")))),
                    onSave = { saved = it },
                )
            }
        }

        // Act
        onNodeWithTag(RecipeEditTestTags.removeStep(0)).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(listOf(RecipeStep("b", "Serve")), saved!!.steps)
    }

    @Test
    fun editingKeepsTheRecipesKeyAndFavourite() = runComposeUiTest {
        // Arrange
        var saved: Recipe? = null
        setContent { VestaTheme { RecipeEditScreen(recipe = Recipe(key = "r1", title = "Soup", favourite = true), onSave = { saved = it }) } }

        // Act
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextReplacement("Tomato Soup")
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals("r1", saved!!.key)
        assertEquals(true, saved!!.favourite)
        assertEquals("Tomato Soup", saved!!.title)
    }

    @Test
    fun anAddedPhotoOrVideoIsKeptWithItsKindAndCaption() = runComposeUiTest {
        // Arrange
        var saved: Recipe? = null
        setContent { VestaTheme { RecipeEditScreen(recipe = Recipe(title = "Soup"), onSave = { saved = it }) } }

        // Act
        onNodeWithTag(RecipeEditTestTags.MediaUriField).performScrollTo().performTextInput("https://x/soup.mp4")
        onNodeWithTag(RecipeEditTestTags.MediaCaptionField).performScrollTo().performTextInput("Stirring")
        onNodeWithTag(RecipeEditTestTags.MediaVideoChip).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.AddMediaButton).performScrollTo().performClick()
        onNodeWithTag(RecipeEditTestTags.SaveButton).performClick()

        // Assert
        assertEquals(listOf(RecipeMedia("https://x/soup.mp4", MediaKind.Video, "Stirring")), saved!!.media)
    }

    @Test
    fun cancelReportsWithoutSaving() = runComposeUiTest {
        // Arrange
        var cancelled = false
        var saved = false
        setContent { VestaTheme { RecipeEditScreen(recipe = Recipe(title = "Soup"), onSave = { saved = true }, onCancel = { cancelled = true }) } }

        // Act
        onNodeWithTag(RecipeEditTestTags.CancelButton).performClick()

        // Assert
        assertEquals(true, cancelled)
        assertEquals(false, saved)
    }
}
