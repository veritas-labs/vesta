package uk.veritaslabs.vesta.acceptance

import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.newShoppingListDocument
import uk.veritaslabs.vesta.automerge.upsertPantryItem
import uk.veritaslabs.vesta.agent.AssistantCapability
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.test.waitUntilNodeCount
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.foundation.layout.Box
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.automerge.Document
import uk.veritaslabs.vesta.agent.AgentApprovalHost
import uk.veritaslabs.vesta.agent.AssistantEditHost
import uk.veritaslabs.vesta.agent.AssistantRepository
import uk.veritaslabs.vesta.agent.AssistantsListHost
import uk.veritaslabs.vesta.agent.DatabaseCommandLog
import uk.veritaslabs.vesta.agent.HomeRef
import uk.veritaslabs.vesta.agent.NotApprovedSummary
import uk.veritaslabs.vesta.agent.NotPermittedSummary
import uk.veritaslabs.vesta.agent.PantryCommandHandler
import uk.veritaslabs.vesta.automerge.CalendarDocumentSource
import uk.veritaslabs.vesta.automerge.CookbookDocumentSource
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.components.AgentApprovalCardTestTags
import uk.veritaslabs.vesta.ui.screens.AssistantEditTestTags
import uk.veritaslabs.vesta.ui.screens.AssistantsTestTags
import uk.veritaslabs.vesta.home.HomeListItem
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.kits.x0x.testing.FakeX0xClient
import uk.veritaslabs.kits.x0x.X0xDirectMessage

/**
 * Acceptance tests for Assistants (docs/adr/ADR-027-assistants.md; see docs/testing/acceptance-tests.md). Each test is one
 * scenario, driven through what the user sees and taps (Settings → Assistants, the approval card) and through the x0x
 * messages the bot sends, over the real repository, command handler, Pantry documents and screens. The criteria:
 *
 *  1. I can add my bot ahead of time by its agent ID, grant it Pantry-add on one Home with no prompting, and its add lands.
 *  2. A Home I did not grant stays closed: adds are refused and the Home is not listed to the bot.
 *  3. A bot I have not added is refused, shows up as waiting in Assistants, and a card offers Review… and Deny.
 *  4. Reviewing a waiting bot and granting it something makes its re-sent command apply.
 *  5. With "Ask me each time" the command is held behind a card; Allow once applies it once.
 *  6. A bot granted nothing is refused every add (a capability never comes for free).
 *  7. Removing an assistant takes its access away; it is then treated as a stranger again.
 *  8. An assistant's page lists what it did and what was refused.
 *  9. Bots approved before Assistants existed keep working: see `AssistantMigrationTest`.
 * 10. Assistants, their grants and their activity survive restarting the app.
 *
 * The harness holds the navigation `App.kt` holds (list ↔ editor, the card's Review…); the x0x mesh is [FakeX0xClient].
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class AssistantsAcceptanceTest {

    private val bot = "agent-grok"

    @BeforeTest
    fun setMain() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class World {
        val database: KitsDatabase = openInMemoryDatabase()
        val pantries = mapOf(
            "home-1" to PantryDocumentSource(Document.load(newPantryDocument())),
            "home-2" to PantryDocumentSource(Document.load(newPantryDocument())),
        )
        val shoppingLists = pantries.mapValues { (_, pantry) ->
            ShoppingListDocumentSource(Document.load(newShoppingListDocument()), pantry)
        }
        val cookbook = CookbookDocumentSource(Document())
        val calendar = CalendarDocumentSource(Document(), EventCalendar.Home)
        val client = FakeX0xClient()
        private var nextId = 0

        init {
            runBlocking {
                database.homeQueries.insert("home-1", "The Johnsons", "family", 0)
                database.homeQueries.insert("home-2", "Flat 4", "family", 0)
            }
        }

        /** A fresh repository over the same database, as after restarting the app. */
        var assistants = AssistantRepository(database)

        fun handler() = PantryCommandHandler(
            resolveSource = { pantries[it] },
            resolveShoppingList = { shoppingLists[it] },
            listHomes = { listOf(HomeRef("home-1", "The Johnsons"), HomeRef("home-2", "Flat 4")) },
            access = assistants,
            commandLog = DatabaseCommandLog(database),
            client = client,
            newId = { "urn:uuid:reply-${nextId++}" },
            log = {},
            resolveCookbook = { homeId -> cookbook.takeIf { homeId == "home-1" } },
            resolveCalendar = { homeId -> calendar.takeIf { homeId == "home-1" } },
        )

        val replies: List<JsonObject> get() = client.sent.map { Json.parseToJsonElement(it.payload.decodeToString()).jsonObject }
        fun stock(home: String) = runBlocking { pantries.getValue(home).contents.first().items.sumOf { it.stockCount } }
    }

    private fun addCommand(id: String, home: String = "home-1") = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Add", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:$home:pantry"},
         "object": {"type": "schema:TypeAndQuantityNode", "schema:amountOfThisGood": 2,
                    "schema:typeOfGood": {"type": "schema:Product", "schema:name": "Milk"}}}
    """.trimIndent()

    private fun readPantryCommand(id: String, home: String = "home-1") = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Read", "id": "$id", "object": {"type": "Collection", "id": "urn:vesta:document:$home:pantry"}}
    """.trimIndent()

    private fun readShoppingCommand(id: String) = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Read", "id": "$id", "object": {"type": "Collection", "id": "urn:vesta:document:home-1:shopping-list"}}
    """.trimIndent()

    private fun listHomesCommand(id: String) = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Read", "id": "$id", "object": {"type": "Collection", "id": "urn:vesta:homes"}}
    """.trimIndent()

    private fun World.botSends(payload: String) = runBlocking {
        handler().handle(
            X0xDirectMessage(sender = bot, payload = Base64.Default.encode(payload.encodeToByteArray()), verified = true, trustDecision = "accept"),
        )
    }

    private sealed interface Screen {
        data object List : Screen
        data class Edit(val agentId: String?) : Screen
    }

    /** Settings → Assistants as the app wires it, with the approval card over everything. */
    private fun ComposeUiTest.showAssistants(world: World, start: Screen = Screen.List) {
        val homes = listOf(HomeListItem("home-1", "The Johnsons"), HomeListItem("home-2", "Flat 4"))
        setContent {
            VestaTheme {
                var screen by remember { mutableStateOf(start) }
                Box {
                    when (val current = screen) {
                        Screen.List -> AssistantsListHost(
                            world.assistants,
                            onAdd = { screen = Screen.Edit(null) },
                            onOpen = { screen = Screen.Edit(it) },
                            onBack = {},
                        )
                        is Screen.Edit -> AssistantEditHost(world.assistants, current.agentId, homes, onDone = { screen = Screen.List })
                    }
                    // Not over the editor: the card would sit on its buttons (App.kt does the same).
                    if (screen !is Screen.Edit) {
                        AgentApprovalHost(
                            world.assistants,
                            onReview = { screen = Screen.Edit(it) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                            homes = flowOf(homes),
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.tap(tag: String) = onNodeWithTag(tag).performClick()

    /** Waits for the list to show [agentId]'s row (the list is built off the UI thread). */
    private fun ComposeUiTest.waitForRow(agentId: String) = waitUntilExactlyOneExists(hasTestTag(AssistantsTestTags.row(agentId)))

    /** Taps something inside the editor's scrolling form, once the editor has loaded. */
    private fun ComposeUiTest.tapInForm(tag: String) {
        waitUntilExactlyOneExists(hasTestTag(AssistantEditTestTags.SaveButton))
        onNodeWithTag(tag).performScrollTo().performClick()
    }

    /** Adds [bot] from the Add assistant form, granting Pantry-add on [homeId] without asking each time. */
    private fun ComposeUiTest.addBotWithAutoPantryAdd(homeId: String) {
        tap(AssistantsTestTags.AddButton)
        onNodeWithTag(AssistantEditTestTags.AgentIdField).performTextInput(bot)
        onNodeWithTag(AssistantEditTestTags.NameField).performTextInput("Grok Bot")
        tapInForm(AssistantEditTestTags.capability(homeId, AssistantCapability.PantryWrite))
        tapInForm(AssistantEditTestTags.askEachTime(homeId))
        tapInForm(AssistantEditTestTags.SaveButton)
        waitForIdle()
    }

    @Test
    fun iCanAddMyBotAheadOfTimeAndItsAddLandsWithoutAPrompt() = runComposeUiTest {
        // Arrange — Given two Homes and no assistants
        val world = World()
        showAssistants(world)

        // Act — When I add my bot, grant it Pantry-add on The Johnsons without asking, and the bot adds milk there
        addBotWithAutoPantryAdd("home-1")
        world.botSends(addCommand("urn:uuid:c1"))
        waitForIdle()

        // Assert — Then the milk is in that Pantry, the bot is listed, and no card appeared
        assertEquals(2, world.stock("home-1"))
        assertEquals("Accept", world.replies.single().getValue("type").jsonPrimitive.content)
        onNodeWithTag(AssistantsTestTags.row(bot)).assertIsDisplayed()
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
    }

    private fun stockCommand(type: String, id: String, amount: Int) = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "$type", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:home-1:pantry"},
         "object": {"type": "schema:TypeAndQuantityNode", "schema:amountOfThisGood": $amount,
                    "schema:typeOfGood": {"type": "schema:Product", "schema:name": "Milk"}}}
    """.trimIndent()

    @Test
    fun myBotCanTakeMilkOutAndCorrectTheCountWithItsWriteGrant() = runComposeUiTest {
        // Arrange — Given a bot with Pantry Write and 2 milk in the Pantry
        val world = World()
        showAssistants(world)
        addBotWithAutoPantryAdd("home-1")
        world.botSends(addCommand("urn:uuid:c1"))

        // Act — When it takes 1 out, then sets the count to 7
        world.botSends(stockCommand("Remove", "urn:uuid:c2", 1))
        val afterRemove = world.stock("home-1")
        world.botSends(stockCommand("Update", "urn:uuid:c3", 7))

        // Assert — Then the Pantry went 2 → 1 → 7 and the bot was told each new count
        assertEquals(1, afterRemove)
        assertEquals(7, world.stock("home-1"))
        assertEquals(listOf("2", "1", "7"), world.replies.map { it.getValue("result").jsonObject.getValue("schema:amountOfThisGood").jsonPrimitive.content })
    }

    private fun shoppingCommand(type: String, id: String, amount: Int?) = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "$type", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:home-1:shopping-list"},
         "object": {"type": "schema:TypeAndQuantityNode", ${amount?.let { """"schema:amountOfThisGood": $it,""" } ?: ""}
                    "schema:typeOfGood": {"type": "schema:Product", "schema:name": "Milk"}}}
    """.trimIndent()

    @Test
    fun myBotCanKeepTheShoppingListWithItsShoppingListSwitchesAndNothingElse() = runComposeUiTest {
        // Arrange — Given a bot added with Shopping list Read and Write (asking each time switched off)
        val world = World()
        showAssistants(world)
        tap(AssistantsTestTags.AddButton)
        onNodeWithTag(AssistantEditTestTags.AgentIdField).performTextInput(bot)
        tapInForm(AssistantEditTestTags.capability("home-1", AssistantCapability.ShoppingListRead))
        tapInForm(AssistantEditTestTags.capability("home-1", AssistantCapability.ShoppingListWrite))
        tapInForm(AssistantEditTestTags.askEachTime("home-1"))
        tapInForm(AssistantEditTestTags.SaveButton)
        waitForIdle()

        // Act — When it adds 2 milk to the list, ticks 1 off, reads the list, and then tries the Pantry
        world.botSends(shoppingCommand("Add", "urn:uuid:s1", 2))
        world.botSends(shoppingCommand("Update", "urn:uuid:s2", 1))
        world.botSends(readShoppingCommand("urn:uuid:s3"))
        world.botSends(readPantryCommand("urn:uuid:s4"))

        // Assert — Then the list holds the milk with 1 picked up, the bot saw it, and the Pantry stayed closed and empty
        val list = runBlocking { world.shoppingLists.getValue("home-1").items.first() }
        val milk = list.single()
        assertEquals(listOf("Milk", "2", "1"), listOf(milk.title, milk.quantity.toString(), milk.got.toString()))
        val seen = world.replies[2].getValue("result").jsonObject.getValue("items").jsonArray.single().jsonObject
        assertEquals("1", seen.getValue("got").jsonPrimitive.content)
        assertEquals(NotPermittedSummary, world.replies[3].getValue("summary").jsonPrimitive.content)
        assertEquals(0, world.stock("home-1"))
    }

    private fun cookbookRead(id: String, objectId: String = "urn:vesta:document:home-1:cookbook") = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Read", "id": "$id", "object": {"type": "Collection", "id": "$objectId"}}
    """.trimIndent()

    private fun addPancakesCommand(id: String) = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "Add", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:home-1:cookbook"},
         "object": {"type": "schema:Recipe", "schema:name": "Pancakes", "schema:recipeYield": 4, "schema:cookTime": "PT15M",
                    "schema:recipeIngredient": ["200g flour", "2 eggs"], "schema:recipeInstructions": ["Mix", "Fry"]}}
    """.trimIndent()

    @Test
    fun myBotCanReadAndAddRecipesWithCookbookGrantsOnly() = runComposeUiTest {
        // Arrange — Given a bot added with Cookbook Read and Write (asking each time switched off) and no Pantry grant
        val world = World()
        showAssistants(world)
        tap(AssistantsTestTags.AddButton)
        onNodeWithTag(AssistantEditTestTags.AgentIdField).performTextInput(bot)
        tapInForm(AssistantEditTestTags.capability("home-1", AssistantCapability.CookbookRead))
        tapInForm(AssistantEditTestTags.capability("home-1", AssistantCapability.CookbookWrite))
        tapInForm(AssistantEditTestTags.askEachTime("home-1"))
        tapInForm(AssistantEditTestTags.SaveButton)
        waitForIdle()

        // Act — When it adds a pancake recipe, lists the cookbook, reads the recipe in full, and tries to read the Pantry
        world.botSends(addPancakesCommand("urn:uuid:c1"))
        world.botSends(cookbookRead("urn:uuid:r1"))
        val key = world.replies[1].getValue("result").jsonObject.getValue("items").jsonArray.single().jsonObject
            .getValue("schema:identifier").jsonPrimitive.content
        world.botSends(cookbookRead("urn:uuid:r2", "urn:vesta:document:home-1:cookbook:$key"))
        world.botSends(readPantryCommand("urn:uuid:r3"))

        // Assert — Then the recipe is in the cookbook with its steps, the bot read it back, and the Pantry read is refused
        val recipe = world.replies[2].getValue("result").jsonObject
        assertEquals("Pancakes", recipe.getValue("schema:name").jsonPrimitive.content)
        assertEquals(2, recipe.getValue("schema:recipeInstructions").jsonArray.size)
        assertEquals(NotPermittedSummary, world.replies[3].getValue("summary").jsonPrimitive.content)
        assertEquals(listOf("Pancakes"), runBlocking { world.cookbook.recipes.first().map { it.title } })
    }

    private fun calendarCommand(type: String, id: String, node: String) = """
        {"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}],
         "type": "$type", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:home-1:calendar"}, "object": $node}
    """.trimIndent()

    @Test
    fun myBotCanKeepTheHomeCalendarWithCalendarGrantsOnly() = runComposeUiTest {
        // Arrange — Given a bot added with Calendar Read and Write (asking each time switched off) and no Pantry grant
        val world = World()
        showAssistants(world)
        tap(AssistantsTestTags.AddButton)
        onNodeWithTag(AssistantEditTestTags.AgentIdField).performTextInput(bot)
        tapInForm(AssistantEditTestTags.capability("home-1", AssistantCapability.CalendarRead))
        tapInForm(AssistantEditTestTags.capability("home-1", AssistantCapability.CalendarWrite))
        tapInForm(AssistantEditTestTags.askEachTime("home-1"))
        tapInForm(AssistantEditTestTags.SaveButton)
        waitForIdle()

        // Act — When it adds a dentist visit, moves it by an hour, reads the calendar, and tries to read the Pantry
        world.botSends(calendarCommand("Add", "urn:uuid:k1", """{"type": "schema:Event", "schema:name": "Dentist", "schema:startDate": "2026-10-20T10:00:00Z", "schema:endDate": "2026-10-20T11:00:00Z"}"""))
        val id = world.replies[0].getValue("result").jsonObject.getValue("schema:identifier").jsonPrimitive.content
        world.botSends(calendarCommand("Update", "urn:uuid:k2", """{"type": "schema:Event", "schema:identifier": "$id", "schema:startDate": "2026-10-20T11:00:00Z"}"""))
        world.botSends("""{"@context": ["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}], "type": "Read", "id": "urn:uuid:k3", "object": {"type": "Collection", "id": "urn:vesta:document:home-1:calendar"}}""")
        world.botSends(readPantryCommand("urn:uuid:k4"))

        // Assert — Then the bot read back the moved event in UTC, the app's calendar holds it, and the Pantry read is refused
        val seen = world.replies[2].getValue("result").jsonObject.getValue("items").jsonArray.single().jsonObject
        assertEquals("2026-10-20T11:00:00Z", seen.getValue("schema:startDate").jsonPrimitive.content)
        assertEquals("2026-10-20T12:00:00Z", seen.getValue("schema:endDate").jsonPrimitive.content)
        assertEquals(listOf("Dentist"), runBlocking { world.calendar.events.first().map { it.title } })
        assertEquals(NotPermittedSummary, world.replies[3].getValue("summary").jsonPrimitive.content)
    }

    @Test
    fun iCanLetMyBotReadThePantryWithoutLettingItAdd() = runComposeUiTest {
        // Arrange — Given a bot added with Pantry Read only (asking each time switched off), and milk in the Pantry
        val world = World()
        showAssistants(world)
        tap(AssistantsTestTags.AddButton)
        onNodeWithTag(AssistantEditTestTags.AgentIdField).performTextInput(bot)
        tapInForm(AssistantEditTestTags.capability("home-1", AssistantCapability.PantryRead))
        tapInForm(AssistantEditTestTags.askEachTime("home-1"))
        tapInForm(AssistantEditTestTags.SaveButton)
        waitForIdle()
        runBlocking { world.pantries.getValue("home-1").mutate { upsertPantryItem(it, "Dairy", null, "Milk", "", null) } }

        // Act — When it reads the Pantry and then tries to add
        world.botSends(readPantryCommand("urn:uuid:r1"))
        world.botSends(addCommand("urn:uuid:c1"))

        // Assert — Then it sees the milk, and the add is refused
        val items = world.replies[0].getValue("result").jsonObject.getValue("items").jsonArray
        assertEquals(listOf("Milk"), items.map { it.jsonObject.getValue("schema:name").jsonPrimitive.content })
        assertEquals(NotPermittedSummary, world.replies[1].getValue("summary").jsonPrimitive.content)
    }

    @Test
    fun aHomeIDidNotGrantStaysClosed() = runComposeUiTest {
        // Arrange — Given my bot may add to The Johnsons only
        val world = World()
        showAssistants(world)
        addBotWithAutoPantryAdd("home-1")

        // Act — When it adds to Flat 4 and asks which Homes there are
        world.botSends(addCommand("urn:uuid:c1", home = "home-2"))
        world.botSends(listHomesCommand("urn:uuid:l1"))

        // Assert — Then the add is refused, Flat 4 is unchanged, and only The Johnsons is listed
        assertEquals(NotPermittedSummary, world.replies[0].getValue("summary").jsonPrimitive.content)
        assertEquals(0, world.stock("home-2"))
        val listed = world.replies[1].getValue("result").jsonObject.getValue("items").jsonArray
        assertEquals(listOf("home-1"), listed.map { it.jsonObject.getValue("schema:identifier").jsonPrimitive.content })
    }

    @Test
    fun aBotIHaveNotAddedIsRefusedAndWaitsForMyReview() = runComposeUiTest {
        // Arrange — Given a bot I never added
        val world = World()
        showAssistants(world)

        // Act — When it sends an add
        world.botSends(addCommand("urn:uuid:c1"))
        waitForIdle()

        // Assert — Then it is refused, nothing changed, a card offers Review… and Deny, and it waits in the list
        assertEquals(NotApprovedSummary, world.replies.single().getValue("summary").jsonPrimitive.content)
        assertEquals(0, world.stock("home-1"))
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertIsDisplayed()
        onNodeWithTag(AgentApprovalCardTestTags.Allow).assertIsDisplayed()
        onNodeWithTag(AgentApprovalCardTestTags.Deny).assertIsDisplayed()
        onNodeWithTag(AssistantsTestTags.row(bot)).assertIsDisplayed()
    }

    @Test
    fun reviewingAWaitingBotAndGrantingItMakesItsResentCommandApply() = runComposeUiTest {
        // Arrange — Given a bot waiting for review
        val world = World()
        showAssistants(world)
        world.botSends(addCommand("urn:uuid:c1"))
        waitForIdle()

        // Act — When I tap Review…, allow Pantry-add on The Johnsons without asking, save, and the bot re-sends
        tap(AgentApprovalCardTestTags.Allow)
        tapInForm(AssistantEditTestTags.capability("home-1", AssistantCapability.PantryWrite))
        tapInForm(AssistantEditTestTags.askEachTime("home-1"))
        tapInForm(AssistantEditTestTags.SaveButton)
        waitForIdle()
        world.botSends(addCommand("urn:uuid:c1"))

        // Assert — Then the add is applied and the card is gone
        assertEquals(2, world.stock("home-1"))
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
    }

    @Test
    fun withAskMeEachTimeAllowOnceAppliesTheCommandOnce() = runComposeUiTest {
        // Arrange — Given my bot may add to The Johnsons but I want to be asked each time
        val world = World()
        showAssistants(world)
        tap(AssistantsTestTags.AddButton)
        onNodeWithTag(AssistantEditTestTags.AgentIdField).performTextInput(bot)
        tapInForm(AssistantEditTestTags.capability("home-1", AssistantCapability.PantryWrite))
        onNodeWithTag(AssistantEditTestTags.askEachTime("home-1")).assertIsOn()
        tapInForm(AssistantEditTestTags.SaveButton)
        waitForIdle()

        // Act — When the bot adds, I see a card and tap Allow once, and the bot re-sends the same command twice
        world.botSends(addCommand("urn:uuid:c1"))
        waitForIdle()
        val stockWhileHeld = world.stock("home-1")
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertIsDisplayed()
        tap(AgentApprovalCardTestTags.Allow)
        world.botSends(addCommand("urn:uuid:c1"))
        world.botSends(addCommand("urn:uuid:c1"))
        waitForIdle()

        // Assert — Then nothing changed while it was held, and afterwards the milk was added once
        assertEquals(0, stockWhileHeld)
        assertEquals(2, world.stock("home-1"))
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertDoesNotExist()
    }

    @Test
    fun aBotGrantedNothingIsRefusedEveryAdd() = runComposeUiTest {
        // Arrange — Given I added my bot and granted it nothing
        val world = World()
        showAssistants(world)
        tap(AssistantsTestTags.AddButton)
        onNodeWithTag(AssistantEditTestTags.AgentIdField).performTextInput(bot)
        onNodeWithTag(AssistantEditTestTags.capability("home-1", AssistantCapability.PantryWrite)).assertIsOff()
        tapInForm(AssistantEditTestTags.SaveButton)
        waitForIdle()

        // Act — When it adds to either Home
        world.botSends(addCommand("urn:uuid:c1", home = "home-1"))
        world.botSends(addCommand("urn:uuid:c2", home = "home-2"))

        // Assert — Then both are refused and nothing changed
        assertEquals(listOf(NotPermittedSummary, NotPermittedSummary), world.replies.map { it.getValue("summary").jsonPrimitive.content })
        assertEquals(0, world.stock("home-1") + world.stock("home-2"))
    }

    @Test
    fun removingAnAssistantTakesItsAccessAway() = runComposeUiTest {
        // Arrange — Given my bot with Pantry-add on The Johnsons
        val world = World()
        showAssistants(world)
        addBotWithAutoPantryAdd("home-1")

        // Act — When I open it, remove it and confirm, and the bot adds again
        waitForRow(bot)
        tap(AssistantsTestTags.row(bot))
        tapInForm(AssistantEditTestTags.RemoveButton)
        tap(AssistantEditTestTags.ConfirmRemove)
        waitForIdle()
        world.botSends(addCommand("urn:uuid:c1"))
        waitForIdle()

        // Assert — Then the add is refused as from a stranger, nothing changed, and it waits for review again
        assertEquals(NotApprovedSummary, world.replies.single().getValue("summary").jsonPrimitive.content)
        assertEquals(0, world.stock("home-1"))
        onNodeWithTag(AgentApprovalCardTestTags.Card).assertIsDisplayed()
    }

    @Test
    fun anAssistantsPageListsWhatItDidAndWhatWasRefused() = runComposeUiTest {
        // Arrange — Given my bot with Pantry-add on The Johnsons only
        val world = World()
        showAssistants(world)
        addBotWithAutoPantryAdd("home-1")

        // Act — When it adds to The Johnsons, is refused for Flat 4, and I open its page
        world.botSends(addCommand("urn:uuid:c1", home = "home-1"))
        world.botSends(addCommand("urn:uuid:c2", home = "home-2"))
        waitForIdle()
        waitForRow(bot)
        tap(AssistantsTestTags.row(bot))
        waitForIdle()

        // Assert — Then I see both lines
        waitUntilNodeCount(hasTestTag(AssistantEditTestTags.ActivityRow), 2)
        onAllNodesWithTag(AssistantEditTestTags.ActivityRow).assertCountEquals(2)
    }

    @Test
    fun assistantsGrantsAndActivitySurviveRestartingTheApp() = runComposeUiTest {
        // Arrange — Given my bot with Pantry-add on The Johnsons that has added once
        val world = World()
        showAssistants(world)
        addBotWithAutoPantryAdd("home-1")
        world.botSends(addCommand("urn:uuid:c1"))

        // Act — When the app restarts (a new repository and screens over the same database)
        world.assistants = AssistantRepository(world.database)
        showAssistants(world)
        waitForIdle()
        world.botSends(addCommand("urn:uuid:c2"))
        waitForRow(bot)
        tap(AssistantsTestTags.row(bot))
        waitForIdle()

        // Assert — Then the bot is still listed with its access, and its history shows both adds
        assertEquals(4, world.stock("home-1"))
        onNodeWithTag(AssistantEditTestTags.capability("home-1", AssistantCapability.PantryWrite)).assertIsOn()
        waitUntilNodeCount(hasTestTag(AssistantEditTestTags.ActivityRow), 2)
        onAllNodesWithTag(AssistantEditTestTags.ActivityRow).assertCountEquals(2)
    }
}
