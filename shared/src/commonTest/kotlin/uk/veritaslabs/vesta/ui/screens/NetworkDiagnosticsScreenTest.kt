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
import uk.veritaslabs.vesta.x0x.X0xLogEntry
import uk.veritaslabs.vesta.x0x.X0xLogEvent
import uk.veritaslabs.vesta.x0x.X0xNetworkDiagnostics

class NetworkDiagnosticsScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheConnectionStatusAndSpeeds() = runComposeUiTest {
        // Arrange
        val diagnostics = X0xNetworkDiagnostics(
            connectivity = X0xConnectivity.Connected(5),
            downBytesPerSec = 2_048,
            upBytesPerSec = 512,
        )
        setContent { VestaTheme { NetworkDiagnosticsScreen(diagnostics) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Connected").assertIsDisplayed()
        onNodeWithText("2.0 KB/s").assertIsDisplayed()
        onNodeWithText("512 B/s").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsALogLineForEachEvent() = runComposeUiTest {
        // Arrange
        val diagnostics = X0xNetworkDiagnostics(
            sampledAtMillis = 10_000,
            log = listOf(X0xLogEntry(4_000, X0xLogEvent.PeersChanged(3, 4))),
        )
        setContent { VestaTheme { NetworkDiagnosticsScreen(diagnostics) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Peers 3 → 4").assertIsDisplayed()
        onNodeWithText("6 s ago").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun saysWhenNothingHasHappenedYet() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { NetworkDiagnosticsScreen(X0xNetworkDiagnostics()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(NetworkDiagnosticsTestTags.LogEmpty).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backArrowCallsOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { NetworkDiagnosticsScreen(X0xNetworkDiagnostics(), onBack = { backs++ }) } }

        // Act
        onNodeWithTag(NetworkDiagnosticsTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }
}
