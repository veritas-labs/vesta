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
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.components.AgentApprovalCardTestTags
import uk.veritaslabs.vesta.ui.theme.VestaTheme

@OptIn(ExperimentalCoroutinesApi::class)
class AgentApprovalHostTest {

    // The host collects with lifecycle awareness, which needs a Main dispatcher the JVM tests don't have.
    @BeforeTest
    fun setMain() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun repository() = AgentAccessRepository(openInMemoryDatabase())

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun drawsNothingWhenNoAgentIsWaiting() = runComposeUiTest {
        // Arrange
        val access = repository()

        // Act
        setContent { VestaTheme { AgentApprovalHost(access) } }

        // Assert
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsThePromptForAWaitingAgent() = runComposeUiTest {
        // Arrange
        val access = repository()
        runBlocking { access.requestApproval("agent-1") }

        // Act
        setContent { VestaTheme { AgentApprovalHost(access) } }
        waitForIdle()

        // Assert
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun allowingApprovesTheAgentAndDismissesThePrompt() = runComposeUiTest {
        // Arrange
        val access = repository()
        runBlocking { access.requestApproval("agent-1") }
        setContent { VestaTheme { AgentApprovalHost(access) } }
        waitForIdle()

        // Act
        onNodeWithTag(AgentApprovalCardTestTags.Allow).performClick()
        waitUntil { runBlocking { access.pending.first().isEmpty() } }

        // Assert
        assertTrue(runBlocking { access.isAllowed("agent-1") })
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun denyingDismissesThePromptWithoutApproving() = runComposeUiTest {
        // Arrange
        val access = repository()
        runBlocking { access.requestApproval("agent-1") }
        setContent { VestaTheme { AgentApprovalHost(access) } }
        waitForIdle()

        // Act
        onNodeWithTag(AgentApprovalCardTestTags.Deny).performClick()
        waitUntil { runBlocking { access.pending.first().isEmpty() } }

        // Assert
        assertFalse(runBlocking { access.isAllowed("agent-1") })
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun queuedAgentsAreOfferedOneAtATimeOldestFirst() = runComposeUiTest {
        // Arrange
        val access = repository()
        runBlocking {
            access.requestApproval("agent-1")
            access.requestApproval("agent-2")
        }
        setContent { VestaTheme { AgentApprovalHost(access) } }
        waitForIdle()

        // Act
        onNodeWithTag(AgentApprovalCardTestTags.Allow).performClick()
        waitUntil { runBlocking { access.pending.first().map { it.agentId } == listOf("agent-2") } }

        // Assert
        assertEquals(true, runBlocking { access.isAllowed("agent-1") })
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertIsDisplayed()
    }
}
