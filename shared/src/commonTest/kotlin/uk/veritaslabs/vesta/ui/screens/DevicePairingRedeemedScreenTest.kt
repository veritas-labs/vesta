package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class DevicePairingRedeemedScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheDoneButton() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { DevicePairingRedeemedScreen() } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(DevicePairingRedeemedTestTags.DoneButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingDoneInvokesOnDone() = runComposeUiTest {
        // Arrange
        var done = false
        setContent { VestaTheme { DevicePairingRedeemedScreen(onDone = { done = true }) } }

        // Act
        onNodeWithTag(DevicePairingRedeemedTestTags.DoneButton).performClick()

        // Assert
        assertTrue(done)
    }
}
