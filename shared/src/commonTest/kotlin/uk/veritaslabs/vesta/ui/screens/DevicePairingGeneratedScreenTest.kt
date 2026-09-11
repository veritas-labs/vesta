package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class DevicePairingGeneratedScreenTest {

    private val sampleLink = "https://vesta.example/pair#test"

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheLink() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { DevicePairingGeneratedScreen(pairingLink = sampleLink) } }

        // Act
        // (no user interaction — the link is shown on initial composition)

        // Assert
        onNodeWithText(sampleLink).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingCopyInvokesOnCopyLink() = runComposeUiTest {
        // Arrange
        var copied = false
        setContent {
            VestaTheme {
                DevicePairingGeneratedScreen(pairingLink = sampleLink, onCopyLink = { copied = true })
            }
        }

        // Act
        onNodeWithText("Copy link").performClick()

        // Assert
        assertTrue(copied)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingCancelInvokesOnCancel() = runComposeUiTest {
        // Arrange
        var cancelled = false
        setContent {
            VestaTheme { DevicePairingGeneratedScreen(pairingLink = sampleLink, onCancel = { cancelled = true }) }
        }

        // Act
        onNodeWithText("Cancel").performClick()

        // Assert
        assertTrue(cancelled)
    }
}
