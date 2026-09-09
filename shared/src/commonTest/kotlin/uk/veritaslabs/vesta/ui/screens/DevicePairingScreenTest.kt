package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class DevicePairingScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsBothPairingOptions() = runComposeUiTest {
        setContent { VestaTheme { DevicePairingScreen() } }

        onNodeWithText("Generate device pairing link").assertIsDisplayed()
        onNodeWithText("Paste a device pairing link").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingGenerateInvokesOnGeneratePairingLink() = runComposeUiTest {
        var generated = false
        setContent {
            VestaTheme { DevicePairingScreen(onGeneratePairingLink = { generated = true }) }
        }

        onNodeWithText("Generate device pairing link").performClick()

        assertTrue(generated)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingPasteInvokesOnPastePairingLink() = runComposeUiTest {
        var pasted = false
        setContent {
            VestaTheme { DevicePairingScreen(onPastePairingLink = { pasted = true }) }
        }

        onNodeWithText("Paste a device pairing link").performClick()

        assertTrue(pasted)
    }
}
