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
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.newShoppingListDocument
import uk.veritaslabs.vesta.automerge.adjustStock
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.automerge.pantryDedupKey
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

    private fun readPantryRequest(id: String = "urn:uuid:r1", home: String = "home-1") =
        listHomesRequest(id = id, objectId = "urn:vesta:document:$home:pantry")

    private fun grantRead(f: Fixture, policy: GrantPolicy, home: String = "home-1") = runBlocking {
        f.access.save(agent, "", listOf(AssistantGrant(home, AssistantCapability.PantryRead, policy)))
    }

    @Test
    fun readingThePantryRepliesWithEachItemAndItsStock() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())
        grantRead(f, GrantPolicy.Auto)
        f.source.mutate { upsertPantryItem(it, "Condiments", "Ketchup", "Heinz Ketchup", "1 bottle", "5000157024671") }
        f.source.mutate { adjustStock(it, "5000157024671", 3) }

        // Act
        f.handler.handle(message(readPantryRequest()))

        // Assert
        val reply = f.replies.single()
        assertEquals("Accept", reply.text("type"))
        val result = reply.getValue("result").jsonObject
        assertEquals("1", result.text("totalItems"))
        val item = result.getValue("items").jsonArray.single().jsonObject
        assertEquals("Heinz Ketchup", item.text("schema:name"))
        assertEquals(listOf("Condiments", "Ketchup"), item.getValue("schema:category").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("1 bottle", item.text("schema:size"))
        assertEquals("5000157024671", item.text("schema:gtin"))
        assertEquals("3", item.text("schema:amountOfThisGood"))
    }

    @Test
    fun readingThePantryChangesNothingAndIsAnsweredFreshEachTime() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())
        grantRead(f, GrantPolicy.Auto)
        f.handler.handle(message(readPantryRequest()))
        f.source.mutate { upsertPantryItem(it, "Dairy", null, "Milk", "", null) }

        // Act
        f.handler.handle(message(readPantryRequest()))

        // Assert
        assertEquals(listOf("0", "1"), f.replies.map { it.getValue("result").jsonObject.text("totalItems") })
    }

    @Test
    fun aWriteGrantDoesNotLetAnAssistantReadThePantry() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())
        f.access.save(agent, "", listOf(AssistantGrant("home-1", AssistantCapability.PantryWrite, GrantPolicy.Auto)))

        // Act
        f.handler.handle(message(readPantryRequest()))

        // Assert
        val reply = f.replies.single()
        assertEquals("Reject", reply.text("type"))
        assertEquals(NotPermittedSummary, reply.text("summary"))
    }

    @Test
    fun aReadGrantDoesNotLetAnAssistantAdd() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())
        grantRead(f, GrantPolicy.Auto)

        // Act
        f.handler.handle(message(command()))

        // Assert
        assertEquals(NotPermittedSummary, f.replies.single().text("summary"))
        assertEquals(emptyList(), f.source.contents.first().items)
    }

    @Test
    fun aReadInAHomeWithoutAGrantLooksLikeAHomeThatDoesNotExist() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())
        grantRead(f, GrantPolicy.Auto, home = "home-2")

        // Act
        f.handler.handle(message(readPantryRequest(home = "home-1")))
        f.handler.handle(message(readPantryRequest(id = "urn:uuid:r2", home = "nowhere")))

        // Assert
        assertEquals(listOf(NotPermittedSummary, NotPermittedSummary), f.replies.map { it.text("summary") })
    }

    @Test
    fun anAskGrantHoldsTheReadUntilAllowOnceAndThenAnswersTheResentCommand() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())
        grantRead(f, GrantPolicy.Ask)
        f.handler.handle(message(readPantryRequest()))
        val held = f.access.pendingCommands.value.single()

        // Act
        f.access.allowOnce(held)
        f.handler.handle(message(readPantryRequest()))

        // Assert
        assertEquals(WaitingForApprovalSummary, f.replies.first().text("summary"))
        assertEquals("Accept", f.replies.last().text("type"))
        assertEquals(AssistantCapability.PantryRead, held.capability)
    }

    @Test
    fun aReplyIsCappedAtTheMostUsedItemsButSaysHowManyThereAre() = runBlocking {
        // Arrange
        val f = Fixture(emptyList())
        grantRead(f, GrantPolicy.Auto)
        f.source.mutate { doc ->
            repeat(AgentCommandParser.MAX_PANTRY_ITEMS + 5) { upsertPantryItem(doc, "Misc", null, "Item $it", "", null) }
        }

        // Act
        f.handler.handle(message(readPantryRequest()))

        // Assert
        val result = f.replies.single().getValue("result").jsonObject
        assertEquals((AgentCommandParser.MAX_PANTRY_ITEMS + 5).toString(), result.text("totalItems"))
        assertEquals(AgentCommandParser.MAX_PANTRY_ITEMS, result.getValue("items").jsonArray.size)
    }

    private fun stockCommand(type: String, amount: Int?, id: String = "urn:uuid:s1", fields: String = """"schema:name": "Milk"""") = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "$type", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:home-1:pantry"},
         "object": {"type": "schema:TypeAndQuantityNode", ${amount?.let { """"schema:amountOfThisGood": $it,""" } ?: ""}
                    "schema:typeOfGood": {"type": "schema:Product", $fields}}}
    """.trimIndent()

    private fun withMilk(stock: Int, barcode: String? = null, f: Fixture = fixture()) = f.also { f ->
        runBlocking {
            f.source.mutate { upsertPantryItem(it, "Dairy", null, "Milk", "", barcode) }
            f.source.mutate { adjustStock(it, pantryDedupKey(barcode, "Milk"), stock) }
        }
    }

    private fun Fixture.stock() = runBlocking { source.contents.first().items.single().stockCount }

    @Test
    fun aRemoveLowersStockAndRepliesWithTheNewCount() = runBlocking {
        // Arrange
        val f = withMilk(5)

        // Act
        f.handler.handle(message(stockCommand("Remove", 2)))

        // Assert
        assertEquals(3, f.stock())
        val reply = f.replies.single()
        assertEquals("Accept", reply.text("type"))
        assertEquals("3", reply.getValue("result").jsonObject.text("schema:amountOfThisGood"))
    }

    @Test
    fun aRemoveOfMoreThanIsInStockStopsAtZero() = runBlocking {
        // Arrange
        val f = withMilk(2)

        // Act
        f.handler.handle(message(stockCommand("Remove", 9)))

        // Assert
        assertEquals(0, f.stock())
        assertEquals("0", f.replies.single().getValue("result").jsonObject.text("schema:amountOfThisGood"))
    }

    @Test
    fun aRepeatedRemoveIsAppliedOnce() = runBlocking {
        // Arrange
        val f = withMilk(5)
        f.handler.handle(message(stockCommand("Remove", 2)))

        // Act
        f.handler.handle(message(stockCommand("Remove", 2)))

        // Assert
        assertEquals(3, f.stock())
        assertEquals(2, f.client.sent.size)
    }

    @Test
    fun anUpdateSetsStockToTheExactValueUpOrDown() = runBlocking {
        // Arrange
        val f = withMilk(5)

        // Act
        f.handler.handle(message(stockCommand("Update", 8, id = "urn:uuid:s1")))
        val up = f.stock()
        f.handler.handle(message(stockCommand("Update", 0, id = "urn:uuid:s2")))

        // Assert
        assertEquals(8, up)
        assertEquals(0, f.stock())
        assertEquals("0", f.replies.last().getValue("result").jsonObject.text("schema:amountOfThisGood"))
    }

    @Test
    fun anItemStoredUnderABarcodeIsFoundByNameAlone() = runBlocking {
        // Arrange
        val f = withMilk(4, barcode = "5000")

        // Act
        f.handler.handle(message(stockCommand("Remove", 1, fields = """"schema:name": "milk"""")))

        // Assert
        assertEquals(3, f.stock())
    }

    @Test
    fun changingStockOfAnItemThatIsNotThereIsRejectedAndCreatesNothing() = runBlocking {
        // Arrange
        val f = fixture()

        // Act
        f.handler.handle(message(stockCommand("Remove", 1)))
        f.handler.handle(message(stockCommand("Update", 3, id = "urn:uuid:s2")))

        // Assert
        assertEquals(listOf("unknown item", "unknown item"), f.replies.map { it.text("summary") })
        assertEquals(emptyList(), f.source.contents.first().items)
    }

    @Test
    fun reducingAndSettingStockNeedThePantryWriteGrant() = runBlocking {
        // Arrange
        val f = withMilk(5)
        val g = withMilk(5, f = Fixture(emptyList()))
        runBlocking { g.access.save(agent, "", listOf(AssistantGrant("home-1", AssistantCapability.PantryRead, GrantPolicy.Auto))) }

        // Act
        g.handler.handle(message(stockCommand("Remove", 1)))
        g.handler.handle(message(stockCommand("Update", 0, id = "urn:uuid:s2")))

        // Assert
        assertEquals(listOf(NotPermittedSummary, NotPermittedSummary), g.replies.map { it.text("summary") })
        assertEquals(5, g.stock())
        assertEquals(5, f.stock())
    }

    @Test
    fun anAskGrantHoldsARemoveUntilAllowOnce() = runBlocking {
        // Arrange
        val g = withMilk(5, f = Fixture(emptyList()))
        g.access.save(agent, "", listOf(AssistantGrant("home-1", AssistantCapability.PantryWrite, GrantPolicy.Ask)))
        g.handler.handle(message(stockCommand("Remove", 2)))
        val held = g.access.pendingCommands.value.single()

        // Act
        g.access.allowOnce(held)
        g.handler.handle(message(stockCommand("Remove", 2)))

        // Assert
        assertEquals("Take 2 × Milk out of the Pantry", held.summary)
        assertEquals(3, g.stock())
    }

    // ---- shopping list (ShoppingListCommandHandler)

    private class ShoppingFixture(allowed: List<String>) {
        val pantry = PantryDocumentSource(Document.load(newPantryDocument()))
        val list = ShoppingListDocumentSource(Document.load(newShoppingListDocument()), pantry)
        val base = Fixture(allowed)
        val handler = PantryCommandHandler(
            resolveSource = { base.source },
            resolveShoppingList = { homeId -> list.takeIf { homeId == "home-1" } },
            listHomes = { base.homes },
            access = base.access,
            commandLog = base.commandLog,
            client = base.client,
            newId = { "urn:uuid:shop-reply-${base.client.sent.size}" },
            log = {},
        )
        val replies get() = base.replies
        val items get() = runBlocking { list.items.first() }
    }

    private fun shoppingCommand(
        type: String,
        amount: Int? = null,
        id: String = "urn:uuid:sh1",
        fields: String = """"schema:name": "Milk"""",
        home: String = "home-1",
    ) = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "$type", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:$home:shopping-list"},
         "object": {"type": "schema:TypeAndQuantityNode", ${amount?.let { """"schema:amountOfThisGood": $it,""" } ?: ""}
                    "schema:typeOfGood": {"type": "schema:Product", $fields}}}
    """.trimIndent()

    private fun readShoppingCommand(id: String = "urn:uuid:sr1", home: String = "home-1") =
        listHomesRequest(id = id, objectId = "urn:vesta:document:$home:shopping-list")

    private fun grantShopping(f: ShoppingFixture, capability: AssistantCapability, policy: GrantPolicy = GrantPolicy.Auto) = runBlocking {
        f.base.access.save(agent, "", listOf(AssistantGrant("home-1", capability, policy)))
    }

    @Test
    fun addingToTheShoppingListPutsTheItemOnItAndRepliesWithTheLine() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        grantShopping(f, AssistantCapability.ShoppingListWrite)

        // Act
        f.handler.handle(message(shoppingCommand("Add", 2, fields = """"schema:name": "Milk", "schema:category": "Dairy"""")))
        f.handler.handle(message(shoppingCommand("Add", 1, id = "urn:uuid:sh2")))

        // Assert
        val item = f.items.single()
        assertEquals(listOf("Milk", 3), listOf(item.title, item.quantity))
        assertEquals("Dairy", item.primaryCategory)
        assertEquals("3", f.replies.last().getValue("result").jsonObject.text("schema:amountOfThisGood"))
        assertEquals(emptyList(), f.base.source.contents.first().items)
    }

    @Test
    fun updatingTheShoppingListTicksAnItemOffAndZeroUnticksIt() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        grantShopping(f, AssistantCapability.ShoppingListWrite)
        f.handler.handle(message(shoppingCommand("Add", 2)))

        // Act
        f.handler.handle(message(shoppingCommand("Update", 2, id = "urn:uuid:sh2")))
        val ticked = f.items.single().isBought
        val gotAfterTick = f.replies.last().getValue("result").jsonObject.text("got")
        f.handler.handle(message(shoppingCommand("Update", 0, id = "urn:uuid:sh3")))

        // Assert
        assertEquals(true, ticked)
        assertEquals("2", gotAfterTick)
        assertEquals(false, f.items.single().isBought)
    }

    @Test
    fun removingTakesTheWholeLineOffAndIsFoundByNameIgnoringCase() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        grantShopping(f, AssistantCapability.ShoppingListWrite)
        f.handler.handle(message(shoppingCommand("Add", 2, fields = """"schema:name": "Milk", "schema:gtin": "123"""")))

        // Act
        f.handler.handle(message(shoppingCommand("Remove", id = "urn:uuid:sh2", fields = """"schema:name": "MILK"""")))

        // Assert
        assertEquals(emptyList(), f.items)
        assertEquals("Accept", f.replies.last().text("type"))
    }

    @Test
    fun changingAnItemThatIsNotOnTheListIsRejectedAndAddsNothing() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        grantShopping(f, AssistantCapability.ShoppingListWrite)

        // Act
        f.handler.handle(message(shoppingCommand("Remove")))
        f.handler.handle(message(shoppingCommand("Update", 1, id = "urn:uuid:sh2")))

        // Assert
        assertEquals(listOf("unknown item", "unknown item"), f.replies.map { it.text("summary") })
        assertEquals(emptyList(), f.items)
    }

    @Test
    fun aRepeatedShoppingListAddIsAppliedOnce() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        grantShopping(f, AssistantCapability.ShoppingListWrite)
        f.handler.handle(message(shoppingCommand("Add", 2)))

        // Act
        f.handler.handle(message(shoppingCommand("Add", 2)))

        // Assert
        assertEquals(2, f.items.single().quantity)
        assertEquals(2, f.base.client.sent.size)
    }

    @Test
    fun readingTheShoppingListRepliesWithQuantityAndPickedUpCount() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        grantShopping(f, AssistantCapability.ShoppingListRead)
        f.list.save(uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails(null, "Dairy", null, "Milk", 3, "1l", null))

        // Act
        f.handler.handle(message(readShoppingCommand()))

        // Assert
        val result = f.replies.single().getValue("result").jsonObject
        assertEquals("1", result.text("totalItems"))
        val item = result.getValue("items").jsonArray.single().jsonObject
        assertEquals(listOf("Milk", "3", "0", "1l"), listOf(item.text("schema:name"), item.text("schema:amountOfThisGood"), item.text("got"), item.text("schema:size")))
    }

    @Test
    fun theShoppingListGrantsAreSeparateFromEachOtherAndFromThePantry() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        f.base.access.save(
            agent, "",
            listOf(
                AssistantGrant("home-1", AssistantCapability.ShoppingListRead, GrantPolicy.Auto),
                AssistantGrant("home-1", AssistantCapability.PantryWrite, GrantPolicy.Auto),
            ),
        )

        // Act
        f.handler.handle(message(shoppingCommand("Add", 1)))
        f.handler.handle(message(readShoppingCommand(id = "urn:uuid:sr2")))
        f.handler.handle(message(command(id = "urn:uuid:c9")))

        // Assert
        assertEquals(NotPermittedSummary, f.replies[0].text("summary"))
        assertEquals("Accept", f.replies[1].text("type"))
        assertEquals("Accept", f.replies[2].text("type"))
        assertEquals(emptyList(), f.items)
    }

    @Test
    fun anAskGrantHoldsAShoppingListAddUntilAllowOnce() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        grantShopping(f, AssistantCapability.ShoppingListWrite, GrantPolicy.Ask)
        f.handler.handle(message(shoppingCommand("Add", 2)))
        val held = f.base.access.pendingCommands.value.single()

        // Act
        f.base.access.allowOnce(held)
        f.handler.handle(message(shoppingCommand("Add", 2)))

        // Assert
        assertEquals(WaitingForApprovalSummary, f.replies.first().text("summary"))
        assertEquals("Add 2 × Milk to the shopping list", held.summary)
        assertEquals(2, f.items.single().quantity)
    }

    @Test
    fun aWriteToAShoppingListThatHasNotArrivedYetIsRefusedAndCreatesNothing() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        val unseeded = ShoppingListDocumentSource(Document(), f.pantry)
        val handler = PantryCommandHandler(
            resolveSource = { f.base.source },
            resolveShoppingList = { unseeded },
            listHomes = { f.base.homes },
            access = f.base.access,
            commandLog = f.base.commandLog,
            client = f.base.client,
            newId = { "urn:uuid:x-${f.base.client.sent.size}" },
            log = {},
        )
        grantShopping(f, AssistantCapability.ShoppingListWrite)

        // Act
        handler.handle(message(shoppingCommand("Add", 1)))

        // Assert
        assertEquals(ShoppingListNotReadySummary, f.replies.single().text("summary"))
        assertEquals(emptyList(), runBlocking { unseeded.items.first() })
    }

    @Test
    fun aShoppingListInAHomeWithoutAGrantLooksLikeOneThatDoesNotExist() = runBlocking {
        // Arrange
        val f = ShoppingFixture(emptyList())
        f.base.access.save(agent, "", listOf(AssistantGrant("home-2", AssistantCapability.ShoppingListRead, GrantPolicy.Auto)))

        // Act
        f.handler.handle(message(readShoppingCommand(home = "home-1")))
        f.handler.handle(message(readShoppingCommand(id = "urn:uuid:sr2", home = "nowhere")))

        // Assert
        assertEquals(listOf(NotPermittedSummary, NotPermittedSummary), f.replies.map { it.text("summary") })
    }
}


private typealias JsonMap = kotlinx.serialization.json.JsonObject
