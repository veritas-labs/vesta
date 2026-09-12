package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class DevicePairingScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsBothPairingOptions() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { DevicePairingScreen() } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(DevicePairingTestTags.GenerateCard).assertIsDisplayed()
        onNodeWithTag(DevicePairingTestTags.PasteCard).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingGenerateInvokesOnGeneratePairingLink() = runComposeUiTest {
        // Arrange
        var generated = false
        setContent {
            VestaTheme { DevicePairingScreen(onGeneratePairingLink = { generated = true }) }
        }

        // Act
        onNodeWithTag(DevicePairingTestTags.GenerateCard).performClick()

        // Assert
        assertTrue(generated)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingPasteInvokesOnPastePairingLink() = runComposeUiTest {
        // Arrange
        var pasted = false
        setContent {
            VestaTheme { DevicePairingScreen(onPastePairingLink = { pasted = true }) }
        }

        // Act
        onNodeWithTag(DevicePairingTestTags.PasteCard).performClick()

        // Assert
        assertTrue(pasted)
    }
}
