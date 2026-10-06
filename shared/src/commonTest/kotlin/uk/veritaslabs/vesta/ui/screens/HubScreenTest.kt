package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.homegroup.HubItem
import uk.veritaslabs.vesta.homegroup.HubUiState
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class HubScreenTest {

    private val commands = "x0x group join invite:g\nx0x group store create g mailbox"
    private val agent = "9f2c41d07a3be58c1d6e0f4a8b7c2d3e5f60718293a4b5c6d7e8f90a1b2c3d4e"

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsProgressWhileLoading() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HubScreen(HubUiState()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HubTestTags.Progress).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun withNoHubItShowsTheCommandsToCopyAndWaitsForTheMachine() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HubScreen(HubUiState(loading = false, commands = commands)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HubTestTags.Commands).assertTextContains("x0x group join invite:g", substring = true)
        onNodeWithTag(HubTestTags.CopyButton).assertIsDisplayed()
        onNodeWithTag(HubTestTags.Waiting).assertIsDisplayed()
        onAllNodesWithTag(HubTestTags.CandidateRow).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anAgentThatJoinedIsOfferedAndChoosingItReportsItsId() = runComposeUiTest {
        // Arrange
        val chosen = mutableListOf<String>()
        setContent {
            VestaTheme { HubScreen(HubUiState(loading = false, commands = commands, candidates = listOf(agent)), onUseAsHub = { chosen += it }) }
        }

        // Act
        onNodeWithTag(HubTestTags.UseButton).performClick()

        // Assert
        onAllNodesWithTag(HubTestTags.CandidateRow).assertCountEquals(1)
        assertEquals(listOf(agent), chosen)
        onAllNodesWithTag(HubTestTags.Waiting).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aRecordedHubShowsWhetherItIsConnected() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HubScreen(HubUiState(loading = false, hub = HubItem(agent, inGroup = false))) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HubTestTags.HubCard).assertIsDisplayed()
        onNodeWithTag(HubTestTags.Status).assertIsDisplayed()
        onAllNodesWithTag(HubTestTags.Commands).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun removingTheHubAsksFirstAndOnlyThenRemoves() = runComposeUiTest {
        // Arrange
        var removed = 0
        setContent { VestaTheme { HubScreen(HubUiState(loading = false, hub = HubItem(agent, inGroup = true)), onRemove = { removed++ }) } }

        // Act
        onNodeWithTag(HubTestTags.RemoveButton).performClick()
        val beforeConfirming = removed
        onNodeWithTag(HubTestTags.ConfirmRemove).performClick()

        // Assert
        assertEquals(0, beforeConfirming)
        assertEquals(1, removed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun saysWhyNothingCanBeShownAndWhenAnActionFailed() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HubScreen(HubUiState(loading = false, unavailable = true, failed = true)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HubTestTags.Unavailable).assertIsDisplayed()
        onNodeWithTag(HubTestTags.Failure).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backLeavesTheScreen() = runComposeUiTest {
        // Arrange
        var backs = 0
        setContent { VestaTheme { HubScreen(HubUiState(loading = false, unavailable = true), onBack = { backs++ }) } }

        // Act
        onNodeWithTag(HubTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, backs)
    }

    @Test
    fun anAgentIdIsShortenedToEnoughToTellMachinesApart() {
        // Arrange
        val short = "agent-hub"

        // Act
        val shortened = shortAgentId(agent)

        // Assert
        assertEquals("9f2c41d0…90a1b2c3".length, shortened.length - 0)
        assertEquals("9f2c41d0…" + agent.takeLast(8), shortened)
        assertEquals(short, shortAgentId(short))
    }
}
