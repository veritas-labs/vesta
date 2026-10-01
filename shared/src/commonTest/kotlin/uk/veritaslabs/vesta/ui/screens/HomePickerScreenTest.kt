package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomePickerScreenTest {

    private val homes = listOf(
        HomeListItem(id = "the-johnsons", name = "The Johnsons"),
        HomeListItem(id = "flat-4b", name = "Flat 4B"),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEachHomeAndTheCreateJoinAndPairDeviceButtons() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomePickerScreen(homes = homes) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        // Text assertions here because the home names are the data under
        // test — a real regression (e.g. the wrong home's name rendering)
        // should fail this test, unlike the tag-based assertions below.
        onNodeWithText("The Johnsons").assertIsDisplayed()
        onNodeWithText("Flat 4B").assertIsDisplayed()
        onNodeWithTag(HomePickerTestTags.CreateHomeButton).assertIsDisplayed()
        onNodeWithTag(HomePickerTestTags.JoinHomeButton).assertIsDisplayed()
        onNodeWithTag(HomePickerTestTags.PairDeviceButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingAHomeInvokesOnHomeSelected() = runComposeUiTest {
        // Arrange
        var selected: HomeListItem? = null
        setContent {
            VestaTheme { HomePickerScreen(homes = homes, onHomeSelected = { selected = it }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.homeItem("the-johnsons")).performClick()

        // Assert
        assertEquals("the-johnsons", selected?.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingCreateHomeInvokesOnCreateHome() = runComposeUiTest {
        // Arrange
        var created = false
        setContent {
            VestaTheme { HomePickerScreen(homes = homes, onCreateHome = { created = true }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.CreateHomeButton).performClick()

        // Assert
        assertTrue(created)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingPairDeviceInvokesOnPairDevice() = runComposeUiTest {
        // Arrange
        var paired = false
        setContent {
            VestaTheme { HomePickerScreen(homes = emptyList(), onPairDevice = { paired = true }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.PairDeviceButton).performClick()

        // Assert
        assertTrue(paired)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingJoinHomeInvokesOnJoinHome() = runComposeUiTest {
        // Arrange
        var joined = false
        setContent {
            VestaTheme { HomePickerScreen(homes = homes, onJoinHome = { joined = true }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.JoinHomeButton).performClick()

        // Assert
        assertTrue(joined)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun longPressingAHomeOpensActionsSheetWithEditAndDelete() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomePickerScreen(homes = homes) } }

        // Act
        onNodeWithTag(HomePickerTestTags.homeItem("the-johnsons")).performTouchInput { longClick() }

        // Assert
        onNodeWithTag(HomePickerTestTags.EditHomeButton).assertIsDisplayed()
        onNodeWithTag(HomePickerTestTags.DeleteHomeButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editHomeInSheetInvokesOnEditHomeForThatHome() = runComposeUiTest {
        // Arrange
        var edited: HomeListItem? = null
        setContent {
            VestaTheme { HomePickerScreen(homes = homes, onEditHome = { edited = it }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.homeItem("flat-4b")).performTouchInput { longClick() }
        onNodeWithTag(HomePickerTestTags.EditHomeButton).performClick()

        // Assert
        assertEquals("flat-4b", edited?.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteHomeInSheetInvokesOnDeleteHomeForThatHome() = runComposeUiTest {
        // Arrange
        var deleted: HomeListItem? = null
        setContent {
            VestaTheme { HomePickerScreen(homes = homes, onDeleteHome = { deleted = it }) }
        }

        // Act
        onNodeWithTag(HomePickerTestTags.homeItem("flat-4b")).performTouchInput { longClick() }
        onNodeWithTag(HomePickerTestTags.DeleteHomeButton).performClick()

        // Assert
        assertEquals("flat-4b", deleted?.id)
    }
}
