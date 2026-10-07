package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xPeer
import uk.veritaslabs.vesta.x0x.X0xPeerStatus

class NetworkPeersScreenTest {

    private fun peers(count: Int) = List(count) { X0xPeer("peer-$it", X0xPeerStatus.Active, direct = false) }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAGraphWithThePeerCount() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { NetworkPeersScreen(peers(12)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(NetworkPeersTestTags.Graph).assertIsDisplayed()
        onNodeWithText("You").assertIsDisplayed()
        onNodeWithText("12 peers").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsALoadingMessageBeforeTheFirstAnswer() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { NetworkPeersScreen(peers = null) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(NetworkPeersTestTags.Loading).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun explainsWhenThereAreNoPeers() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { NetworkPeersScreen(peers = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(NetworkPeersTestTags.Empty).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingBackInvokesOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { NetworkPeersScreen(peers(3), onBack = { backs++ }) } }

        // Act
        onNodeWithTag(NetworkPeersTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingAPeerShowsItsDetailsAndMeasuresItsRoundTrip() = runComposeUiTest {
        // Arrange
        val peer = X0xPeer("m".repeat(64), X0xPeerStatus.Active, direct = true, agentId = "a".repeat(64), lastReceivedMsAgo = 5_000)
        var probed: String? = null
        setContent { VestaTheme { NetworkPeersScreen(listOf(peer), probe = { probed = it; 42L }) } }
        val position = peerPositions(1).single()

        // Act
        onNodeWithTag(NetworkPeersTestTags.Graph).performTouchInput {
            val reach = minOf(width, height) / 2f - 12.dp.toPx()
            click(Offset(width / 2f + position.x * reach, height / 2f + position.y * reach))
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        // Assert
        onNodeWithTag(NetworkPeersTestTags.Selected).assertIsDisplayed()
        onNodeWithText("42 ms").assertIsDisplayed()
        onNodeWithText("5 s ago").assertIsDisplayed()
        assertEquals(peer.id, probed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsNoDetailsUntilAPeerIsChosen() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { NetworkPeersScreen(peers(3)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(NetworkPeersTestTags.Selected).assertDoesNotExist()
    }

    @Test
    fun longIdsAreShortenedAndShortOnesKept() {
        // Arrange
        val long = "0123456789abcdef0123456789abcdef"

        // Act
        val shortened = shortId(long)

        // Assert
        assertEquals("01234567…89abcdef", shortened)
        assertEquals("peer-1", shortId("peer-1"))
    }

    @Test
    fun peerPositionsStayInsideTheGraphAndAreDistinct() {
        // Arrange
        val count = 72

        // Act
        val positions = peerPositions(count)

        // Assert
        assertEquals(count, positions.size)
        assertTrue(positions.all { kotlin.math.hypot(it.x, it.y) <= 1f })
        assertEquals(count, positions.toSet().size)
    }
}
