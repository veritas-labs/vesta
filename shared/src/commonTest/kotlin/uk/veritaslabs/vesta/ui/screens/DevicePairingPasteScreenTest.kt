package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevicePairingPasteScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheFieldAndDisabledSubmitWhenEmpty() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { DevicePairingPasteScreen() } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(DevicePairingPasteTestTags.LinkField).assertIsDisplayed()
        onNodeWithTag(DevicePairingPasteTestTags.SubmitButton).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun typingAndSubmittingInvokesOnSubmitPairingLinkWithTypedValue() = runComposeUiTest {
        // Arrange
        var submitted: String? = null
        setContent {
            VestaTheme { DevicePairingPasteScreen(onSubmitPairingLink = { submitted = it }) }
        }

        // Act
        onNodeWithTag(DevicePairingPasteTestTags.LinkField).performTextInput("https://vesta.example/pair#typed")
        onNodeWithTag(DevicePairingPasteTestTags.SubmitButton).performClick()

        // Assert
        assertEquals("https://vesta.example/pair#typed", submitted)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingCancelInvokesOnCancel() = runComposeUiTest {
        // Arrange
        var cancelled = false
        setContent { VestaTheme { DevicePairingPasteScreen(onCancel = { cancelled = true }) } }

        // Act
        onNodeWithTag(DevicePairingPasteTestTags.CancelButton).performClick()

        // Assert
        assertTrue(cancelled)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsErrorMessageWhenProvided() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { DevicePairingPasteScreen(errorMessage = "This pairing link has expired.") } }

        // Act
        // (no user interaction — checking the screen renders a caller-supplied error)

        // Assert
        onNodeWithTag(DevicePairingPasteTestTags.ErrorText).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hidesErrorMessageWhenNotProvided() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { DevicePairingPasteScreen() } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(DevicePairingPasteTestTags.ErrorText).assertDoesNotExist()
    }
}
