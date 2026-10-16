package uk.veritaslabs.vesta.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgentCommandParserTest {

    private val context = """["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]"""

    private fun command(
        id: String = "\"urn:uuid:1\"",
        type: String = "\"Add\"",
        target: String = """{"type": "Collection", "id": "urn:vesta:document:home-1:pantry"}""",
        nodeType: String = "\"schema:TypeAndQuantityNode\"",
        amount: String? = "2",
        productType: String = "\"schema:Product\"",
        productFields: String = """"schema:name": "Heinz Ketchup", "schema:category": ["Condiments", "Ketchup"], "schema:gtin": "5000157024671", "schema:size": "1 bottle"""",
        ctx: String = context,
    ): ByteArray = """
        {"@context": $ctx, "type": $type, "id": $id, "target": $target,
         "object": {"type": $nodeType, ${amount?.let { "\"schema:amountOfThisGood\": $it," } ?: ""}
                    "schema:typeOfGood": {"type": $productType, $productFields}}}
    """.trimIndent().encodeToByteArray()

    private fun invalid(payload: ByteArray): ParseResult.Invalid = assertIs<ParseResult.Invalid>(AgentCommandParser.parse(payload))

    @Test
    fun parsesAFullCommand() {
        // Arrange
        val payload = command()

        // Act
        val result = AgentCommandParser.parse(payload)

        // Assert
        val parsed = assertIs<AddPantryItem>(assertIs<ParseResult.Command>(result).command)
        assertEquals(
            AddPantryItem("urn:uuid:1", "home-1", "Heinz Ketchup", "Condiments", "Ketchup", "1 bottle", "5000157024671", 2),
            parsed,
        )
    }

    @Test
    fun parsesAMinimalCommandWithDefaults() {
        // Arrange
        val payload = command(amount = null, productFields = """"schema:name": "Milk"""")

        // Act
        val result = AgentCommandParser.parse(payload)

        // Assert
        val parsed = assertIs<AddPantryItem>(assertIs<ParseResult.Command>(result).command)
        assertEquals(1, parsed.quantity)
        assertEquals("Milk", parsed.title)
        assertNull(parsed.primaryCategory)
        assertNull(parsed.secondaryCategory)
        assertNull(parsed.packageSize)
        assertNull(parsed.barcode)
    }

    @Test
    fun acceptsACategoryGivenAsABareString() {
        // Arrange
        val payload = command(productFields = """"schema:name": "Milk", "schema:category": "Dairy"""")

        // Act
        val parsed = assertIs<AddPantryItem>(assertIs<ParseResult.Command>(AgentCommandParser.parse(payload)).command)

        // Assert
        assertEquals("Dairy", parsed.primaryCategory)
        assertNull(parsed.secondaryCategory)
    }

    @Test
    fun rejectsMalformedCommandsKeepingTheCommandId() {
        // Arrange
        val cases = listOf(
            "type must be Add, Remove, Update or Read" to command(type = "\"Delete\""),
            "target.id must be" to command(target = """{"id": "urn:vesta:document:home-1:shopping"}"""),
            "target.id must be" to command(target = """{"id": "urn:vesta:document::pantry"}"""),
            "target.id is required" to command(target = "{}"),
            "schema:TypeAndQuantityNode" to command(nodeType = "\"schema:Thing\""),
            "schema:Product" to command(productType = "\"schema:Person\""),
            "title is required" to command(productFields = """"schema:name": "  """"),
            "title is required" to command(productFields = """"schema:category": "Dairy""""),
            "category" to command(productFields = """"schema:name": "Milk", "schema:category": ["a", "b", "c"]"""),
            "category" to command(productFields = """"schema:name": "Milk", "schema:category": []"""),
            "category" to command(productFields = """"schema:name": "Milk", "schema:category": 7"""),
            "quantity out of range" to command(amount = "0"),
            "quantity out of range" to command(amount = "10000"),
            "quantity must be an integer" to command(amount = "\"2\""),
            "quantity must be an integer" to command(amount = "1.5"),
            "size must be a string" to command(productFields = """"schema:name": "Milk", "schema:size": 3"""),
        )

        cases.forEach { (expected, payload) ->
            // Act
            val result = invalid(payload)

            // Assert
            assertEquals("urn:uuid:1", result.commandId, expected)
            assertEquals(true, result.reason.contains(expected), "'${result.reason}' should mention '$expected'")
        }
    }

    private fun read(objectId: String = "urn:vesta:homes") =
        ("""{"@context": $context, "type": "Read", "id": "urn:uuid:l1", "object": {"type": "Collection", "id": "$objectId"}}""").encodeToByteArray()

    @Test
    fun parsesAListHomesRequest() {
        // Arrange
        val payload = read()

        // Act
        val result = AgentCommandParser.parse(payload)

        // Assert
        assertEquals(ParseResult.Command(ListHomes("urn:uuid:l1")), result)
    }

    @Test
    fun parsesAPantryReadRequest() {
        // Arrange
        val payload = read(objectId = "urn:vesta:document:home-1:pantry")

        // Act
        val result = AgentCommandParser.parse(payload)

        // Assert
        assertEquals(ParseResult.Command(ReadPantry("urn:uuid:l1", "home-1")), result)
    }

    @Test
    fun rejectsAReadOfAnythingButTheHomesCollectionOrAPantry() {
        // Arrange
        val payload = read(objectId = "urn:vesta:document:home-1:calendar")

        // Act
        val result = invalid(payload)

        // Assert
        assertEquals("urn:uuid:l1", result.commandId)
        assertEquals(true, result.reason.contains("urn:vesta:homes"))
    }

    @Test
    fun homesReplyListsEachHomeWithItsId() {
        // Arrange / Act
        val reply = Json.parseToJsonElement(AgentReplies.homes("urn:uuid:r1", "urn:uuid:l1", listOf(HomeRef("h1", "The Johnsons")))).jsonObject

        // Assert
        assertEquals("Accept", reply["type"]!!.jsonPrimitive.content)
        assertEquals("urn:uuid:l1", reply["inReplyTo"]!!.jsonPrimitive.content)
        val item = reply["result"]!!.jsonObject["items"]!!.jsonArray.single().jsonObject
        assertEquals("h1", item["schema:identifier"]!!.jsonPrimitive.content)
        assertEquals("The Johnsons", item["schema:name"]!!.jsonPrimitive.content)
    }

    @Test
    fun rejectsACommandWithoutAnIdButCannotReply() {
        // Arrange
        val payload = command(id = "\"\"")

        // Act
        val result = invalid(payload)

        // Assert
        assertNull(result.commandId)
        assertEquals("id is required", result.reason)
    }

    @Test
    fun treatsNonJsonAndOtherContextsAsNotACommand() {
        // Arrange
        val notJson = "hello".encodeToByteArray()
        val jsonArray = "[1,2]".encodeToByteArray()
        val noContext = """{"type": "Add"}""".encodeToByteArray()
        val otherContext = command(ctx = """["https://www.w3.org/ns/activitystreams"]""")
        val otherPrefix = command(ctx = """["https://www.w3.org/ns/activitystreams", {"s": "https://schema.org/"}]""")

        // Act / Assert
        listOf(notJson, jsonArray, noContext, otherContext, otherPrefix).forEach {
            assertEquals(ParseResult.NotACommand, AgentCommandParser.parse(it))
        }
    }

    @Test
    fun rejectsAnOversizePayload() {
        // Arrange
        val payload = command(productFields = """"schema:name": "${"x".repeat(AgentCommandParser.MAX_PAYLOAD_BYTES)}"""")

        // Act
        val result = invalid(payload)

        // Assert
        assertEquals("payload too large", result.reason)
    }

    @Test
    fun repliesRoundTripAndReferenceTheCommand() {
        // Arrange / Act
        val accept = Json.parseToJsonElement(AgentReplies.accept("urn:uuid:r1", "urn:uuid:1", "key-1", "Heinz Ketchup", 3)).jsonObject
        val reject = Json.parseToJsonElement(AgentReplies.reject("urn:uuid:r2", "urn:uuid:1", "unknown home")).jsonObject

        // Assert
        assertEquals("Accept", accept["type"]!!.jsonPrimitive.content)
        assertEquals("urn:uuid:1", accept["inReplyTo"]!!.jsonPrimitive.content)
        assertEquals("urn:uuid:1", accept["object"]!!.jsonPrimitive.content)
        assertEquals(AgentCommandParser.CONTEXT, accept["@context"])
        val result = accept["result"]!!.jsonObject
        assertEquals("key-1", result["id"]!!.jsonPrimitive.content)
        assertEquals("3", result["schema:amountOfThisGood"]!!.jsonPrimitive.content)
        assertEquals("Reject", reject["type"]!!.jsonPrimitive.content)
        assertEquals("urn:uuid:1", reject["inReplyTo"]!!.jsonPrimitive.content)
        assertEquals("unknown home", reject["summary"]!!.jsonPrimitive.content)
        assertNull(reject["result"])
    }

    private fun stockChange(type: String, amount: String?, fields: String = """"schema:name": "Milk", "schema:gtin": "123"""") =
        ("""{"@context": $context, "type": "$type", "id": "urn:uuid:s1", "target": {"type": "Collection", "id": "urn:vesta:document:home-1:pantry"},
            "object": {"type": "schema:TypeAndQuantityNode", ${amount?.let { """"schema:amountOfThisGood": $it,""" } ?: ""}
                       "schema:typeOfGood": {"type": "schema:Product", $fields}}}""").encodeToByteArray()

    @Test
    fun parsesARemoveAsAReduceOfStockDefaultingToOne() {
        // Arrange
        val payload = stockChange("Remove", amount = null)

        // Act
        val result = AgentCommandParser.parse(payload)

        // Assert
        assertEquals(ParseResult.Command(ReducePantryStock("urn:uuid:s1", "home-1", "Milk", "123", 1)), result)
    }

    @Test
    fun parsesAnUpdateAsASetOfStockAndAllowsZero() {
        // Arrange
        val payload = stockChange("Update", amount = "0", fields = """"schema:name": "Milk"""")

        // Act
        val result = AgentCommandParser.parse(payload)

        // Assert
        assertEquals(ParseResult.Command(SetPantryStock("urn:uuid:s1", "home-1", "Milk", null, 0)), result)
    }

    @Test
    fun rejectsBadStockChanges() {
        // Arrange
        val cases = mapOf(
            "quantity out of range" to stockChange("Remove", "0"),
            "quantity out of range " to stockChange("Update", "10000"),
            "quantity is required" to stockChange("Update", null),
            "quantity must be an integer" to stockChange("Remove", "\"2\""),
            "title is required" to stockChange("Remove", "1", fields = """"schema:gtin": "123""""),
        )

        // Act / Assert
        cases.forEach { (expected, payload) ->
            val result = invalid(payload)
            assertEquals(true, result.reason.contains(expected.trim()), "'${result.reason}' should mention '$expected'")
        }
    }

    private fun shopping(type: String, amount: String?, fields: String = """"schema:name": "Milk", "schema:gtin": "123"""", target: String = "urn:vesta:document:home-1:shopping-list") =
        ("""{"@context": $context, "type": "$type", "id": "urn:uuid:p1", "target": {"type": "Collection", "id": "$target"},
            "object": {"type": "schema:TypeAndQuantityNode", ${amount?.let { """"schema:amountOfThisGood": $it,""" } ?: ""}
                       "schema:typeOfGood": {"type": "schema:Product", $fields}}}""").encodeToByteArray()

    @Test
    fun parsesAShoppingListRead() {
        // Arrange
        val payload = read(objectId = "urn:vesta:document:home-1:shopping-list")

        // Act
        val result = AgentCommandParser.parse(payload)

        // Assert
        assertEquals(ParseResult.Command(ReadShoppingList("urn:uuid:l1", "home-1")), result)
    }

    @Test
    fun parsesAnAddToTheShoppingListWithItsDetails() {
        // Arrange
        val payload = shopping("Add", "3", fields = """"schema:name": "Milk", "schema:category": ["Dairy", "Milk"], "schema:size": "1l", "schema:gtin": "123"""")

        // Act
        val result = AgentCommandParser.parse(payload)

        // Assert
        assertEquals(ParseResult.Command(AddShoppingItem("urn:uuid:p1", "home-1", "Milk", "Dairy", "Milk", "1l", "123", 3)), result)
    }

    @Test
    fun anAddToTheShoppingListDefaultsToOneAndARemoveNeedsNoAmount() {
        // Arrange
        val add = shopping("Add", null, fields = """"schema:name": "Milk"""")
        val remove = shopping("Remove", null)

        // Act
        val added = AgentCommandParser.parse(add)
        val removed = AgentCommandParser.parse(remove)

        // Assert
        assertEquals(ParseResult.Command(AddShoppingItem("urn:uuid:p1", "home-1", "Milk", null, null, null, null, 1)), added)
        assertEquals(ParseResult.Command(RemoveShoppingItem("urn:uuid:p1", "home-1", "Milk", "123")), removed)
    }

    @Test
    fun anUpdateOfTheShoppingListSetsHowManyArePickedUpAndAllowsZero() {
        // Arrange
        val payload = shopping("Update", "0")

        // Act
        val result = AgentCommandParser.parse(payload)

        // Assert
        assertEquals(ParseResult.Command(SetShoppingProgress("urn:uuid:p1", "home-1", "Milk", "123", 0)), result)
    }

    @Test
    fun rejectsBadShoppingListWrites() {
        // Arrange
        val cases = mapOf(
            "quantity out of range" to shopping("Add", "0"),
            "quantity out of range " to shopping("Update", "10000"),
            "quantity is required" to shopping("Update", null),
            "quantity must be an integer" to shopping("Add", "\"2\""),
            "title is required" to shopping("Add", "1", fields = """"schema:gtin": "123""""),
            "category must have" to shopping("Add", "1", fields = """"schema:name": "Milk", "schema:category": ["a", "b", "c"]"""),
        )

        // Act / Assert
        cases.forEach { (expected, payload) ->
            val result = invalid(payload)
            assertEquals("urn:uuid:p1", result.commandId)
            assertEquals(true, result.reason.contains(expected.trim()), "'${result.reason}' should mention '$expected'")
        }
    }
}
