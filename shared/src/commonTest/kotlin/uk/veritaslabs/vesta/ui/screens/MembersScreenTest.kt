package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.layout.boundsInRoot
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
import androidx.compose.ui.test.assertContentDescriptionEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.homejoin.JoinRequest
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
    fun theInviteRowComesAfterTheMembersAndMatchesTheirSize() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MembersScreen(members = listOf(admin, sam)) } }

        // Act
        val invite = onNodeWithTag(MembersTestTags.InviteButton).fetchSemanticsNode().boundsInRoot
        val lastMember = onAllNodesWithTag(MembersTestTags.Row)[1].fetchSemanticsNode().boundsInRoot

        // Assert
        assertTrue(invite.top >= lastMember.bottom)
        assertEquals(lastMember.height, invite.height)
        assertEquals(lastMember.width, invite.width)
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

    private val pendingRequest = JoinRequest("home-1", "alex", "Alex", createdAt = 1L, approved = false)
    private val approvedRequest = JoinRequest("home-1", "jo", "Jo", createdAt = 2L, approved = true)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminSeesRequestsToJoinAboveTheMembers() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MembersScreen(members = listOf(admin, sam), joinRequests = listOf(pendingRequest)) } }

        // Act
        val request = onNodeWithTag(MembersTestTags.RequestRow).fetchSemanticsNode().boundsInRoot
        val firstMember = onAllNodesWithTag(MembersTestTags.Row)[0].fetchSemanticsNode().boundsInRoot

        // Assert
        assertTrue(request.bottom <= firstMember.top)
        onNodeWithText("Alex").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anOrdinaryMemberDoesNotSeeRequestsToJoin() = runComposeUiTest {
        // Arrange
        val me = MemberListItem("3", "Pat", MemberRole.Member, isYou = true)
        setContent { VestaTheme { MembersScreen(members = listOf(admin.copy(isYou = false), me), joinRequests = listOf(pendingRequest)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onAllNodesWithTag(MembersTestTags.RequestRow).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun approvingARequestPassesItOn() = runComposeUiTest {
        // Arrange
        var approved: JoinRequest? = null
        setContent { VestaTheme { MembersScreen(members = listOf(admin), joinRequests = listOf(pendingRequest), onApprove = { approved = it }) } }

        // Act
        onNodeWithTag(MembersTestTags.approve("alex")).performClick()

        // Assert
        assertEquals(pendingRequest, approved)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun decliningARequestPassesItOn() = runComposeUiTest {
        // Arrange
        var declined: JoinRequest? = null
        setContent { VestaTheme { MembersScreen(members = listOf(admin), joinRequests = listOf(pendingRequest), onDecline = { declined = it }) } }

        // Act
        onNodeWithTag(MembersTestTags.decline("alex")).performClick()

        // Assert
        assertEquals(pendingRequest, declined)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anApprovedRequestOnlyWaitsForTheirDevice() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MembersScreen(members = listOf(admin), joinRequests = listOf(approvedRequest)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(MembersTestTags.RequestRow).assertIsDisplayed()
        onNodeWithTag(MembersTestTags.approve("jo")).assertDoesNotExist()
        onNodeWithTag(MembersTestTags.decline("jo")).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminsMembersTabCountsTheRequestsStillWaitingForThem() = runComposeUiTest {
        // Arrange
        val second = pendingRequest.copy(memberId = "pat", name = "Pat")
        setContent {
            VestaTheme { MembersScreen(members = listOf(admin), joinRequests = listOf(pendingRequest, second, approvedRequest)) }
        }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeNavigationBarTestTags.MembersBadge, useUnmergedTree = true).assertContentDescriptionEquals("2 waiting to join")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noBadgeShowsWhenEveryRequestIsAlreadyApproved() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MembersScreen(members = listOf(admin), joinRequests = listOf(approvedRequest)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeNavigationBarTestTags.MembersBadge, useUnmergedTree = true).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anOrdinaryMembersTabHasNoBadge() = runComposeUiTest {
        // Arrange
        val me = MemberListItem("3", "Pat", MemberRole.Member, isYou = true)
        setContent { VestaTheme { MembersScreen(members = listOf(admin.copy(isYou = false), me), joinRequests = listOf(pendingRequest)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HomeNavigationBarTestTags.MembersBadge, useUnmergedTree = true).assertDoesNotExist()
    }
}
