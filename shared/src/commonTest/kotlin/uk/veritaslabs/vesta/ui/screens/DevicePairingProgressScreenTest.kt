package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import uk.veritaslabs.vesta.devicesync.PairingStep
import uk.veritaslabs.vesta.devicesync.PairingStepStatus
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class DevicePairingProgressScreenTest {

    private val allPending = PairingStep.entries.associateWith { PairingStepStatus.Pending }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsEveryStepOfTheChecklist() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { DevicePairingProgressScreen(steps = allPending) } }

        // Act
        // (no user interaction — checking the initial checklist)

        // Assert
        PairingStep.entries.forEach { onNodeWithTag(DevicePairingProgressTestTags.step(it)).assertIsDisplayed() }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun offersNoActionsWhileStepsAreStillRunning() = runComposeUiTest {
        // Arrange
        val steps = allPending + (PairingStep.PrepareGroup to PairingStepStatus.Running)
        setContent { VestaTheme { DevicePairingProgressScreen(steps = steps) } }

        // Act
        // (no user interaction)

        // Assert
        onNodeWithTag(DevicePairingProgressTestTags.RetryButton).assertDoesNotExist()
        onNodeWithTag(DevicePairingProgressTestTags.BackButton).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheErrorAndRetryAndBackWhenAStepFailed() = runComposeUiTest {
        // Arrange
        val steps = allPending + (PairingStep.AwaitJoin to PairingStepStatus.Failed)
        var retried = false
        var cancelled = false
        setContent {
            VestaTheme {
                DevicePairingProgressScreen(
                    steps = steps,
                    errorMessage = "Couldn't reach that device.",
                    onRetry = { retried = true },
                    onCancel = { cancelled = true },
                )
            }
        }

        // Act
        onNodeWithTag(DevicePairingProgressTestTags.RetryButton).performClick()
        onNodeWithTag(DevicePairingProgressTestTags.BackButton).performClick()

        // Assert
        onNodeWithTag(DevicePairingProgressTestTags.ErrorText).assertIsDisplayed()
        assertTrue(retried)
        assertTrue(cancelled)
    }
}
