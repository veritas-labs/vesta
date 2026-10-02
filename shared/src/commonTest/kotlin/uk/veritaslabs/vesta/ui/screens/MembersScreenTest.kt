package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.components.HomeNavigationBarTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class MembersScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAnInviteButton() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MembersScreen() } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(MembersTestTags.InviteButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingInviteInvokesOnInviteClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent { VestaTheme { MembersScreen(onInviteClick = { clicks++ }) } }

        // Act
        onNodeWithTag(MembersTestTags.InviteButton).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun marksMembersAsTheSelectedTab() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MembersScreen() } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeNavigationBarTestTags.MembersItem).assertIsSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingHomeInTheNavBarInvokesOnHomeClick() = runComposeUiTest {
        // Arrange
        var clicks = 0
        setContent { VestaTheme { MembersScreen(onHomeClick = { clicks++ }) } }

        // Act
        onNodeWithTag(HomeNavigationBarTestTags.HomeItem).performClick()

        // Assert
        assertEquals(1, clicks)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsOneRowPerMemberWithTheCurrentUserMarked() = runComposeUiTest {
        // Arrange
        val members = listOf(
            MemberListItem("1", "Mark", MemberRole.Admin, isYou = true),
            MemberListItem("2", "Sam", MemberRole.Member),
        )

        // Act
        setContent { VestaTheme { MembersScreen(members = members) } }

        // Assert
        onAllNodesWithTag(MembersTestTags.Row).assertCountEquals(2)
        onNodeWithText("Mark (You)").assertIsDisplayed()
        onNodeWithText("Sam").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun leavingNeedsConfirmation() = runComposeUiTest {
        // Arrange
        var leaves = 0
        setContent { VestaTheme { MembersScreen(onLeaveHome = { leaves++ }) } }

        // Act
        onNodeWithTag(MembersTestTags.LeaveButton).performClick()
        val beforeConfirm = leaves
        onNodeWithTag(MembersTestTags.ConfirmLeave).performClick()

        // Assert
        assertEquals(0, beforeConfirm)
        assertEquals(1, leaves)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cancellingTheLeaveConfirmationDoesNotLeave() = runComposeUiTest {
        // Arrange
        var leaves = 0
        setContent { VestaTheme { MembersScreen(onLeaveHome = { leaves++ }) } }
        onNodeWithTag(MembersTestTags.LeaveButton).performClick()

        // Act
        onNodeWithText("Cancel").performClick()

        // Assert
        assertEquals(0, leaves)
        onNodeWithTag(MembersTestTags.ConfirmLeave).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun explainsWhyTheOnlyAdminCannotLeave() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MembersScreen(leaveBlocked = true) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(MembersTestTags.BlockedDialog).assertIsDisplayed()
    }
}
