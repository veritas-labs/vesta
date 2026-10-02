package uk.veritaslabs.vesta.ui.flows

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.homejoin.FakeHomeJoining
import uk.veritaslabs.vesta.homejoin.InviteLink
import uk.veritaslabs.vesta.ui.screens.DevicePairingGeneratedTestTags
import uk.veritaslabs.vesta.ui.screens.RedeemInviteTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

/** The Join a home flow: the join link to show an admin, and the way over to pasting an invite link instead. */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinHomeFlowTest {

    private val invite = InviteLink("home-1", "s3cr3t", expiresAt = Long.MAX_VALUE, adminAgentIds = listOf("agent-a"), homeName = "Maple Street")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheJoinLinkWithAWayToUseAnInviteLinkInstead() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { JoinHomeFlow(FakeHomeJoining(), onExit = {}, onJoined = {}) } }

        // Act
        waitUntilExactlyOneExists(hasTestTag(DevicePairingGeneratedTestTags.CopyLinkButton))

        // Assert
        onNodeWithTag(JoinHomeFlowTestTags.HaveInviteLinkButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pastingAnInviteLinkAsksToJoinAndLeavesTheFlow() = runComposeUiTest {
        // Arrange
        val joining = FakeHomeJoining()
        var exits = 0
        setContent { VestaTheme { JoinHomeFlow(joining, onExit = { exits++ }, onJoined = {}) } }
        waitUntilExactlyOneExists(hasTestTag(JoinHomeFlowTestTags.HaveInviteLinkButton))
        onNodeWithTag(JoinHomeFlowTestTags.HaveInviteLinkButton).performClick()

        // Act
        onNodeWithTag(RedeemInviteTestTags.LinkField).performTextInput(invite.encode())
        onNodeWithTag(RedeemInviteTestTags.SubmitButton).performClick()
        waitUntil { exits > 0 }

        // Assert
        assertEquals(listOf(invite), joining.redeemed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backFromTheInviteLinkScreenReturnsToTheJoinLink() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { JoinHomeFlow(FakeHomeJoining(), onExit = {}, onJoined = {}) } }
        waitUntilExactlyOneExists(hasTestTag(JoinHomeFlowTestTags.HaveInviteLinkButton))
        onNodeWithTag(JoinHomeFlowTestTags.HaveInviteLinkButton).performClick()

        // Act
        onNodeWithTag(RedeemInviteTestTags.BackButton).performClick()

        // Assert
        onNodeWithTag(DevicePairingGeneratedTestTags.CopyLinkButton).assertIsDisplayed()
    }
}
