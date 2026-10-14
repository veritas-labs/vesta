package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
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
    fun noMerchantsShowsItsPicture() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MerchantsScreen(merchants = emptyList()) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(illustration).assertIsDisplayed()
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
}
