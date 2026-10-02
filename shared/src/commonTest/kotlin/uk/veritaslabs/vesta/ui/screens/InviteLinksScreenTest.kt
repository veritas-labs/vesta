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
    fun theNewLinkIsShownToCopyWithAWayToManageLinks() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(created = created)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(DevicePairingGeneratedTestTags.CopyLinkButton).assertIsDisplayed()
        onNodeWithTag(InviteLinksTestTags.ManageButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingManageLinksAsksToManage() = runComposeUiTest {
        // Arrange
        var manages = 0
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(created = created), onManage = { manages++ }) } }

        // Act
        onNodeWithTag(InviteLinksTestTags.ManageButton).performClick()

        // Assert
        assertEquals(1, manages)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backFromTheNewLinkLeavesTheScreen() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(created = created), onBack = { backs++ }) } }

        // Act
        onNodeWithTag(DevicePairingGeneratedTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsProgressWhileTheLinkIsBeingMade() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(InviteLinksTestTags.Progress).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsWhenNoLinkCouldBeMadeAndBackLeaves() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(failed = true), onBack = { backs++ }) } }

        // Act
        onNodeWithTag(InviteLinksTestTags.BackButton).performClick()

        // Assert
        onNodeWithTag(InviteLinksTestTags.Error).assertIsDisplayed()
        assertEquals(1, backs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun managingListsEachActiveLinkAndRevokesTheOneTapped() = runComposeUiTest {
        // Arrange
        val revoked = mutableListOf<String>()
        val links = listOf(InviteLinkItem("a", 6, 0, 1), InviteLinkItem("b", 0, 1, 3))
        setContent {
            VestaTheme { InviteLinksScreen(InviteLinksUiState(created = created, managing = true, links = links), onRevoke = { revoked += it }) }
        }

        // Act
        onAllNodesWithTag(InviteLinksTestTags.RevokeButton)[1].performClick()

        // Assert
        onAllNodesWithTag(InviteLinksTestTags.Row).assertCountEquals(2)
        assertEquals(listOf("b"), revoked)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun managingWithNoLinksSaysSo() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { InviteLinksScreen(InviteLinksUiState(created = created, managing = true)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(InviteLinksTestTags.None).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backWhileManagingReturnsToTheLinkNotOutOfTheScreen() = runComposeUiTest {
        // Arrange
        var stops = 0
        var backs = 0
        setContent {
            VestaTheme {
                InviteLinksScreen(InviteLinksUiState(created = created, managing = true), onStopManaging = { stops++ }, onBack = { backs++ })
            }
        }

        // Act
        onNodeWithTag(InviteLinksTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, stops)
        assertEquals(0, backs)
    }
}
