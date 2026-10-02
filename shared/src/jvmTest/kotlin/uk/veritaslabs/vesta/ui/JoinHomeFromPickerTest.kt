package uk.veritaslabs.vesta.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.App
import uk.veritaslabs.vesta.homejoin.FakeHomeJoining
import uk.veritaslabs.vesta.homejoin.InviteLink
import uk.veritaslabs.vesta.homejoin.PendingJoin
import uk.veritaslabs.vesta.ui.screens.HomePickerTestTags
import uk.veritaslabs.vesta.x0x.X0xConnectivity
import uk.veritaslabs.vesta.ui.screens.RedeemInviteTestTags

/** Joining a Home from the picker (docs/adr/ADR-019-one-way-to-join.md): Join a home goes straight to the invite link screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinHomeFromPickerTest {

    // The view models' scope is Dispatchers.Main; an unconfined one runs them on the test's own thread, which the
    // lifecycle bookkeeping of the composition needs (a pool thread races it).
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val invite = InviteLink("home-1", "s3cr3t", expiresAt = Long.MAX_VALUE, adminAgentIds = listOf("agent-a"), homeName = "Maple Street")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theJoinTileOpensTheInviteLinkScreenDirectly() = runComposeUiTest {
        // Arrange
        setContent { App(homeJoining = FakeHomeJoining(), x0xConnectivity = { X0xConnectivity.Connected(peers = 5) }) }

        // Act
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.JoinHomeButton))
        onNodeWithTag(HomePickerTestTags.JoinHomeButton).performClick()

        // Assert
        onNodeWithTag(RedeemInviteTestTags.LinkField).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pastingTheLinkAsksToJoinAndReturnsToThePicker() = runComposeUiTest {
        // Arrange
        val joining = FakeHomeJoining()
        setContent { App(homeJoining = joining, x0xConnectivity = { X0xConnectivity.Connected(peers = 5) }) }
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.JoinHomeButton))
        onNodeWithTag(HomePickerTestTags.JoinHomeButton).performClick()

        // Act
        onNodeWithTag(RedeemInviteTestTags.LinkField).performTextInput(invite.encode())
        onNodeWithTag(RedeemInviteTestTags.SubmitButton).performClick()
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.JoinHomeButton))

        // Assert
        assertEquals(listOf(invite), joining.redeemed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backFromTheInviteLinkScreenReturnsToThePicker() = runComposeUiTest {
        // Arrange
        setContent { App(homeJoining = FakeHomeJoining(), x0xConnectivity = { X0xConnectivity.Connected(peers = 5) }) }
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.JoinHomeButton))
        onNodeWithTag(HomePickerTestTags.JoinHomeButton).performClick()

        // Act
        onNodeWithTag(RedeemInviteTestTags.BackButton).performClick()

        // Assert
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.JoinHomeButton))
        onNodeWithTag(HomePickerTestTags.JoinHomeButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theRequestShowsAsAPendingCardOnThePicker() = runComposeUiTest {
        // Arrange
        val joining = FakeHomeJoining().apply { pending.value = listOf(PendingJoin("home-1", "Maple Street", 1L, denial = null)) }

        // Act
        setContent { App(homeJoining = joining, x0xConnectivity = { X0xConnectivity.Connected(peers = 5) }) }

        // Assert
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.pendingJoin("home-1")))
        onNodeWithTag(HomePickerTestTags.pendingJoin("home-1")).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aRequestWaitsForANetworkConnectionWhileTheDaemonHasNoPeers() = runComposeUiTest {
        // Arrange
        val joining = FakeHomeJoining().apply { pending.value = listOf(PendingJoin("home-1", "Maple Street", 1L, denial = null)) }

        // Act
        setContent { App(homeJoining = joining, x0xConnectivity = { X0xConnectivity.Connected(peers = 0) }) }

        // Assert
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.pendingJoin("home-1")))
        onNodeWithText("Waiting for a network connection…").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aRequestWaitsForAnAdminOnceTheDaemonHasPeers() = runComposeUiTest {
        // Arrange
        val joining = FakeHomeJoining().apply { pending.value = listOf(PendingJoin("home-1", "Maple Street", 1L, denial = null)) }

        // Act
        setContent { App(homeJoining = joining, x0xConnectivity = { X0xConnectivity.Connected(peers = 3) }) }

        // Assert
        waitUntilExactlyOneExists(hasTestTag(HomePickerTestTags.pendingJoin("home-1")))
        onNodeWithText("Waiting for an admin to approve you…").assertIsDisplayed()
    }
}
