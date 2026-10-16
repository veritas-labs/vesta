package uk.veritaslabs.vesta.agent

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.automerge.readPantryItem
import uk.veritaslabs.vesta.automerge.upsertPantryItem
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.kits.x0x.testing.FakeX0xClient
import uk.veritaslabs.kits.x0x.X0xApiException
import uk.veritaslabs.kits.x0x.X0xDirectMessage

class PantryCommandHandlerTest {

    private val agent = "agent-1"

    private class Fixture(allowed: List<String>) {
        val source = PantryDocumentSource(Document.load(newPantryDocument()))
        val client = FakeX0xClient()
        val database = openInMemoryDatabase()
        val commandLog = DatabaseCommandLog(database)
        val access = AssistantRepository(database, FixedAgentAllowlist(allowed))
        private var nextId = 0
        val logged = mutableListOf<String>()
        var homes = listOf(HomeRef("home-1", "The Johnsons"), HomeRef("home-2", "Flat 4"))
        init {
            runBlocking {
                database.homeQueries.insert("home-1", "The Johnsons", "family", 0)
                database.homeQueries.insert("home-2", "Flat 4", "family", 0)
            }
        }

        val handler = PantryCommandHandler(
            resolveSource = { homeId -> source.takeIf { homeId == "home-1" } },
            listHomes = { homes },
            access = access,
            commandLog = commandLog,
            client = client,
            newId = { "urn:uuid:reply-${nextId++}" },
            log = { logged += it },
        )

        val replies get() = client.sent.map { Json.parseToJsonElement(it.payload.decodeToString()).jsonObject }
    }

    private fun fixture() = Fixture(listOf(agent))

    private fun command(
        id: String = "urn:uuid:c1",
        home: String = "home-1",
        fields: String = """"schema:name": "Heinz Ketchup", "schema:category": ["Condiments", "Ketchup"], "schema:gtin": "5000157024671", "schema:size": "1 bottle"""",
        amount: Int = 2,
    ) = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Add", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:$home:pantry"},
         "object": {"type": "schema:TypeAndQuantityNode", "schema:amountOfThisGood": $amount,
                    "schema:typeOfGood": {"type": "schema:Product", $fields}}}
    """.trimIndent()

    private fun message(
        payload: String,
        sender: String? = agent,
        verified: Boolean? = true,
        trust: String? = "accept",
    ) = X0xDirectMessage(
        sender = sender,
        payload = Base64.Default.encode(payload.encodeToByteArray()),
        verified = verified,
        trustDecision = trust,
    )

    private fun JsonMap.text(key: String) = getValue(key).jsonPrimitive.content

    @Test
    fun addsANewItemWithStockEqualToTheQuantityAndAccepts() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.handler.handle(message(command()))

        // Assert
        val item = assertNotNull(f.source.mutate { it.readPantryItem("5000157024671") })
        assertEquals("Heinz Ketchup", item.title)
        assertEquals("Condiments", item.primaryCategory)
        assertEquals("Ketchup", item.secondaryCategory)
        assertEquals("1 bottle", item.packageSize)
        assertEquals(2, item.stockCount)
        assertEquals(agent, f.client.sent.single().agentId)
        val reply = f.replies.single()
        assertEquals("Accept", reply.text("type"))
        assertEquals("urn:uuid:c1", reply.text("inReplyTo"))
        assertEquals("2", reply.getValue("result").jsonObject.text("schema:amountOfThisGood"))
    }

    @Test
    fun theAddIsVisibleThroughThePantrySourceTheUiUses() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.handler.handle(message(command()))
        val contents = f.source.contents.first()

        // Assert
        assertEquals(listOf("Heinz Ketchup" to 2), contents.items.map { it.title to it.stockCount })
    }

    @Test
    fun aSecondAddIncreasesStockAndKeepsFieldsTheCommandOmits() = runBlocking {
        // Arrange
        val f = fixture()
        f.handler.handle(message(command(id = "urn:uuid:c1")))
        val followUp = command(id = "urn:uuid:c2", fields = """"schema:name": "Heinz Ketchup", "schema:gtin": "5000157024671"""", amount = 3)

        // Act
        f.handler.handle(message(followUp))

        // Assert
        val item = assertNotNull(f.source.mutate { it.readPantryItem("5000157024671") })
        assertEquals(5, item.stockCount)
        assertEquals("Condiments", item.primaryCategory)
        assertEquals("Ketchup", item.secondaryCategory)
        assertEquals("1 bottle", item.packageSize)
        assertEquals("5", f.replies.last().getValue("result").jsonObject.text("schema:amountOfThisGood"))
    }

    @Test
    fun aSizeIsNormalisedAndTheStoredFormIsEchoedInTheReply() = runBlocking {
        // Arrange
        val f = fixture()
        val fields = """"schema:name": "Tomato Sauce", "schema:size": "15 Ounce Can""""

        // Act
        f.handler.handle(message(command(fields = fields)))

        // Assert
        val item = assertNotNull(f.source.mutate { it.readPantryItem("tomato sauce") })
        assertEquals("15 oz tin", item.packageSize)
        assertEquals("15 oz tin", f.replies.single().getValue("result").jsonObject.text("schema:size"))
    }

    @Test
    fun aSizeItDoesNotRecogniseIsStoredTrimmedRatherThanRejected() = runBlocking {
        // Arrange
        val f = fixture()
        val fields = """"schema:name": "Crisps", "schema:size": "  family size """"

        // Act
        f.handler.handle(message(command(fields = fields)))

        // Assert
        val item = assertNotNull(f.source.mutate { it.readPantryItem("crisps") })
        assertEquals("family size", item.packageSize)
        assertEquals("Accept", f.replies.single().text("type"))
    }

    @Test
    fun aNewItemWithoutCategoryOrSizeGetsDefaults() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.handler.handle(message(command(fields = """"schema:name": "Milk"""", amount = 1)))

        // Assert
        val item = assertNotNull(f.source.mutate { it.readPantryItem("milk") })
        assertEquals(UncategorizedCategory, item.primaryCategory)
        assertEquals("", item.packageSize)
        assertEquals(1, item.stockCount)
    }

    @Test
    fun itemsAreMatchedByNormalisedTitleWhenThereIsNoBarcode() = runBlocking {
        // Arrange
        val f = fixture()
        f.source.mutate { upsertPantryItem(it, "Dairy", null, "Whole Milk", "2 pint", barcode = null) }

        // Act
        f.handler.handle(message(command(fields = """"schema:name": "  WHOLE milk """", amount = 4)))

        // Assert
        val items = f.source.contents.first().items
        assertEquals(1, items.size)
        assertEquals(4, items.single().stockCount)
        assertEquals("Dairy", items.single().primaryCategory)
    }

    @Test
    fun aRepeatedCommandIdIsAppliedOnceAndTheSameReplyIsResent() = runBlocking {
        // Arrange
        val f = fixture()
        f.handler.handle(message(command()))

        // Act
        f.handler.handle(message(command()))

        // Assert
        assertEquals(2, f.source.contents.first().items.single().stockCount)
        assertEquals(2, f.client.sent.size)
        assertEquals(f.client.sent[0].payload.decodeToString(), f.client.sent[1].payload.decodeToString())
    }

    @Test
    fun theSameCommandIdFromADifferentSenderIsACommandOfItsOwn() = runBlocking {
        // Arrange
        val f = Fixture(listOf(agent, "agent-2"))
        f.handler.handle(message(command()))

        // Act
        f.handler.handle(message(command(), sender = "agent-2"))

        // Assert
        assertEquals(4, f.source.contents.first().items.single().stockCount)
    }

    @Test
    fun messagesTheDaemonDoesNotVouchForAreDroppedSilentlyAndNeverQueueAnApproval() = runBlocking {
        // Arrange
        val f = fixture()
        val cases = listOf(
            message(command(), verified = false),
            message(command(), verified = null),
            message(command(), trust = "reject"),
            message(command(), trust = null),
            message(command(), sender = null),
        )

        cases.forEach {
            // Act
            f.handler.handle(it)
        }

        // Assert
        assertEquals(emptyList(), f.source.contents.first().items)
        assertEquals(0, f.client.sent.size)
        assertEquals(emptyList(), f.access.pending.first())
        assertEquals(cases.size, f.logged.size)
    }

    @Test
    fun aTrustedButUnapprovedAgentGetsARejectAndIsQueuedForApproval() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())

        // Act
        f.handler.handle(message(command()))

        // Assert
        assertEquals(emptyList(), f.source.contents.first().items)
        val reply = f.replies.single()
        assertEquals("Reject", reply.text("type"))
        assertEquals(NotApprovedSummary, reply.text("summary"))
        assertEquals("urn:uuid:c1", reply.text("inReplyTo"))
        assertEquals(listOf(agent), f.access.pending.first().map { it.agentId })
    }

    @Test
    fun anUnapprovedAgentIsQueuedOnceHoweverOftenItAsks() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())

        // Act
        f.handler.handle(message(command(id = "urn:uuid:c1")))
        f.handler.handle(message(command(id = "urn:uuid:c2")))

        // Assert
        assertEquals(1, f.access.pending.first().size)
        assertEquals(2, f.client.sent.size)
    }

    @Test
    fun listHomesFromAnUnapprovedAgentIsHeldToo() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())

        // Act
        f.handler.handle(message(listHomesRequest()))

        // Assert
        assertEquals(NotApprovedSummary, f.replies.single().text("summary"))
    }

    @Test
    fun nonCommandTrafficFromAnUnapprovedAgentDoesNotQueueAnApproval() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())

        // Act
        f.handler.handle(message("""{"some": "sync message"}"""))

        // Assert
        assertEquals(emptyList(), f.access.pending.first())
        assertEquals(0, f.client.sent.size)
    }

    @Test
    fun onceTheUserApprovesTheAgentTheSameCommandIsAppliedNormally() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())
        f.handler.handle(message(command()))
        f.access.save(agent, "", listOf(AssistantGrant("home-1", AssistantCapability.PantryWrite, GrantPolicy.Auto)))

        // Act
        f.handler.handle(message(command()))

        // Assert
        assertEquals(2, f.source.contents.first().items.single().stockCount)
        assertEquals("Accept", f.replies.last().text("type"))
        assertEquals(emptyList(), f.access.pending.first())
    }

    @Test
    fun aDeniedRequestIsAskedAboutAgainWhenTheAgentReturns() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())
        f.handler.handle(message(command()))
        f.access.deny(agent)

        // Act
        f.handler.handle(message(command(id = "urn:uuid:c2")))

        // Assert
        assertEquals(listOf(agent), f.access.pending.first().map { it.agentId })
        assertEquals(emptyList(), f.source.contents.first().items)
    }

    @Test
    fun anUnknownHomeIsRejected() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.handler.handle(message(command(home = "nowhere")))

        // Assert
        val reply = f.replies.single()
        assertEquals("Reject", reply.text("type"))
        assertEquals("unknown home", reply.text("summary"))
        assertEquals("urn:uuid:c1", reply.text("inReplyTo"))
        assertEquals(emptyList(), f.source.contents.first().items)
    }

    @Test
    fun anInvalidCommandIsRejectedOnceAndItsRepeatGetsTheSameReject() = runBlocking {
        // Arrange
        val f = fixture()
        val invalid = command(amount = 0)

        // Act
        f.handler.handle(message(invalid))
        f.handler.handle(message(invalid))

        // Assert
        assertEquals(2, f.client.sent.size)
        assertEquals("quantity out of range", f.replies[0].text("summary"))
        assertEquals(f.replies[0], f.replies[1])
        assertEquals(emptyList(), f.source.contents.first().items)
    }

    @Test
    fun nonCommandTrafficIsIgnored() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.handler.handle(message("""{"hello": "world"}"""))
        f.handler.handle(message("not json at all"))
        f.handler.handle(X0xDirectMessage(sender = agent, payload = "%%%not base64", verified = true, trustDecision = "accept"))

        // Assert
        assertEquals(0, f.client.sent.size)
        assertEquals(emptyList(), f.source.contents.first().items)
    }

    private fun listHomesRequest(id: String = "urn:uuid:l1", objectId: String = "urn:vesta:homes") = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Read", "id": "$id", "object": {"type": "Collection", "id": "$objectId"}}
    """.trimIndent()

    @Test
    fun listHomesRepliesWithEveryHomesNameAndId() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.handler.handle(message(listHomesRequest()))

        // Assert
        val reply = f.replies.single()
        assertEquals("Accept", reply.text("type"))
        assertEquals("urn:uuid:l1", reply.text("inReplyTo"))
        val result = reply.getValue("result").jsonObject
        assertEquals("2", result.text("totalItems"))
        val items = result.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(listOf("home-1" to "The Johnsons", "home-2" to "Flat 4"), items.map { it.text("schema:identifier") to it.text("schema:name") })
        assertEquals("urn:vesta:home:home-1", items.first().text("id"))
    }

    @Test
    fun listHomesIsAnsweredFreshEachTimeAndWithNoHomesIsAnEmptyCollection() = runBlocking {
        // Arrange
        val f = fixture()
        f.handler.handle(message(listHomesRequest()))
        f.homes = emptyList()

        // Act
        f.handler.handle(message(listHomesRequest()))

        // Assert
        assertEquals(2, f.client.sent.size)
        assertEquals("0", f.replies.last().getValue("result").jsonObject.text("totalItems"))
    }

    @Test
    fun listHomesFromAnAgentTheDaemonDoesNotTrustGetsNothing() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.handler.handle(message(listHomesRequest(), trust = "reject"))
        f.handler.handle(message(listHomesRequest(), verified = false))

        // Assert
        assertEquals(0, f.client.sent.size)
    }

    @Test
    fun aMalformedListHomesRequestIsRejected() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.handler.handle(message(listHomesRequest(objectId = "urn:vesta:everything")))

        // Assert
        assertEquals("Reject", f.replies.single().text("type"))
    }

    @Test
    fun aFailedReplyDeliveryKeepsTheEffectAndALaterRepeatReceivesTheReply() = runBlocking {
        // Arrange
        val f = fixture()
        f.client.sendFailure = X0xApiException(503, "recipient_ack_semantics_unavailable")

        // Act
        f.handler.handle(message(command()))
        f.client.sendFailure = null
        f.handler.handle(message(command()))

        // Assert
        assertEquals(2, f.source.contents.first().items.single().stockCount)
        assertEquals("Accept", f.replies.single().text("type"))
        assertTrue(f.logged.any { "could not deliver" in it })
    }
}

private typealias JsonMap = kotlinx.serialization.json.JsonObject
