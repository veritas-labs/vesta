package uk.veritaslabs.vesta.agent

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.kits.x0x.FakeX0xClient
import uk.veritaslabs.kits.x0x.X0xDirectMessage

/** ADR-027: what an assistant may do depends on its grants, per Home, per capability. */
class AssistantPermissionsTest {

    private val agent = "agent-1"

    private class Fixture {
        val sources = mapOf(
            "home-1" to PantryDocumentSource(Document.load(newPantryDocument())),
            "home-2" to PantryDocumentSource(Document.load(newPantryDocument())),
        )
        val client = FakeX0xClient()
        val database = openInMemoryDatabase()
        val assistants = AssistantRepository(database)
        private var nextId = 0
        val handler = PantryCommandHandler(
            resolveSource = { sources[it] },
            listHomes = { listOf(HomeRef("home-1", "The Johnsons"), HomeRef("home-2", "Flat 4")) },
            access = assistants,
            commandLog = DatabaseCommandLog(database),
            client = client,
            newId = { "urn:uuid:reply-${nextId++}" },
            log = {},
        )

        init {
            runBlocking {
                database.homeQueries.insert("home-1", "The Johnsons", "family", 0)
                database.homeQueries.insert("home-2", "Flat 4", "family", 0)
            }
        }

        val replies: List<JsonObject> get() = client.sent.map { Json.parseToJsonElement(it.payload.decodeToString()).jsonObject }
    }

    private fun addCommand(id: String = "urn:uuid:c1", home: String = "home-1") = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Add", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:$home:pantry"},
         "object": {"type": "schema:TypeAndQuantityNode", "schema:amountOfThisGood": 2,
                    "schema:typeOfGood": {"type": "schema:Product", "schema:name": "Milk"}}}
    """.trimIndent()

    private fun listHomes(id: String = "urn:uuid:l1") = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Read", "id": "$id", "object": {"type": "Collection", "id": "urn:vesta:homes"}}
    """.trimIndent()

    private fun message(payload: String) = X0xDirectMessage(
        sender = agent,
        payload = Base64.Default.encode(payload.encodeToByteArray()),
        verified = true,
        trustDecision = "accept",
    )

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private fun grant(home: String, policy: GrantPolicy) = AssistantGrant(home, AssistantCapability.PantryAdd, policy)

    @Test
    fun anAutoGrantAppliesTheAddAndRecordsIt() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", listOf(grant("home-1", GrantPolicy.Auto)))

        // Act
        f.handler.handle(message(addCommand()))

        // Assert
        assertEquals("Accept", f.replies.single().text("type"))
        assertEquals(2, f.sources.getValue("home-1").contents.first().items.single().stockCount)
        val entry = f.assistants.activity(agent).first().single()
        assertEquals(ActivityOutcome.Applied, entry.outcome)
        assertEquals("home-1", entry.homeId)
        assertEquals("Add 2 × Milk to the Pantry", entry.summary)
    }

    @Test
    fun anAddToAHomeWithoutAGrantIsRejectedAndNothingChanges() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", listOf(grant("home-1", GrantPolicy.Auto)))

        // Act
        f.handler.handle(message(addCommand(home = "home-2")))

        // Assert
        assertEquals(NotPermittedSummary, f.replies.single().text("summary"))
        assertEquals(emptyList(), f.sources.getValue("home-2").contents.first().items)
        assertEquals(ActivityOutcome.Rejected, f.assistants.activity(agent).first().single().outcome)
    }

    @Test
    fun aHomeThatDoesNotExistLooksTheSameAsOneWithoutAGrant() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", listOf(grant("home-1", GrantPolicy.Auto)))

        // Act
        f.handler.handle(message(addCommand(home = "no-such-home")))

        // Assert
        assertEquals(NotPermittedSummary, f.replies.single().text("summary"))
    }

    @Test
    fun listHomesOnlyNamesTheHomesTheAssistantMaySee() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", listOf(grant("home-1", GrantPolicy.Auto)))

        // Act
        f.handler.handle(message(listHomes()))

        // Assert
        val items = f.replies.single().getValue("result").jsonObject.getValue("items").jsonArray
        assertEquals(listOf("home-1"), items.map { it.jsonObject.text("schema:identifier") })
    }

    @Test
    fun anAssistantWithNoGrantsSeesNoHomesAndMayNotAdd() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", emptyList())

        // Act
        f.handler.handle(message(listHomes()))
        f.handler.handle(message(addCommand()))

        // Assert
        assertEquals(0, f.replies[0].getValue("result").jsonObject.getValue("totalItems").jsonPrimitive.content.toInt())
        assertEquals(NotPermittedSummary, f.replies[1].text("summary"))
    }

    @Test
    fun anAskGrantHoldsTheCommandAndAppliesItOnlyAfterAllowOnce() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", listOf(grant("home-1", GrantPolicy.Ask)))

        // Act
        f.handler.handle(message(addCommand()))
        val held = f.assistants.pendingCommands.value.single()
        val heldReply = f.replies.single()
        val itemsWhileHeld = f.sources.getValue("home-1").contents.first().items
        f.assistants.allowOnce(held)
        f.handler.handle(message(addCommand()))

        // Assert
        assertEquals(WaitingForApprovalSummary, heldReply.text("summary"))
        assertEquals(emptyList(), itemsWhileHeld)
        assertEquals("Accept", f.replies.last().text("type"))
        assertEquals(2, f.sources.getValue("home-1").contents.first().items.single().stockCount)
        assertEquals(1, f.assistants.activity(agent).first().size)
    }

    @Test
    fun anAskGrantAsksAgainForTheNextCommand() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", listOf(grant("home-1", GrantPolicy.Ask)))
        f.handler.handle(message(addCommand(id = "urn:uuid:c1")))
        f.assistants.allowOnce(f.assistants.pendingCommands.value.single())
        f.handler.handle(message(addCommand(id = "urn:uuid:c1")))

        // Act
        f.handler.handle(message(addCommand(id = "urn:uuid:c2")))

        // Assert
        assertEquals(listOf("urn:uuid:c2"), f.assistants.pendingCommands.value.map { it.commandId })
        assertEquals(2, f.sources.getValue("home-1").contents.first().items.single().stockCount)
    }

    @Test
    fun aGrantForOneCapabilityDoesNotCoverAnother() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", emptyList())
        runBlocking {
            f.database.assistantQueries.upsertGrant(agent, "home-1", AssistantCapability.ListHomes.name, GrantPolicy.Auto.name, 0)
        }

        // Act
        f.handler.handle(message(addCommand()))

        // Assert
        assertEquals(NotPermittedSummary, f.replies.single().text("summary"))
    }

    @Test
    fun aRemovedAssistantIsAskedAboutAgain() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", listOf(grant("home-1", GrantPolicy.Auto)))
        f.assistants.remove(agent)

        // Act
        f.handler.handle(message(addCommand()))

        // Assert
        assertEquals(NotApprovedSummary, f.replies.single().text("summary"))
        assertEquals(listOf(agent), f.assistants.pending.first().map { it.agentId })
        assertEquals(emptyList(), f.sources.getValue("home-1").contents.first().items)
    }

    @Test
    fun aRepeatedCommandIsNotRecordedTwice() = runBlocking {
        // Arrange
        val f = Fixture()
        f.assistants.save(agent, "Grok Bot", listOf(grant("home-1", GrantPolicy.Auto)))

        // Act
        f.handler.handle(message(addCommand()))
        f.handler.handle(message(addCommand()))

        // Assert
        assertEquals(1, f.assistants.activity(agent).first().size)
    }
}
