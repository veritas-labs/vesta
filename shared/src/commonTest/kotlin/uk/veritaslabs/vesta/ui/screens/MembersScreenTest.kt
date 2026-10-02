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

    private val admin = MemberListItem("1", "Mark", MemberRole.Admin, isYou = true)
    private val sam = MemberListItem("2", "Sam", MemberRole.Member)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminCanMakeAnotherMemberAnAdmin() = runComposeUiTest {
        // Arrange
        var change: Pair<String, MemberRole>? = null
        setContent {
            VestaTheme { MembersScreen(members = listOf(admin, sam), onSetRole = { m, r -> change = m.memberId to r }) }
        }

        // Act
        onNodeWithText("Sam").performClick()
        onNodeWithTag(MembersTestTags.ConfirmRole).performClick()

        // Assert
        assertEquals("2" to MemberRole.Admin, change)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminCanTakeAdminAwayFromAnotherAdmin() = runComposeUiTest {
        // Arrange
        var change: Pair<String, MemberRole>? = null
        val otherAdmin = sam.copy(role = MemberRole.Admin)
        setContent {
            VestaTheme { MembersScreen(members = listOf(admin, otherAdmin), onSetRole = { m, r -> change = m.memberId to r }) }
        }

        // Act
        onNodeWithText("Sam").performClick()
        onNodeWithTag(MembersTestTags.ConfirmRole).performClick()

        // Assert
        assertEquals("2" to MemberRole.Member, change)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anOrdinaryMemberCannotOpenTheRoleDialog() = runComposeUiTest {
        // Arrange
        val me = MemberListItem("1", "Mark", MemberRole.Member, isYou = true)
        val boss = MemberListItem("2", "Sam", MemberRole.Admin)
        setContent { VestaTheme { MembersScreen(members = listOf(me, boss)) } }

        // Act
        onNodeWithText("Sam").performClick()

        // Assert
        onNodeWithTag(MembersTestTags.ConfirmRole).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminCannotOpenTheRoleDialogOnTheirOwnRow() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MembersScreen(members = listOf(admin, sam)) } }

        // Act
        onNodeWithText("Mark (You)").performClick()

        // Assert
        onNodeWithTag(MembersTestTags.ConfirmRole).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminCanRemoveAMemberAfterConfirming() = runComposeUiTest {
        // Arrange
        var removed: String? = null
        setContent { VestaTheme { MembersScreen(members = listOf(admin, sam), onRemoveMember = { removed = it.memberId }) } }

        // Act
        onNodeWithText("Sam").performClick()
        onNodeWithTag(MembersTestTags.RemoveMember).performClick()
        val beforeConfirm = removed
        onNodeWithTag(MembersTestTags.ConfirmRemove).performClick()

        // Assert
        assertEquals(null, beforeConfirm)
        assertEquals("2", removed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cancellingTheRemovalDoesNotRemoveAnyone() = runComposeUiTest {
        // Arrange
        var removed: String? = null
        setContent { VestaTheme { MembersScreen(members = listOf(admin, sam), onRemoveMember = { removed = it.memberId }) } }
        onNodeWithText("Sam").performClick()
        onNodeWithTag(MembersTestTags.RemoveMember).performClick()

        // Act
        onNodeWithText("Cancel").performClick()

        // Assert
        assertEquals(null, removed)
        onNodeWithTag(MembersTestTags.ConfirmRemove).assertDoesNotExist()
    }
}
