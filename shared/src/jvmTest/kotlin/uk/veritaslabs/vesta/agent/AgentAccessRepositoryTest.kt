package uk.veritaslabs.vesta.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.db.openInMemoryDatabase

class AgentAccessRepositoryTest {

    private var clock = 0L

    private fun repository(preApproved: List<String> = emptyList()) =
        AgentAccessRepository(openInMemoryDatabase(), FixedAgentAllowlist(preApproved)) { clock++ }

    @Test
    fun anUnknownAgentIsNotAllowed() = runBlocking {
        // Arrange
        val access = repository()

        // Act
        val allowed = access.isAllowed("a1")

        // Assert
        assertFalse(allowed)
    }

    @Test
    fun aPreApprovedAgentIsAllowedWithoutAnyRow() = runBlocking {
        // Arrange
        val access = repository(preApproved = listOf("a1"))

        // Act / Assert
        assertTrue(access.isAllowed("a1"))
        assertEquals(emptyList(), access.pending.first())
    }

    @Test
    fun aPendingAgentIsNotAllowedUntilApproved() = runBlocking {
        // Arrange
        val access = repository()
        access.requestApproval("a1")

        // Act
        val before = access.isAllowed("a1")
        access.allow("a1")

        // Assert
        assertFalse(before)
        assertTrue(access.isAllowed("a1"))
        assertEquals(emptyList(), access.pending.first())
    }

    @Test
    fun pendingAgentsAreListedOldestFirstAndRequestingTwiceKeepsTheFirstTime() = runBlocking {
        // Arrange
        val access = repository()

        // Act
        access.requestApproval("b")
        access.requestApproval("a")
        access.requestApproval("b")

        // Assert
        assertEquals(listOf("b", "a"), access.pending.first().map { it.agentId })
    }

    @Test
    fun denyDropsAPendingRequestButNeverRevokesAnApprovedAgent() = runBlocking {
        // Arrange
        val access = repository()
        access.requestApproval("pending")
        access.allow("approved")

        // Act
        access.deny("pending")
        access.deny("approved")

        // Assert
        assertEquals(emptyList(), access.pending.first())
        assertFalse(access.isAllowed("pending"))
        assertTrue(access.isAllowed("approved"))
    }

    @Test
    fun requestingApprovalForAnAlreadyApprovedAgentDoesNotDowngradeIt() = runBlocking {
        // Arrange
        val access = repository()
        access.allow("a1")

        // Act
        access.requestApproval("a1")

        // Assert
        assertTrue(access.isAllowed("a1"))
        assertEquals(emptyList(), access.pending.first())
    }
}
