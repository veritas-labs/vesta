package uk.veritaslabs.vesta.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uk.veritaslabs.vesta.ui.theme.VestaTheme

class AgentApprovalCardTest {

    private val agentId = "63ab3172ff7b4afa6717974c9f05946481a0e9b90e9533da8d332933e781d930"

    @Test
    fun abbreviatesALongAgentIdKeepingBothEnds() {
        // Arrange / Act
        val abbreviated = abbreviateAgentId(agentId)

        // Assert
        assertEquals("63ab3172…d930", abbreviated)
    }

    @Test
    fun leavesAShortAgentIdAlone() {
        // Arrange / Act
        val abbreviated = abbreviateAgentId("agent-1")

        // Assert
        assertEquals("agent-1", abbreviated)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsTheCardWithBothChoices() = runComposeUiTest {
        // Arrange
        setContent { VestaTheme { AgentApprovalCard(agentId = agentId) } }

        // Act
        // (no user interaction — checking the initial state)

        // Assert
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertIsDisplayed()
        onNodeWithTag(AgentApprovalCardTestTags.Allow).assertIsDisplayed()
        onNodeWithTag(AgentApprovalCardTestTags.Deny).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingAllowInvokesOnAllowOnly() = runComposeUiTest {
        // Arrange
        var allowed = 0
        var denied = 0
        setContent { VestaTheme { AgentApprovalCard(agentId, onAllow = { allowed++ }, onDeny = { denied++ }) } }

        // Act
        onNodeWithTag(AgentApprovalCardTestTags.Allow).performClick()

        // Assert
        assertEquals(1 to 0, allowed to denied)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingDenyInvokesOnDenyOnly() = runComposeUiTest {
        // Arrange
        var allowed = 0
        var denied = 0
        setContent { VestaTheme { AgentApprovalCard(agentId, onAllow = { allowed++ }, onDeny = { denied++ }) } }

        // Act
        onNodeWithTag(AgentApprovalCardTestTags.Deny).performClick()

        // Assert
        assertEquals(0 to 1, allowed to denied)
    }
}
