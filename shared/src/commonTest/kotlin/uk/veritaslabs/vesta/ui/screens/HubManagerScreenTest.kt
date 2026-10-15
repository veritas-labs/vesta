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
import uk.veritaslabs.vesta.homegroup.HubItem
import uk.veritaslabs.vesta.homegroup.HubUiState
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class HubManagerScreenTest {

    private val agent = "9f2c41d07a3be58c1d6e0f4a8b7c2d3e5f60718293a4b5c6d7e8f90a1b2c3d4e"
    private val agent2 = "3b7e90a1c5d2486f1e0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e"

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsProgressWhileLoading() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HubManagerScreen(HubUiState()) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HubManagerTestTags.Progress).assertIsDisplayed()
        onNodeWithTag(HubManagerTestTags.HowButton).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun seeActivityLinkIsOfferedAndInvokesItsCallback() = runComposeUiTest {
        // Arrange
        var opens = 0
        setContent { VestaTheme { HubManagerScreen(HubUiState(loading = false), onSeeActivity = { opens++ }) } }

        // Act
        onNodeWithTag(HubManagerTestTags.SeeActivity).assertIsDisplayed()
        onNodeWithTag(HubManagerTestTags.SeeActivity).performClick()

        // Assert
        assertEquals(1, opens)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun withNoHubsShowsTheEmptyStateAndTheTutorialButton() = runComposeUiTest {
        // Arrange
        var setUps = 0
        setContent { VestaTheme { HubManagerScreen(HubUiState(loading = false), onSetUpHub = { setUps++ }) } }

        // Act
        onNodeWithTag(HubManagerTestTags.SetupButton).performClick()

        // Assert
        onNodeWithTag(HubManagerTestTags.EmptyState).assertIsDisplayed()
        onNodeWithTag(HubManagerTestTags.HowButton).assertIsDisplayed()
        onAllNodesWithTag(HubManagerTestTags.AddHub).assertCountEquals(0)
        assertEquals(1, setUps)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun whenUnavailableTheEmptyStateExplainsAndOffersNoSetup() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { HubManagerScreen(HubUiState(loading = false, unavailable = true)) } }

        // Act
        // (no user interaction — checking the screen's initial state)

        // Assert
        onNodeWithTag(HubManagerTestTags.Unavailable).assertIsDisplayed()
        onAllNodesWithTag(HubManagerTestTags.SetupButton).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun listsEachHubAndEndsWithThePlusButton() = runComposeUiTest {
        // Arrange
        var adds = 0
        val state = HubUiState(loading = false, hubs = listOf(HubItem(agent, inGroup = true), HubItem(agent2, inGroup = false)))
        setContent { VestaTheme { HubManagerScreen(state, onAddHub = { adds++ }) } }

        // Act
        onNodeWithTag(HubManagerTestTags.AddHub).performClick()

        // Assert
        onAllNodesWithTag(HubTestTags.HubCard).assertCountEquals(2)
        onAllNodesWithTag(HubManagerTestTags.EmptyState).assertCountEquals(0)
        assertEquals(1, adds)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tutorialButtonIsOfferedWithHubsToo() = runComposeUiTest {
        // Arrange
        var opens = 0
        val state = HubUiState(loading = false, hubs = listOf(HubItem(agent, inGroup = true)))
        setContent { VestaTheme { HubManagerScreen(state, onHowHubsWork = { opens++ }) } }

        // Act
        onNodeWithTag(HubManagerTestTags.HowButton).performClick()

        // Assert
        assertEquals(1, opens)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun removingAHubAsksFirstThenReportsItsId() = runComposeUiTest {
        // Arrange
        val removed = mutableListOf<String>()
        val state = HubUiState(loading = false, hubs = listOf(HubItem(agent, inGroup = true)))
        setContent { VestaTheme { HubManagerScreen(state, onRemove = { removed += it }) } }

        // Act
        onNodeWithTag(HubTestTags.RemoveButton).performClick()
        onNodeWithTag(HubTestTags.ConfirmRemove).performClick()

        // Assert
        assertEquals(listOf(agent), removed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backArrowLeaves() = runComposeUiTest {
        // Arrange
        var left = 0
        setContent { VestaTheme { HubManagerScreen(HubUiState(loading = false), onBack = { left++ }) } }

        // Act
        onNodeWithTag(HubManagerTestTags.BackButton).performClick()

        // Assert
        assertEquals(1, left)
    }
}
