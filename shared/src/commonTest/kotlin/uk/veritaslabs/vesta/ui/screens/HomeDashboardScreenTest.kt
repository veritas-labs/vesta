package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.components.HomeNavigationBarTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.x0x.X0xConnectivity

class HomeDashboardScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAPantryTile() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons") } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeDashboardTestTags.PantryTile).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingThePantryTileInvokesOnPantryClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", onPantryClick = { clicks++ }) } }

        // Act
        onNodeWithTag(HomeDashboardTestTags.PantryTile).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAnX0xConnectivityIndicatorInTheAppBar() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", x0xConnectivity = X0xConnectivity.Connected(peers = 23)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeDashboardTestTags.X0xIndicator).assertIsDisplayed()
        onNodeWithText("x0x").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheHomeNameInTheAppBar() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "Flat 4B") } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Flat 4B").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingBackInvokesOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", onBack = { backs++ }) } }

        // Act
        onNodeWithTag(HomeDashboardTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingMembersInTheNavBarInvokesOnMembersClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", onMembersClick = { clicks++ }) } }

        // Act
        onNodeWithTag(HomeNavigationBarTestTags.MembersItem).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.openMenu() = onNodeWithTag(HomeDashboardTestTags.OverflowButton).performClick()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminsOverflowMenuHasEditLeaveAndDelete() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", isAdmin = true) } }

        // Act
        openMenu()

        // Assert
        onNodeWithTag(HomeDashboardTestTags.EditMenuItem).assertIsDisplayed()
        onNodeWithTag(HomeDashboardTestTags.HubMenuItem).assertIsDisplayed()
        onNodeWithTag(HomeDashboardTestTags.LeaveMenuItem).assertIsDisplayed()
        onNodeWithTag(HomeDashboardTestTags.DeleteMenuItem).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aMembersOverflowMenuHasOnlyLeave() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", isAdmin = false) } }

        // Act
        openMenu()

        // Assert
        onNodeWithTag(HomeDashboardTestTags.LeaveMenuItem).assertIsDisplayed()
        onNodeWithTag(HomeDashboardTestTags.EditMenuItem).assertDoesNotExist()
        onNodeWithTag(HomeDashboardTestTags.HubMenuItem).assertDoesNotExist()
        onNodeWithTag(HomeDashboardTestTags.DeleteMenuItem).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun manageHubsInTheMenuInvokesOnHubClick() = runComposeUiTest {
        // Arrange
        var opens = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", isAdmin = true, onHubClick = { opens++ }) } }

        // Act
        openMenu()
        onNodeWithTag(HomeDashboardTestTags.HubMenuItem).performClick()

        // Assert
        assertEquals(1, opens)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editHomeInTheMenuInvokesOnEditHome() = runComposeUiTest {
        // Arrange
        var edits = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", isAdmin = true, onEditHome = { edits++ }) } }

        // Act
        openMenu()
        onNodeWithTag(HomeDashboardTestTags.EditMenuItem).performClick()

        // Assert
        assertEquals(1, edits)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteHomeInTheMenuInvokesOnDeleteHome() = runComposeUiTest {
        // Arrange
        var deletes = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", isAdmin = true, onDeleteHome = { deletes++ }) } }

        // Act
        openMenu()
        onNodeWithTag(HomeDashboardTestTags.DeleteMenuItem).performClick()

        // Assert
        assertEquals(1, deletes)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun leavingNeedsConfirmation() = runComposeUiTest {
        // Arrange
        var leaves = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", onLeaveHome = { leaves++ }) } }

        // Act
        openMenu()
        onNodeWithTag(HomeDashboardTestTags.LeaveMenuItem).performClick()
        val beforeConfirm = leaves
        onNodeWithTag(HomeDashboardTestTags.ConfirmLeave).performClick()

        // Assert
        assertEquals(0, beforeConfirm)
        assertEquals(1, leaves)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cancellingTheLeaveConfirmationDoesNotLeave() = runComposeUiTest {
        // Arrange
        var leaves = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", onLeaveHome = { leaves++ }) } }
        openMenu()
        onNodeWithTag(HomeDashboardTestTags.LeaveMenuItem).performClick()

        // Act
        onNodeWithText("Cancel").performClick()

        // Assert
        assertEquals(0, leaves)
        onNodeWithTag(HomeDashboardTestTags.ConfirmLeave).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun explainsWhyTheOnlyAdminCannotLeave() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", leaveBlocked = true) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeDashboardTestTags.BlockedDialog).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theMembersTabShowsTheNumberOfPeopleWaitingToJoin() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", membersBadge = 2) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeNavigationBarTestTags.MembersBadge, useUnmergedTree = true).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theMembersTabHasNoBadgeByDefault() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons") } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeNavigationBarTestTags.MembersBadge, useUnmergedTree = true).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingTheCalendarTileInvokesOnCalendarClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent { VestaTheme { HomeDashboardScreen(homeName = "The Johnsons", onCalendarClick = { clicks++ }) } }

        // Act
        onNodeWithTag(HomeDashboardTestTags.CalendarTile).performClick()

        // Assert
        assertEquals(1, clicks)
    }
}
