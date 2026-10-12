package uk.veritaslabs.vesta.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase

class AssistantRepositoryTest {

    private var clock = 0L
    private val database: VestaDatabase = openInMemoryDatabase()

    private fun repository(preApproved: List<String> = emptyList()) =
        AssistantRepository(database, FixedAgentAllowlist(preApproved)) { clock++ }

    private suspend fun home(id: String) = database.homeQueries.insert(id, id, "family", 0)

    private fun pantry(homeId: String, policy: GrantPolicy) = AssistantGrant(homeId, AssistantCapability.PantryAdd, policy)

    @Test
    fun anUnknownAgentIsNotActive() = runBlocking {
        // Arrange
        val assistants = repository()

        // Act
        val active = assistants.isActive("a1")

        // Assert
        assertFalse(active)
    }

    @Test
    fun aPendingAgentIsNotActiveUntilSaved() = runBlocking {
        // Arrange
        val assistants = repository()
        assistants.requestApproval("a1")
        assistants.requestApproval("a1")

        // Act
        val before = assistants.isActive("a1")
        assistants.save("a1", "Grok Bot", emptyList())

        // Assert
        assertFalse(before)
        assertTrue(assistants.isActive("a1"))
        assertEquals(emptyList(), assistants.pending.first())
        assertEquals(listOf("Grok Bot"), assistants.assistants.first().map { it.name })
    }

    @Test
    fun aPreApprovedAgentIsAllowedEverywhereWithoutAnyRow() = runBlocking {
        // Arrange
        val assistants = repository(preApproved = listOf("a1"))

        // Act / Assert
        assertTrue(assistants.isActive("a1"))
        assertNull(assistants.visibleHomes("a1"))
        assertEquals(AccessDecision.Allow, assistants.decide("a1", "any-home", AssistantCapability.PantryAdd, "c1"))
        assertEquals(emptyList(), assistants.assistants.first())
    }

    @Test
    fun nothingIsPermittedWithoutAGrant() = runBlocking {
        // Arrange
        home("h1")
        val assistants = repository()
        assistants.save("a1", "", emptyList())

        // Act
        val decision = assistants.decide("a1", "h1", AssistantCapability.PantryAdd, "c1")

        // Assert
        assertEquals(AccessDecision.NotPermitted, decision)
        assertEquals(emptySet(), assistants.visibleHomes("a1"))
    }

    @Test
    fun anAutoGrantAllowsAndOnlyForItsOwnHome() = runBlocking {
        // Arrange
        home("h1"); home("h2")
        val assistants = repository()
        assistants.save("a1", "", listOf(pantry("h1", GrantPolicy.Auto)))

        // Act / Assert
        assertEquals(AccessDecision.Allow, assistants.decide("a1", "h1", AssistantCapability.PantryAdd, "c1"))
        assertEquals(AccessDecision.NotPermitted, assistants.decide("a1", "h2", AssistantCapability.PantryAdd, "c1"))
    }

    @Test
    fun anyGrantOnAHomeMakesTheHomeVisible() = runBlocking {
        // Arrange
        home("h1"); home("h2")
        val assistants = repository()

        // Act
        assistants.save("a1", "", listOf(pantry("h1", GrantPolicy.Ask)))

        // Assert
        assertEquals(setOf("h1"), assistants.visibleHomes("a1"))
    }

    @Test
    fun anAskGrantAsksUntilAllowOnceAndThenAllowsThatCommandOnce() = runBlocking {
        // Arrange
        home("h1")
        val assistants = repository()
        assistants.save("a1", "", listOf(pantry("h1", GrantPolicy.Ask)))
        assistants.hold("a1", "h1", AssistantCapability.PantryAdd, "c1", "Add 1 × Milk to the Pantry")
        val held = assistants.pendingCommands.value.single()

        // Act
        assistants.allowOnce(held)

        // Assert
        assertEquals(emptyList(), assistants.pendingCommands.value)
        assertEquals(AccessDecision.Ask, assistants.decide("a1", "h1", AssistantCapability.PantryAdd, "c2"))
        assertEquals(AccessDecision.Allow, assistants.decide("a1", "h1", AssistantCapability.PantryAdd, "c1"))
        assertEquals(AccessDecision.Ask, assistants.decide("a1", "h1", AssistantCapability.PantryAdd, "c1"))
    }

    @Test
    fun holdingTheSameCommandTwiceQueuesItOnce() = runBlocking {
        // Arrange
        val assistants = repository()

        // Act
        assistants.hold("a1", "h1", AssistantCapability.PantryAdd, "c1", "s")
        assistants.hold("a1", "h1", AssistantCapability.PantryAdd, "c1", "s")

        // Assert
        assertEquals(1, assistants.pendingCommands.value.size)
    }

    @Test
    fun savingReplacesTheGrantsAndKeepsTheOrderOfAddition() = runBlocking {
        // Arrange
        home("h1"); home("h2")
        val assistants = repository()
        assistants.save("a1", "First", listOf(pantry("h1", GrantPolicy.Auto)))
        val addedAt = assistants.assistant("a1")!!.addedAt

        // Act
        assistants.save("a1", " Renamed ", listOf(pantry("h2", GrantPolicy.Ask)))

        // Assert
        assertEquals(
            setOf(pantry("h2", GrantPolicy.Ask), AssistantGrant("h2", AssistantCapability.ListHomes, GrantPolicy.Auto)),
            assistants.grants("a1").first().toSet(),
        )
        assertEquals("Renamed", assistants.assistant("a1")!!.name)
        assertEquals(addedAt, assistants.assistant("a1")!!.addedAt)
    }

    @Test
    fun removingAnAssistantRemovesItsGrantsActivityAndHeldCommands() = runBlocking {
        // Arrange
        home("h1")
        val assistants = repository()
        assistants.save("a1", "", listOf(pantry("h1", GrantPolicy.Ask)))
        assistants.recordActivity("a1", "h1", AssistantCapability.PantryAdd, "c1", ActivityOutcome.Applied, "did it")
        assistants.hold("a1", "h1", AssistantCapability.PantryAdd, "c2", "s")

        // Act
        assistants.remove("a1")

        // Assert
        assertFalse(assistants.isActive("a1"))
        assertEquals(emptyList(), assistants.grants("a1").first())
        assertEquals(emptyList(), assistants.activity("a1").first())
        assertEquals(emptyList(), assistants.pendingCommands.value)
    }

    @Test
    fun deletingAHomeRemovesGrantsOnIt() = runBlocking {
        // Arrange
        home("h1"); home("h2")
        val assistants = repository()
        assistants.save("a1", "", listOf(pantry("h1", GrantPolicy.Auto), pantry("h2", GrantPolicy.Auto)))

        // Act
        database.homeQueries.deleteById("h1")

        // Assert
        assertEquals(setOf("h2"), assistants.visibleHomes("a1"))
    }

    @Test
    fun activityIsNewestFirstAndCappedPerAssistant() = runBlocking {
        // Arrange
        val assistants = repository()
        assistants.save("a1", "", emptyList())
        assistants.save("a2", "", emptyList())

        // Act
        repeat(AssistantActivityLimit.toInt() + 5) {
            assistants.recordActivity("a1", null, AssistantCapability.ListHomes, "c$it", ActivityOutcome.Applied, "n$it")
        }
        assistants.recordActivity("a2", null, AssistantCapability.ListHomes, "x", ActivityOutcome.Rejected, "other")

        // Assert
        val activity = assistants.activity("a1").first()
        assertEquals(AssistantActivityLimit.toInt(), activity.size)
        assertEquals("n${AssistantActivityLimit + 4}", activity.first().summary)
        assertEquals(1, assistants.activity("a2").first().size)
        assertEquals(ActivityOutcome.Rejected, assistants.activity("a2").first().single().outcome)
    }

    @Test
    fun activityOfAnAgentWithoutARowIsNotRecorded() = runBlocking {
        // Arrange
        val assistants = repository(preApproved = listOf("a1"))

        // Act
        assistants.recordActivity("a1", null, AssistantCapability.ListHomes, "c1", ActivityOutcome.Applied, "s")

        // Assert
        assertEquals(emptyList(), assistants.activity("a1").first())
    }

    @Test
    fun denyingAPendingAgentForgetsItButNotAnActiveOne() = runBlocking {
        // Arrange
        val assistants = repository()
        assistants.requestApproval("pending")
        assistants.save("active", "", emptyList())

        // Act
        assistants.deny("pending")
        assistants.deny("active")

        // Assert
        assertEquals(listOf("active"), assistants.assistants.first().map { it.agentId })
    }
}
