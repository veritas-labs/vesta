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
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.kits.x0x.X0xDirectMessage
import uk.veritaslabs.kits.x0x.testing.FakeX0xClient
import uk.veritaslabs.vesta.automerge.CookbookDocumentSource
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.ui.screens.Recipe
import uk.veritaslabs.vesta.ui.screens.RecipeIngredient
import uk.veritaslabs.vesta.ui.screens.RecipeStep

/** ADR-027's Cookbook Read and Write commands, through [PantryCommandHandler]. */
class CookbookCommandHandlerTest {

    private val agent = "agent-1"
    private val context = """["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]"""

    private class Fixture {
        val cookbook = CookbookDocumentSource(Document())
        val client = FakeX0xClient()
        val database = openInMemoryDatabase()
        val access = AssistantRepository(database, FixedAgentAllowlist(emptyList()))
        private var nextId = 0
        val handler = PantryCommandHandler(
            resolveSource = { PantryDocumentSource(Document.load(newPantryDocument())) },
            listHomes = { listOf(HomeRef("home-1", "The Johnsons")) },
            access = access,
            commandLog = DatabaseCommandLog(database),
            client = client,
            newId = { "urn:uuid:reply-${nextId++}" },
            log = {},
            resolveCookbook = { homeId -> cookbook.takeIf { homeId == "home-1" } },
        )
        val replies: List<JsonObject> get() = client.sent.map { Json.parseToJsonElement(it.payload.decodeToString()).jsonObject }
        val titles get() = runBlocking { cookbook.recipes.first().map { it.title } }

        init {
            runBlocking { database.homeQueries.insert("home-1", "The Johnsons", "family", 0) }
        }
    }

    private fun granted(policy: GrantPolicy, vararg capabilities: AssistantCapability) = Fixture().also { f ->
        runBlocking { f.access.save(agent, "", capabilities.map { AssistantGrant("home-1", it, policy) }) }
    }

    private fun send(f: Fixture, payload: String) = runBlocking {
        f.handler.handle(X0xDirectMessage(sender = agent, payload = Base64.Default.encode(payload.encodeToByteArray()), verified = true, trustDecision = "accept"))
    }

    private fun read(id: String, objectId: String = "urn:vesta:document:home-1:cookbook") =
        """{"@context": $context, "type": "Read", "id": "$id", "object": {"type": "Collection", "id": "$objectId"}}"""

    private fun write(type: String, id: String, node: String) =
        """{"@context": $context, "type": "$type", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:home-1:cookbook"}, "object": $node}"""

    private fun addPancakes(id: String = "urn:uuid:a1") =
        write("Add", id, """{"type": "schema:Recipe", "schema:name": "Pancakes", "schema:recipeYield": 4, "schema:recipeIngredient": ["2 eggs"], "schema:recipeInstructions": ["Mix"]}""")

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private fun Fixture.seed(title: String, key: String = "") = runBlocking {
        cookbook.save(Recipe(key = key, title = title, ingredients = listOf(RecipeIngredient("Flour", 200.0, "g")), steps = listOf(RecipeStep("s1", "Mix", 5))))
    }

    @Test
    fun readingTheCookbookListsEachRecipeWithItsKeyAndNameAndTheTotal() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookRead)
        val key = f.seed("Pancakes")
        f.seed("Soup")

        // Act
        send(f, read("urn:uuid:r1"))

        // Assert
        val result = f.replies.single().getValue("result").jsonObject
        assertEquals("2", result.text("totalItems"))
        val items = result.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Pancakes", "Soup"), items.map { it.text("schema:name") })
        assertEquals(key, items.first().text("schema:identifier"))
        assertEquals(false, items.first().containsKey("schema:recipeIngredient"))
    }

    @Test
    fun readingOneRecipeGivesItsIngredientsAndStepsWithDurations() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookRead)
        val key = f.seed("Pancakes")

        // Act
        send(f, read("urn:uuid:r1", "urn:vesta:document:home-1:cookbook:$key"))

        // Assert
        val result = f.replies.single().getValue("result").jsonObject
        val ingredient = result.getValue("schema:recipeIngredient").jsonArray.single().jsonObject
        assertEquals("Flour", ingredient.text("schema:name"))
        assertEquals("200.0", ingredient.text("schema:value"))
        assertEquals("g", ingredient.text("schema:unitText"))
        val step = result.getValue("schema:recipeInstructions").jsonArray.single().jsonObject
        assertEquals("Mix", step.text("schema:text"))
        assertEquals("PT5M", step.text("schema:performTime"))
    }

    @Test
    fun readingARecipeThatIsNotThereIsRejected() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookRead)

        // Act
        send(f, read("urn:uuid:r1", "urn:vesta:document:home-1:cookbook:nope"))

        // Assert
        assertEquals("unknown recipe", f.replies.single().text("summary"))
    }

    @Test
    fun aReadIsAnsweredFreshEachTime() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookRead)
        send(f, read("urn:uuid:r1"))
        f.seed("Soup")

        // Act
        send(f, read("urn:uuid:r1"))

        // Assert
        assertEquals(listOf("0", "1"), f.replies.map { it.getValue("result").jsonObject.text("totalItems") })
    }

    @Test
    fun aReplyIsCappedButSaysHowManyRecipesThereAre() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookRead)
        repeat(CookbookCommandParser.MAX_RECIPES + 3) { f.seed("Recipe $it") }

        // Act
        send(f, read("urn:uuid:r1"))

        // Assert
        val result = f.replies.single().getValue("result").jsonObject
        assertEquals((CookbookCommandParser.MAX_RECIPES + 3).toString(), result.text("totalItems"))
        assertEquals(CookbookCommandParser.MAX_RECIPES, result.getValue("items").jsonArray.size)
    }

    @Test
    fun addingARecipePutsItInTheCookbookAndRepliesWithItsKey() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookWrite)

        // Act
        send(f, addPancakes())

        // Assert
        val recipe = runBlocking { f.cookbook.recipes.first().single() }
        assertEquals("Pancakes", recipe.title)
        assertEquals(listOf("2 eggs"), recipe.ingredients.map { it.name })
        assertEquals(listOf("Mix"), recipe.steps.map { it.text })
        val result = f.replies.single().getValue("result").jsonObject
        assertEquals(recipe.key, result.text("schema:identifier"))
    }

    @Test
    fun aRepeatedAddIsAppliedOnceAndTheSameReplyResent() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookWrite)
        send(f, addPancakes())

        // Act
        send(f, addPancakes())

        // Assert
        assertEquals(listOf("Pancakes"), f.titles)
        assertEquals(2, f.client.sent.size)
    }

    @Test
    fun addingARecipeWhoseNameIsTakenIsRejected() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookWrite)
        f.seed("pancakes")

        // Act
        send(f, addPancakes())

        // Assert
        assertEquals("recipe already exists", f.replies.single().text("summary"))
        assertEquals(listOf("pancakes"), f.titles)
    }

    @Test
    fun removingARecipeByKeyOrByName() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookWrite)
        val key = f.seed("Pancakes")
        f.seed("Soup")

        // Act
        send(f, write("Remove", "urn:uuid:d1", """{"type": "schema:Recipe", "schema:identifier": "$key"}"""))
        send(f, write("Remove", "urn:uuid:d2", """{"type": "schema:Recipe", "schema:name": "SOUP"}"""))

        // Assert
        assertEquals(emptyList(), f.titles)
        assertEquals(listOf("Pancakes", "Soup"), f.replies.map { it.getValue("result").jsonObject.text("schema:name") })
    }

    @Test
    fun removingARecipeThatIsNotThereIsRejected() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookWrite)

        // Act
        send(f, write("Remove", "urn:uuid:d1", """{"type": "schema:Recipe", "schema:name": "Ghost"}"""))

        // Assert
        assertEquals("unknown recipe", f.replies.single().text("summary"))
    }

    @Test
    fun readAndWriteAreSeparateGrants() = runBlocking {
        // Arrange
        val readOnly = granted(GrantPolicy.Auto, AssistantCapability.CookbookRead)
        val writeOnly = granted(GrantPolicy.Auto, AssistantCapability.CookbookWrite)

        // Act
        send(readOnly, addPancakes())
        send(writeOnly, read("urn:uuid:r1"))

        // Assert
        assertEquals(NotPermittedSummary, readOnly.replies.single().text("summary"))
        assertEquals(emptyList(), readOnly.titles)
        assertEquals(NotPermittedSummary, writeOnly.replies.single().text("summary"))
    }

    @Test
    fun aPantryGrantDoesNotOpenTheCookbook() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.PantryRead, AssistantCapability.PantryWrite)

        // Act
        send(f, read("urn:uuid:r1"))
        send(f, addPancakes("urn:uuid:a2"))

        // Assert
        assertEquals(listOf(NotPermittedSummary, NotPermittedSummary), f.replies.map { it.text("summary") })
    }

    @Test
    fun anAskGrantHoldsAnAddUntilAllowOnceAndThenAppliesTheResentCommand() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Ask, AssistantCapability.CookbookWrite)
        send(f, addPancakes())
        val held = f.access.pendingCommands.value.single()

        // Act
        f.access.allowOnce(held)
        send(f, addPancakes())

        // Assert
        assertEquals(WaitingForApprovalSummary, f.replies.first().text("summary"))
        assertEquals("Add the recipe Pancakes to the cookbook", held.summary)
        assertEquals(listOf("Pancakes"), f.titles)
    }

    @Test
    fun anInvalidRecipeIsRejectedWithItsReason() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.CookbookWrite)

        // Act
        send(f, write("Add", "urn:uuid:a1", """{"type": "schema:Recipe", "schema:recipeYield": 4}"""))

        // Assert
        assertEquals("title is required", f.replies.single().text("summary"))
    }
}
