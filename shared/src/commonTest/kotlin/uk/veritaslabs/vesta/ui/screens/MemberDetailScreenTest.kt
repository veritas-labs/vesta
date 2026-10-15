package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.home.MemberRole
import uk.veritaslabs.vesta.home.MemberListItem
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class MemberDetailScreenTest {

    private val sam = MemberListItem("2", "Sam", MemberRole.Member)
    private val agentA = "9f2c41d07a3be58c1d6e0f4a8b7c2d3e5f60718293a4b5c6d7e8f90a1b2c3d4e"
    private val agentB = "1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809"

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheMembersNameAndRole() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MemberDetailScreen(sam.copy(role = MemberRole.Admin), agents = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Sam").assertIsDisplayed()
        onNodeWithText("Admin").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun listsEveryAgentOfTheMember() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MemberDetailScreen(sam, agents = listOf(MemberAgent(agentA), MemberAgent(agentB))) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(MemberDetailTestTags.agent(agentA)).assertIsDisplayed()
        onNodeWithTag(MemberDetailTestTags.agent(agentB)).assertIsDisplayed()
        onNodeWithTag(MemberDetailTestTags.AgentsEmpty).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAnAgentsMachineLastSeenAndLastSynced() = runComposeUiTest {
        // Arrange
        val agent = MemberAgent(agentA, machineId = "11d167d8442575e837eb45fc4859155ffaccc149a50bf6cf22268853175636c3", lastSeenMsAgo = 42_000, lastSyncedMsAgo = 180_000)
        setContent { VestaTheme { MemberDetailScreen(sam, agents = listOf(agent)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("11d167d8…175636c3").assertIsDisplayed()
        onNodeWithText("42 s ago").assertIsDisplayed()
        onNodeWithText("3 min ago").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheNameADeviceGivesItself() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MemberDetailScreen(sam, agents = listOf(MemberAgent(agentA, name = "Sam's Pixel 9"))) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Sam's Pixel 9").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun groupsAgentsUnderTheirDeviceNameAndLabelsTheAgentId() = runComposeUiTest {
        // Arrange
        val agents = listOf(MemberAgent(agentA, name = "Pixel"), MemberAgent(agentB, name = "Pixel"), MemberAgent("c".repeat(64)))
        setContent { VestaTheme { MemberDetailScreen(sam, agents = agents) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(MemberDetailTestTags.device("Pixel")).assertIsDisplayed()
        onNodeWithTag(MemberDetailTestTags.device(null)).assertIsDisplayed()
        onNodeWithText("Unnamed device").assertIsDisplayed()
        onAllNodesWithText("Agent").assertCountEquals(3)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAgentNeverSyncedSaysSoAndHidesWhatIsUnknown() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MemberDetailScreen(sam, agents = listOf(MemberAgent(agentA))) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithText("Never").assertIsDisplayed()
        onNodeWithText("Machine").assertDoesNotExist()
        onNodeWithText("Last seen").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun saysSoWhenTheMemberHasNoKnownAgents() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MemberDetailScreen(sam, agents = emptyList()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(MemberDetailTestTags.AgentsEmpty).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theBackArrowInvokesOnBack() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { MemberDetailScreen(sam, agents = emptyList(), onBack = { backs++ }) } }

        // Act
        onNodeWithTag(MemberDetailTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminCanMakeAnotherMemberAnAdmin() = runComposeUiTest {
        // Arrange
        var change: Pair<String, MemberRole>? = null
        setContent {
            VestaTheme {
                MemberDetailScreen(sam, agents = emptyList(), canManage = true, onSetRole = { m, r -> change = m.memberId to r })
            }
        }

        // Act
        onNodeWithTag(MemberDetailTestTags.Manage).performClick()
        onNodeWithTag(MemberDetailTestTags.ConfirmRole).performClick()

        // Assert
        assertEquals("2" to MemberRole.Admin, change)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminCanTakeAdminAwayFromAnotherAdmin() = runComposeUiTest {
        // Arrange
        var change: Pair<String, MemberRole>? = null
        setContent {
            VestaTheme {
                MemberDetailScreen(
                    sam.copy(role = MemberRole.Admin), agents = emptyList(), canManage = true,
                    onSetRole = { m, r -> change = m.memberId to r },
                )
            }
        }

        // Act
        onNodeWithTag(MemberDetailTestTags.Manage).performClick()
        onNodeWithTag(MemberDetailTestTags.ConfirmRole).performClick()

        // Assert
        assertEquals("2" to MemberRole.Member, change)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anOrdinaryMemberCannotManageAnyone() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { MemberDetailScreen(sam, agents = emptyList(), canManage = false) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(MemberDetailTestTags.Manage).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminCannotManageThemselves() = runComposeUiTest {
        // Arrange
        val me = MemberListItem("1", "Mark", MemberRole.Admin, isYou = true)
        setContent { VestaTheme { MemberDetailScreen(me, agents = emptyList(), canManage = true) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(MemberDetailTestTags.Manage).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAdminCanRemoveAMemberAfterConfirming() = runComposeUiTest {
        // Arrange
        var removed: String? = null
        setContent {
            VestaTheme {
                MemberDetailScreen(sam, agents = emptyList(), canManage = true, onRemoveMember = { removed = it.memberId })
            }
        }

        // Act
        onNodeWithTag(MemberDetailTestTags.Manage).performClick()
        onNodeWithTag(MemberDetailTestTags.RemoveMember).performClick()
        val beforeConfirm = removed
        onNodeWithTag(MemberDetailTestTags.ConfirmRemove).performClick()

        // Assert
        assertEquals(null, beforeConfirm)
        assertEquals("2", removed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cancellingTheRemovalDoesNotRemoveAnyone() = runComposeUiTest {
        // Arrange
        var removed: String? = null
        setContent {
            VestaTheme {
                MemberDetailScreen(sam, agents = emptyList(), canManage = true, onRemoveMember = { removed = it.memberId })
            }
        }
        onNodeWithTag(MemberDetailTestTags.Manage).performClick()
        onNodeWithTag(MemberDetailTestTags.RemoveMember).performClick()

        // Act
        onNodeWithText("Cancel").performClick()

        // Assert
        assertEquals(null, removed)
        onNodeWithTag(MemberDetailTestTags.ConfirmRemove).assertDoesNotExist()
    }
}
