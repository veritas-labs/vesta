package uk.veritaslabs.vesta.agent

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.components.AgentApprovalCardTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalCoroutinesApi::class)
class AgentApprovalHostTest {

    // The host collects with lifecycle awareness, which needs a Main dispatcher the JVM tests don't have.
    @BeforeTest
    fun setMain() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun repository() = AssistantRepository(openInMemoryDatabase())

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun drawsNothingWhenNoAgentIsWaiting() = runComposeUiTest {
        // Arrange
        val assistants = repository()

        // Act
        setContent { VestaTheme { AgentApprovalHost(assistants, onReview = {}) } }

        // Assert
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsThePromptForAWaitingAgent() = runComposeUiTest {
        // Arrange
        val assistants = repository()
        runBlocking { assistants.requestApproval("agent-1") }

        // Act
        setContent { VestaTheme { AgentApprovalHost(assistants, onReview = {}) } }
        waitForIdle()

        // Assert
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reviewingOffersTheAgentToTheReviewScreenWithoutAllowingIt() = runComposeUiTest {
        // Arrange
        val assistants = repository()
        runBlocking { assistants.requestApproval("agent-1") }
        val reviewed = mutableListOf<String>()
        setContent { VestaTheme { AgentApprovalHost(assistants, onReview = { reviewed += it }) } }
        waitForIdle()

        // Act
        onNodeWithTag(AgentApprovalCardTestTags.Allow).performClick()

        // Assert
        assertEquals(listOf("agent-1"), reviewed)
        assertFalse(runBlocking { assistants.isActive("agent-1") })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun denyingDismissesThePromptWithoutApproving() = runComposeUiTest {
        // Arrange
        val assistants = repository()
        runBlocking { assistants.requestApproval("agent-1") }
        setContent { VestaTheme { AgentApprovalHost(assistants, onReview = {}) } }
        waitForIdle()

        // Act
        onNodeWithTag(AgentApprovalCardTestTags.Deny).performClick()
        waitUntil { runBlocking { assistants.pending.first().isEmpty() } }

        // Assert
        assertFalse(runBlocking { assistants.isActive("agent-1") })
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aHeldCommandOffersAllowOnceAndAppliesOnlyThatCommand() = runComposeUiTest {
        // Arrange
        val database = openInMemoryDatabase()
        val assistants = AssistantRepository(database)
        runBlocking {
            database.homeQueries.insert("home-1", "The Johnsons", "family", 0)
            assistants.save("agent-1", "Grok Bot", listOf(AssistantGrant("home-1", AssistantCapability.PantryAdd, GrantPolicy.Ask)))
            assistants.hold("agent-1", "home-1", AssistantCapability.PantryAdd, "c1", "Add 2 × Milk to the Pantry")
        }
        setContent { VestaTheme { AgentApprovalHost(assistants, onReview = {}) } }
        waitForIdle()

        // Act
        onNodeWithTag(AgentApprovalCardTestTags.Allow).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
        runBlocking {
            assertEquals(AccessDecision.Allow, assistants.decide("agent-1", "home-1", AssistantCapability.PantryAdd, "c1"))
            assertEquals(AccessDecision.Ask, assistants.decide("agent-1", "home-1", AssistantCapability.PantryAdd, "c1"))
            assertEquals(AccessDecision.Ask, assistants.decide("agent-1", "home-1", AssistantCapability.PantryAdd, "c2"))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun denyingAHeldCommandDropsItWithoutAllowingIt() = runComposeUiTest {
        // Arrange
        val database = openInMemoryDatabase()
        val assistants = AssistantRepository(database)
        runBlocking {
            database.homeQueries.insert("home-1", "The Johnsons", "family", 0)
            assistants.save("agent-1", "Grok Bot", listOf(AssistantGrant("home-1", AssistantCapability.PantryAdd, GrantPolicy.Ask)))
            assistants.hold("agent-1", "home-1", AssistantCapability.PantryAdd, "c1", "Add 2 × Milk to the Pantry")
        }
        setContent { VestaTheme { AgentApprovalHost(assistants, onReview = {}) } }
        waitForIdle()

        // Act
        onNodeWithTag(AgentApprovalCardTestTags.Deny).performClick()
        waitForIdle()

        // Assert
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
        assertEquals(emptyList(), assistants.pendingCommands.value)
        assertEquals(AccessDecision.Ask, runBlocking { assistants.decide("agent-1", "home-1", AssistantCapability.PantryAdd, "c1") })
    }
}
