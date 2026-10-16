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
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.TaskDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.tasks.Task
import uk.veritaslabs.vesta.tasks.TaskList

/** ADR-027's Tasks Read and Write commands, through [PantryCommandHandler]. */
class TasksCommandHandlerTest {

    private val agent = "agent-1"
    private val context = """["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]"""

    private class Fixture {
        val shared = TaskDocumentSource(Document(), TaskList.Home)
        val private = TaskDocumentSource(Document(), TaskList.Private)
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
            resolveTasks = { homeId -> shared.takeIf { homeId == "home-1" } },
        )
        val replies: List<JsonObject> get() = client.sent.map { Json.parseToJsonElement(it.payload.decodeToString()).jsonObject }
        val tasks get() = runBlocking { shared.snapshot.first() }

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

    private fun read(id: String) =
        """{"@context": $context, "type": "Read", "id": "$id", "object": {"type": "Collection", "id": "urn:vesta:document:home-1:tasks"}}"""

    private fun write(type: String, id: String, node: String) =
        """{"@context": $context, "type": "$type", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:home-1:tasks"}, "object": $node}"""

    private fun add(title: String = "Take the bins out", id: String = "urn:uuid:a1", extra: String = "") =
        write("Add", id, """{"type": "schema:Action", "schema:name": "$title"$extra}""")

    private fun status(done: Boolean) = if (done) "schema:CompletedActionStatus" else "schema:PotentialActionStatus"

    private fun update(title: String, done: Boolean, id: String = "urn:uuid:u1") =
        write("Update", id, """{"type": "schema:Action", "schema:name": "$title", "schema:actionStatus": "${status(done)}"}""")

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private fun Fixture.seed(title: String, due: CalendarDate? = null, id: String = title) = runBlocking {
        shared.save(Task(id = id, list = TaskList.Home, title = title, due = due))
    }

    @Test
    fun readingListsTheSharedTasksWithDueDatesAndStatusAndTheTrueTotal() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.TasksRead)
        f.seed("Bins", CalendarDate(2026, 10, 14))
        f.seed("Dust")

        // Act
        send(f, read("urn:uuid:r1"))

        // Assert
        val result = f.replies.single().getValue("result").jsonObject
        assertEquals("2", result.text("totalItems"))
        val items = result.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Bins", "Dust"), items.map { it.text("schema:name") })
        assertEquals("2026-10-14", items[0].text("schema:endTime"))
        assertEquals("schema:PotentialActionStatus", items[0].text("schema:actionStatus"))
    }

    @Test
    fun theUsersPrivateTasksAreNeverReturned() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.TasksRead)
        runBlocking { f.private.save(Task(id = "p1", list = TaskList.Private, title = "Secret")) }
        f.seed("Bins")

        // Act
        send(f, read("urn:uuid:r1"))

        // Assert
        val names = f.replies.single().getValue("result").jsonObject.getValue("items").jsonArray.map { it.jsonObject.text("schema:name") }
        assertEquals(listOf("Bins"), names)
    }

    @Test
    fun addingCreatesAHomeTaskAndRepliesWithIt() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.TasksWrite)

        // Act
        send(f, add(extra = """, "schema:endTime": "2026-10-14", "schema:description": "Both""""))

        // Assert
        val task = f.tasks.tasks.single()
        assertEquals(listOf("Take the bins out", "Both", CalendarDate(2026, 10, 14), TaskList.Home), listOf(task.title, task.description, task.due, task.list))
        val result = f.replies.single().getValue("result").jsonObject
        assertEquals(task.id, result.text("schema:identifier"))
        assertEquals("schema:PotentialActionStatus", result.text("schema:actionStatus"))
    }

    @Test
    fun addingTheSameTaskAndDayTwiceIsRejectedButARepeatedCommandIdIsReplayed() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.TasksWrite)
        send(f, add())

        // Act
        send(f, add(id = "urn:uuid:a1")) // same command id: replayed
        send(f, add(id = "urn:uuid:a2")) // new command id, same task: rejected

        // Assert
        assertEquals(1, f.tasks.tasks.size)
        assertEquals(listOf("Accept", "Accept", "Reject"), f.replies.map { it.text("type") })
        assertEquals("task already exists", f.replies.last().text("summary"))
    }

    @Test
    fun markingDoneAndNotDoneChangesTheStatusAndTheDoneRecord() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.TasksWrite)
        f.seed("Bins", CalendarDate(2026, 10, 14))

        // Act
        send(f, update("bins", done = true, id = "urn:uuid:u1"))
        val doneRecords = f.tasks.done.size
        send(f, update("Bins", done = false, id = "urn:uuid:u2"))

        // Assert
        assertEquals(1, doneRecords)
        assertEquals(0, f.tasks.done.size)
        assertEquals(listOf("schema:CompletedActionStatus", "schema:PotentialActionStatus"), f.replies.map { it.getValue("result").jsonObject.text("schema:actionStatus") })
    }

    @Test
    fun removingDeletesTheTaskAndRepliesWithItAsItWas() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.TasksWrite)
        f.seed("Bins")

        // Act
        send(f, write("Remove", "urn:uuid:d1", """{"type": "schema:Action", "schema:name": "Bins"}"""))

        // Assert
        assertEquals(emptyList(), f.tasks.tasks)
        assertEquals("Bins", f.replies.single().getValue("result").jsonObject.text("schema:name"))
    }

    @Test
    fun anUnknownOrAmbiguousTaskIsRejectedAndChangesNothing() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Auto, AssistantCapability.TasksWrite)
        f.seed("Bins", id = "k1")
        f.seed("Bins", CalendarDate(2026, 10, 21), id = "k2")

        // Act
        send(f, update("Nothing", done = true, id = "urn:uuid:u1"))
        send(f, update("bins", done = true, id = "urn:uuid:u2"))

        // Assert
        assertEquals(listOf("unknown task", "several tasks have that name: use schema:identifier"), f.replies.map { it.text("summary") })
        assertEquals(0, f.tasks.done.size)
        assertEquals(2, f.tasks.tasks.size)
    }

    @Test
    fun theTwoSwitchesAreSeparate() = runBlocking {
        // Arrange
        val readOnly = granted(GrantPolicy.Auto, AssistantCapability.TasksRead)
        val writeOnly = granted(GrantPolicy.Auto, AssistantCapability.TasksWrite)

        // Act
        send(readOnly, add())
        send(writeOnly, read("urn:uuid:r1"))

        // Assert
        assertEquals(NotPermittedSummary, readOnly.replies.single().text("summary"))
        assertEquals(NotPermittedSummary, writeOnly.replies.single().text("summary"))
        assertEquals(emptyList(), readOnly.tasks.tasks)
    }

    @Test
    fun anAskGrantHoldsAWriteUntilAllowOnceThenAppliesTheResentCommand() = runBlocking {
        // Arrange
        val f = granted(GrantPolicy.Ask, AssistantCapability.TasksWrite)
        send(f, add())
        val held = f.access.pendingCommands.value.single()

        // Act
        f.access.allowOnce(held)
        send(f, add())

        // Assert
        assertEquals("Add the task Take the bins out", held.summary)
        assertEquals(WaitingForApprovalSummary, f.replies.first().text("summary"))
        assertEquals(1, f.tasks.tasks.size)
    }
}
