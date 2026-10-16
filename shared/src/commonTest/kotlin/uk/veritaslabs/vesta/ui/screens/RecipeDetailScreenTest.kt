package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.kits.blobs.MediaKind
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalTestApi::class)
class RecipeDetailScreenTest {

    private val recipe = RecipePreview

    @Test
    fun showsPrepCookRestAndTotalTime() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RecipeDetailScreen(recipe) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(RecipeDetailTestTags.PrepTime).assertTextEquals("20 min")
        onNodeWithTag(RecipeDetailTestTags.CookTime).assertTextEquals("50 min")
        onNodeWithTag(RecipeDetailTestTags.RestTime).assertTextEquals("10 min")
        onNodeWithTag(RecipeDetailTestTags.TotalTime).assertTextEquals("1 h 20 min")
    }

    @Test
    fun aTimeThatIsZeroShowsADash() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RecipeDetailScreen(recipe.copy(restMinutes = 0)) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(RecipeDetailTestTags.RestTime).assertTextEquals("-")
    }

    @Test
    fun eachIngredientIsLabelledInPantryOutOfStockOrNotInPantry() = runComposeUiTest {
        // Arrange
        val pantry = listOf(
            PantryListItem("potatoes", "Veg", null, "Potatoes", "", 3),
            PantryListItem("onion", "Veg", null, "Onion", "", 0),
        )
        setContent { VestaTheme { RecipeDetailScreen(recipe, pantry = pantry) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(RecipeDetailTestTags.availability(0)).assertTextEquals("Not in pantry") // lamb
        onNodeWithTag(RecipeDetailTestTags.availability(1)).assertTextEquals("In pantry") // potatoes
        onNodeWithTag(RecipeDetailTestTags.availability(2)).assertTextEquals("Out of stock") // onion
    }

    @Test
    fun scalingTheServingsCanTipAnIngredientIntoNotEnough() = runComposeUiTest {
        // Arrange — 800 g of potatoes for 4, and a Pantry holding 1 kg
        val pantry = listOf(PantryListItem("potatoes", "Veg", null, "Potatoes", "500g", 2))
        setContent { VestaTheme { RecipeDetailScreen(recipe, pantry = pantry) } }
        onNodeWithTag(RecipeDetailTestTags.availability(1)).assertTextEquals("In pantry")

        // Act — double the servings, so 1.6 kg is needed
        repeat(4) { onNodeWithTag(RecipeDetailTestTags.ServingsIncrease).performClick() }

        // Assert
        onNodeWithTag(RecipeDetailTestTags.availability(1)).assertTextEquals("Not enough")
    }

    @Test
    fun theMethodIsOneNumberedListOfSteps() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RecipeDetailScreen(recipe) } }

        // Act
        onNodeWithTag(RecipeDetailTestTags.step(1)).performScrollTo()

        // Assert
        onNodeWithTag(RecipeDetailTestTags.step(4)).assertExists()
        onNodeWithTag(RecipeDetailTestTags.stepMinutes(3)).assertDoesNotExist()
        onNodeWithTag(RecipeDetailTestTags.stepMinutes(4)).assertTextEquals("30 min")
    }

    @Test
    fun theServingsStepperRescalesQuantitiesWithoutChangingTheRecipe() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RecipeDetailScreen(recipe) } }

        // Act
        repeat(4) { onNodeWithTag(RecipeDetailTestTags.ServingsIncrease).performClick() }

        // Assert
        onNodeWithTag(RecipeDetailTestTags.Servings).assertTextEquals("Serves 8")
        onNodeWithText("1000 g Lamb mince").assertIsDisplayed()
    }

    @Test
    fun tappingAStarRatesAndTappingTheLitOneAgainClears() = runComposeUiTest {
        // Arrange
        val rated = mutableListOf<Int>()
        setContent { VestaTheme { RecipeDetailScreen(recipe.copy(rating = 4), onRate = { rated += it }) } }

        // Act
        onNodeWithTag(RecipeDetailTestTags.star(5)).performClick()
        onNodeWithTag(RecipeDetailTestTags.star(4)).performClick()

        // Assert
        assertEquals(listOf(5, 0), rated)
    }

    @Test
    fun deletingAsksForConfirmationFirst() = runComposeUiTest {
        // Arrange
        var deleted = false
        setContent { VestaTheme { RecipeDetailScreen(recipe, onDelete = { deleted = true }) } }

        // Act
        onNodeWithTag(RecipeDetailTestTags.OverflowButton).performClick()
        onNodeWithTag(RecipeDetailTestTags.DeleteMenuItem).performClick()
        val beforeConfirm = deleted
        onNodeWithTag(RecipeDetailTestTags.ConfirmDelete).performClick()

        // Assert
        assertEquals(false, beforeConfirm)
        assertEquals(true, deleted)
    }

    @Test
    fun theFavouriteButtonReportsTheOppositeOfTheCurrentState() = runComposeUiTest {
        // Arrange
        val changes = mutableListOf<Boolean>()
        setContent { VestaTheme { RecipeDetailScreen(recipe.copy(favourite = true), onFavourite = { changes += it }) } }

        // Act
        onNodeWithTag(RecipeDetailTestTags.FavouriteButton).performClick()

        // Assert
        assertEquals(listOf(false), changes)
    }

    @Test
    fun aBlobPhotoShowsItsPlaceholderUntilThePhotoArrives() = runComposeUiTest {
        // Arrange
        val blobs = uk.veritaslabs.kits.blobs.FakeBlobs()
        val reference = uk.veritaslabs.kits.blobs.testBlobReference()
        val withPhoto = recipe.copy(media = listOf(RecipeMedia("", MediaKind.Photo, "Fresh", reference)))
        setContent {
            VestaTheme {
                androidx.compose.runtime.CompositionLocalProvider(uk.veritaslabs.vesta.ui.components.LocalBlobs provides blobs) { RecipeDetailScreen(withPhoto) }
            }
        }
        waitUntil { runCatching { onNodeWithTag(uk.veritaslabs.vesta.ui.components.BlobImageTestTags.Waiting).assertIsDisplayed() }.isSuccess }

        // Act
        blobs.arrive(reference.hash)
        waitUntil { runCatching { onNodeWithTag(uk.veritaslabs.vesta.ui.components.BlobImageTestTags.Photo).assertIsDisplayed() }.isSuccess }

        // Assert
        onNodeWithTag(uk.veritaslabs.vesta.ui.components.BlobImageTestTags.Photo).assertIsDisplayed()
    }

    @Test
    fun onlyTheFirstPhotoIsShown() = runComposeUiTest {
        // Arrange
        val twoPhotos = recipe.copy(media = listOf(RecipeMedia("file:///a.jpg", MediaKind.Photo, "First"), RecipeMedia("file:///b.jpg", MediaKind.Photo, "Second")))
        setContent { VestaTheme { RecipeDetailScreen(twoPhotos) } }

        // Act / Assert
        onNodeWithText("First").assertIsDisplayed()
        onNodeWithTag(RecipeDetailTestTags.media(1)).assertDoesNotExist()
    }
}
