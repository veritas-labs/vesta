package uk.veritaslabs.vesta.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.blobs.FakeBlobs
import uk.veritaslabs.vesta.ui.components.LocalBlobs
import uk.veritaslabs.vesta.recipes.LocalRecipeImporter
import uk.veritaslabs.vesta.recipes.RecipeImportFailure
import uk.veritaslabs.vesta.recipes.RecipeImportResult
import uk.veritaslabs.vesta.recipes.RecipeImporter
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class RecipeEditImportTest {

    private val imported = Recipe(
        title = "Lentil dal",
        servings = 6,
        cookMinutes = 40,
        ingredients = listOf(RecipeIngredient("red lentils", 1.0, "cup")),
        steps = listOf(RecipeStep("s0", "Simmer."), RecipeStep("s1", "Serve.")),
        tags = listOf("vegan"),
        source = "https://example.com/dal",
    )

    @Test
    fun withoutAnImporterThereIsNoOverflowMenu() = runComposeUiTest {
        // Arrange / Act
        setContent { VestaTheme { RecipeEditScreen(recipe = null) } }

        // Assert
        onNodeWithTag(RecipeEditTestTags.OverflowButton).assertDoesNotExist()
    }

    @Test
    fun editingARecipeHasNoImportOption() = runComposeUiTest {
        // Arrange / Act
        setContent { VestaTheme { CompositionLocalProvider(LocalRecipeImporter provides RecipeImporter { error("unused") }) { RecipeEditScreen(Recipe(key = "r1", title = "Pie")) } } }

        // Assert
        onNodeWithTag(RecipeEditTestTags.OverflowButton).assertDoesNotExist()
    }

    @Test
    fun importFromUrlFillsTheFormAndSavesTheImportedRecipe() = runComposeUiTest {
        // Arrange
        val asked = mutableListOf<String>()
        val importer = RecipeImporter { url ->
            asked += url
            RecipeImportResult.Imported(imported, photoUrl = "https://example.com/dal.jpg")
        }
        val blobs = FakeBlobs()
        var saved: Recipe? = null
        setContent {
            VestaTheme {
                CompositionLocalProvider(LocalRecipeImporter provides importer, LocalBlobs provides blobs) { RecipeEditScreen(recipe = null, onSave = { saved = it }) }
            }
        }

        // Act
        onNodeWithTag(RecipeEditTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportMenuItem).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportUrlField).performTextInput("https://example.com/dal")
        onNodeWithTag(RecipeEditTestTags.ImportConfirmButton).performClick()
        waitUntil { runCatching { onNodeWithTag(RecipeEditTestTags.RemovePhotoButton).assertExists() }.isSuccess }
        onNodeWithTag(RecipeEditTestTags.TitleField).assertTextContains("Lentil dal")
        onNodeWithTag(RecipeEditTestTags.SaveButton).performScrollTo().performClick()

        // Assert
        assertEquals(listOf("https://example.com/dal"), asked)
        assertEquals(listOf("https://example.com/dal.jpg"), blobs.addedUrls)
        val recipe = saved!!
        assertEquals("Lentil dal", recipe.title)
        assertEquals(6, recipe.servings)
        assertEquals(40, recipe.cookMinutes)
        assertEquals(listOf("red lentils"), recipe.ingredients.map { it.name })
        assertEquals(listOf("Simmer.", "Serve."), recipe.steps.map { it.text })
        assertEquals(listOf("vegan"), recipe.tags)
        assertEquals("https://example.com/dal", recipe.source)
        assertEquals(1, recipe.media.size)
    }

    @Test
    fun aPageWithNoRecipeShowsAnErrorAndLeavesTheFormAlone() = runComposeUiTest {
        // Arrange
        val importer = RecipeImporter { RecipeImportResult.Failed(RecipeImportFailure.NoRecipe) }
        setContent { VestaTheme { CompositionLocalProvider(LocalRecipeImporter provides importer) { RecipeEditScreen(recipe = null) } } }
        onNodeWithTag(RecipeEditTestTags.TitleField).performTextInput("My own")

        // Act
        onNodeWithTag(RecipeEditTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportMenuItem).performClick()
        onNodeWithTag(RecipeEditTestTags.ImportUrlField).performTextInput("https://example.com/none")
        onNodeWithTag(RecipeEditTestTags.ImportConfirmButton).performClick()

        // Assert
        waitUntil { runCatching { onNodeWithTag(RecipeEditTestTags.ImportError).assertIsDisplayed() }.isSuccess }
        onNodeWithTag(RecipeEditTestTags.ImportCancelButton).performClick()
        onNodeWithTag(RecipeEditTestTags.TitleField).assertTextContains("My own")
    }
}
