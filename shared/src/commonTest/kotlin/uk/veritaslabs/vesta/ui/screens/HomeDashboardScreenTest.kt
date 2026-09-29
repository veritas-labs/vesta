package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class HomeDashboardScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAPantryTile() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen() } }

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
        setContent { VestaTheme { HomeDashboardScreen(onPantryClick = { clicks++ }) } }

        // Act
        onNodeWithTag(HomeDashboardTestTags.PantryTile).performClick()

        // Assert
        assertEquals(1, clicks)
    }
}
