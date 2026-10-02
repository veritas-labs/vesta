package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.homejoin.InviteLink
import uk.veritaslabs.vesta.homejoin.InviteLinkItem
import uk.veritaslabs.vesta.homejoin.InviteLinksUiState
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class InviteLinksScreenTest {

    private val created = InviteLink("home-1", "s3cr3t", 1_000L, listOf("agent-a"), "Maple Street")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theCreateButtonMakesALink() = runComposeUiTest {
        // Arrange
        var creates = 0
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(), onCreate = { creates++ }) } }

        // Act
        onNodeWithTag(InviteLinksTestTags.CreateButton).performClick()

        // Assert
        assertEquals(1, creates)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun saysSoWhenThereAreNoActiveLinks() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(InviteLinksTestTags.None).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun listsEachActiveLinkAndRevokesTheOneTapped() = runComposeUiTest {
        // Arrange
        val revoked = mutableListOf<String>()
        val links = listOf(InviteLinkItem("a", 6, 0, 1), InviteLinkItem("b", 0, 1, 3))
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(links = links), onRevoke = { revoked += it }) } }

        // Act
        onAllNodesWithTag(InviteLinksTestTags.RevokeButton)[1].performClick()

        // Assert
        onAllNodesWithTag(InviteLinksTestTags.Row).assertCountEquals(2)
        assertEquals(listOf("b"), revoked)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsWhenALinkCouldNotBeMade() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(failed = true)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(InviteLinksTestTags.Error).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aJustMadeLinkIsShownToShareAndBackReturnsToTheList() = runComposeUiTest {
        // Arrange
        var dismissed = 0
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(created = created), onDismissCreated = { dismissed++ }) } }

        // Act
        onNodeWithTag(DevicePairingGeneratedTestTags.CopyLinkButton).assertIsDisplayed()
        onNodeWithTag(DevicePairingGeneratedTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, dismissed)
        onAllNodesWithTag(InviteLinksTestTags.CreateButton).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theBackArrowLeavesTheScreen() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(), onBack = { backs++ }) } }

        // Act
        onNodeWithTag(InviteLinksTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }
}
