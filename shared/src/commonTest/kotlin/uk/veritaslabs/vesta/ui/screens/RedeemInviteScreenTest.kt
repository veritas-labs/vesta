package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.homejoin.RedeemInviteError
import uk.veritaslabs.vesta.homejoin.RedeemInviteUiState
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class RedeemInviteScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theSubmitButtonNeedsALink() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RedeemInviteScreen(RedeemInviteUiState.Idle) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(RedeemInviteTestTags.SubmitButton).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun submittingPassesThePastedLinkOn() = runComposeUiTest {
        // Arrange
        var sent: String? = null
        setContent { VestaTheme { RedeemInviteScreen(RedeemInviteUiState.Idle, onRedeem = { sent = it }) } }

        // Act
        onNodeWithTag(RedeemInviteTestTags.LinkField).performTextInput("vesta://invite/abc")
        onNodeWithTag(RedeemInviteTestTags.SubmitButton).assertIsEnabled().performClick()

        // Assert
        assertEquals("vesta://invite/abc", sent)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsWhyARequestFailed() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RedeemInviteScreen(RedeemInviteUiState.Failed(RedeemInviteError.Expired)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(RedeemInviteTestTags.Error).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsProgressAndStopsFurtherSubmitsWhileRequesting() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { RedeemInviteScreen(RedeemInviteUiState.Redeeming) } }

        // Act
        onNodeWithTag(RedeemInviteTestTags.LinkField).assertIsNotEnabled()

        // Assert
        onNodeWithTag(RedeemInviteTestTags.Progress).assertIsDisplayed()
        onNodeWithTag(RedeemInviteTestTags.SubmitButton).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onceTheRequestIsOnItsWayTheScreenSaysSo() = runComposeUiTest {
        // Arrange
        var started = 0
        setContent { VestaTheme { RedeemInviteScreen(RedeemInviteUiState.Started, onStarted = { started++ }) } }

        // Act
        waitForIdle()

        // Assert
        assertEquals(1, started)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theBackArrowLeavesTheScreen() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { RedeemInviteScreen(RedeemInviteUiState.Idle, onBack = { backs++ }) } }

        // Act
        onNodeWithTag(RedeemInviteTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aScannedCodeIsSubmittedStraightAway() = runComposeUiTest {
        // Arrange
        var sent: String? = null
        setContent {
            VestaTheme {
                RedeemInviteScreen(
                    RedeemInviteUiState.Idle,
                    onRedeem = { sent = it },
                    scanSupported = true,
                    qrCamera = { onBarcode, cameraModifier ->
                        Box(cameraModifier.testTag("fake_camera").clickable { onBarcode("vesta://invite/scanned") })
                    },
                )
            }
        }

        // Act
        onNodeWithTag(RedeemInviteTestTags.ScanButton).performClick()
        onNodeWithTag("fake_camera").performClick()

        // Assert
        assertEquals("vesta://invite/scanned", sent)
    }
}
