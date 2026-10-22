package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.ui.components.EmptyState
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.homegroup.HubUiState
import uk.veritaslabs.vesta.ui.components.EmptyStateTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/** Every full-screen empty state shows its line-art picture (and a list with something in it does not). */
@OptIn(ExperimentalTestApi::class)
class EmptyStateIllustrationsTest {

    private val illustration = EmptyStateTestTags.Illustration
    private val pie = Recipe(key = "pie", title = "Shepherd's Pie", type = RecipeType.Dinner)

    @Test
    fun anEmptyPantryShowsItsPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryScreen(items = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
    }

    @Test
    fun anEmptyShoppingListShowsItsPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { ShoppingListScreen(items = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
    }

    @Test
    fun noPantryCategoriesShowsItsPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { PantryCategoriesScreen(categories = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
    }

    @Test
    fun anEmptyCookbookShowsItsPictureButACookbookWithRecipesDoesNot() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CookbookScreen(recipes = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
    }

    @Test
    fun aCookbookWithARecipeHasNoEmptyPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CookbookScreen(recipes = listOf(pie)) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertDoesNotExist()
    }

    @Test
    fun noPairedDevicesShowsItsPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { SettingsSyncedDevicesScreen(devices = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
    }

    @Test
    fun noHubsShowsItsPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HubManagerScreen(HubUiState(loading = false)) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
    }

    @Test
    fun noAssistantsShowsItsPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { AssistantsScreen(assistants = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
    }

    @Test
    fun anEmptyCookbookInTheMealPickerShowsTheCookbookPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MealPlannerPickerScreen(emptyList(), CalendarDate(2026, 10, 12), MealSlot.Dinner) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
    }

    @Test
    fun aDayWithNoEventsShowsTheQuietDayPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { CalendarScreen(today = CalendarDate(2026, 10, 14), events = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
    }

    private fun ComposeUiTest.emptyStateIn(width: Int, height: Int) = setContent {
        VestaTheme {
            Box(Modifier.size(width.dp, height.dp)) { EmptyState(EmptyStateArt.Pantry, "Your pantry is empty", body = "Items your household buys will show up here.") }
        }
    }

    @Test
    fun onAWideScreenThePictureIsBesideTheWords() = runComposeUiTest {
        // Arrange
        emptyStateIn(width = 1000, height = 600)

        // Act
        val picture = onNodeWithTag(illustration).getUnclippedBoundsInRoot()
        val title = onNodeWithTag(EmptyStateTestTags.Title).getUnclippedBoundsInRoot()

        // Assert
        assertTrue(title.left >= picture.right, "the words start to the right of the picture")
    }

    @Test
    fun onANarrowScreenThePictureIsAboveTheWords() = runComposeUiTest {
        // Arrange
        emptyStateIn(width = 360, height = 700)

        // Act
        val picture = onNodeWithTag(illustration).getUnclippedBoundsInRoot()
        val title = onNodeWithTag(EmptyStateTestTags.Title).getUnclippedBoundsInRoot()

        // Assert
        assertTrue(title.top >= picture.bottom, "the words start below the picture")
    }

    @Test
    fun aWideButShortLandscapeScreenAlsoPutsThePictureBesideTheWords() = runComposeUiTest {
        // Arrange
        emptyStateIn(width = 800, height = 380)

        // Act
        val picture = onNodeWithTag(illustration).getUnclippedBoundsInRoot()
        val title = onNodeWithTag(EmptyStateTestTags.Title).getUnclippedBoundsInRoot()

        // Assert
        assertTrue(title.left >= picture.right, "the words start to the right of the picture")
    }

    @Test
    fun aTallWideScreenKeepsThePictureAboveTheWords() = runComposeUiTest {
        // Arrange
        emptyStateIn(width = 720, height = 760)

        // Act
        val picture = onNodeWithTag(illustration).getUnclippedBoundsInRoot()
        val title = onNodeWithTag(EmptyStateTestTags.Title).getUnclippedBoundsInRoot()

        // Assert
        assertTrue(title.top >= picture.bottom, "the words start below the picture")
    }
}
