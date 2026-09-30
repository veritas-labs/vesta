package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xConnectivity

class HomeDashboardScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAPantryTile() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons") } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeDashboardTestTags.PantryTile).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingThePantryTileInvokesOnPantryClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", onPantryClick = { clicks++ }) } }

        // Act
        onNodeWithTag(HomeDashboardTestTags.PantryTile).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAnX0xConnectivityIndicatorInTheAppBar() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", x0xConnectivity = X0xConnectivity.Connected(peers = 23)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeDashboardTestTags.X0xIndicator).assertIsDisplayed()
        onNodeWithText("x0x").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheHomeNameInTheAppBar() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "Flat 4B") } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Flat 4B").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingBackInvokesOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", onBack = { backs++ }) } }

        // Act
        onNodeWithTag(HomeDashboardTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }
}
