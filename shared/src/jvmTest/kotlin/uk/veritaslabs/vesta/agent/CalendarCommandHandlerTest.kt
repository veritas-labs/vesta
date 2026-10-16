package uk.veritaslabs.vesta.agent

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import uk.veritaslabs.vesta.automerge.CalendarDocumentSource
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.calendar.allDayMillis

/** ADR-027's Calendar Read and Write commands, through [PantryCommandHandler]. */
class CalendarCommandHandlerTest {

    private val agent = "agent-1"
    private val context = """["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]"""
    private val day = allDayMillis(CalendarDate(2026, 10, 20))
    private val hour = 3_600_000L
    private val millisPerDay = 86_400_000L

    private class Fixture {
        val shared = CalendarDocumentSource(Document(), EventCalendar.Home)
        val private = CalendarDocumentSource(Document(), EventCalendar.Private)
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
            resolveCalendar = { homeId -> shared.takeIf { homeId == "home-1" } },
        )
        val replies: List<JsonObject> get() = client.sent.map { Json.parseToJsonElement(it.payload.decodeToString()).jsonObject }
        val events get() = runBlocking { shared.events.first() }

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

    private fun read(id: String, fields: String = "", objectId: String = "urn:vesta:document:home-1:calendar") =
        """{"@context": $context, "type": "Read", "id": "$id", "object": {"type": "Collection", "id": "$objectId"$fields}}"""

    private fun write(type: String, id: String, node: String) =
        """{"@context": $context, "type": "$type", "id": "$id", "target": {"type": "Collection", "id": "urn:vesta:document:home-1:calendar"}, "object": $node}"""

    private fun addDentist(id: String = "urn:uuid:a1") = write(
        "Add", id,
        """{"type": "schema:Event", "schema:name": "Dentist", "schema:startDate": "2026-10-20T10:00:00Z", "schema:endDate": "2026-10-20T11:00:00Z", "schema:location": "High St"}""",
    )

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private fun Fixture.seed(id: String, title: String, start: Long, end: Long, allDay: Boolean = false) = runBlocking {
        shared.save(CalendarEvent(id, EventCalendar.Home, title, allDay, start, end))
    }

    private val bothSwitches = arrayOf(AssistantCapability.CalendarRead, AssistantCapability.CalendarWrite)

    @Test
    fun readingListsTheEventsEarliestFirstWithTheirTimesInUtcAndDatesForAllDayOnes() {
        // Arrange
        val f = granted(GrantPolicy.Auto, *bothSwitches)
        f.seed("ev-2", "Holiday", day + millisPerDay, day + 2 * millisPerDay, allDay = true)
        f.seed("ev-1", "Dentist", day + 10 * hour, day + 11 * hour)

        // Act
        send(f, read("urn:uuid:r1"))

        // Assert
        val result = f.replies.single().getValue("result").jsonObject
        assertEquals("2", result.text("totalItems"))
        val items = result.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Dentist", "Holiday"), items.map { it.text("schema:name") })
        assertEquals(listOf("2026-10-20T10:00:00Z", "2026-10-20T11:00:00Z", "false"), listOf("schema:startDate", "schema:endDate", "allDay").map { items[0].text(it) })
        assertEquals(listOf("2026-10-21", "2026-10-22", "true"), listOf("schema:startDate", "schema:endDate", "allDay").map { items[1].text(it) })
    }

    @Test
    fun aRangeKeepsOnlyTheEventsTouchingIt() {
        // Arrange
        val f = granted(GrantPolicy.Auto, *bothSwitches)
        f.seed("ev-1", "Before", day - 3 * millisPerDay, day - 2 * millisPerDay, allDay = true)
        f.seed("ev-2", "Spans", day - millisPerDay, day + millisPerDay, allDay = true)
        f.seed("ev-3", "Inside", day + 10 * hour, day + 11 * hour)
        f.seed("ev-4", "After", day + 5 * millisPerDay, day + 5 * millisPerDay + hour)

        // Act
        send(f, read("urn:uuid:r1", """, "schema:startDate": "2026-10-20", "schema:endDate": "2026-10-20""""))

        // Assert
        val items = f.replies.single().getValue("result").jsonObject.getValue("items").jsonArray.map { it.jsonObject.text("schema:name") }
        assertEquals(listOf("Spans", "Inside"), items)
    }

    @Test
    fun anAddCreatesTheEventAndRepliesWithItsIdentifier() {
        // Arrange
        val f = granted(GrantPolicy.Auto, *bothSwitches)

        // Act
        send(f, addDentist())

        // Assert
        val event = f.events.single()
        assertEquals(listOf("Dentist", "High St", day + 10 * hour, day + 11 * hour), listOf(event.title, event.location, event.startMillis, event.endMillis))
        assertEquals(EventCalendar.Home, event.calendar)
        val reply = f.replies.single()
        assertEquals("Accept", reply.text("type"))
        assertEquals(event.id, reply.getValue("result").jsonObject.text("schema:identifier"))
    }

    @Test
    fun anAddWithNoEndIsAnHourLongOrOneDayLong() {
        // Arrange
        val f = granted(GrantPolicy.Auto, *bothSwitches)

        // Act
        send(f, write("Add", "urn:uuid:a1", """{"type": "schema:Event", "schema:name": "Call", "schema:startDate": "2026-10-20T10:00:00Z"}"""))
        send(f, write("Add", "urn:uuid:a2", """{"type": "schema:Event", "schema:name": "Birthday", "schema:startDate": "2026-10-20"}"""))

        // Assert
        val byTitle = f.events.associateBy { it.title }
        assertEquals(day + 11 * hour, byTitle.getValue("Call").endMillis)
        assertEquals(true, byTitle.getValue("Birthday").allDay)
        assertEquals(day, byTitle.getValue("Birthday").endMillis)
    }

    @Test
    fun aRepeatedCommandIdIsAppliedOnceAndAnIdenticalEventUnderANewIdIsRefused() {
        // Arrange
        val f = granted(GrantPolicy.Auto, *bothSwitches)
        send(f, addDentist("urn:uuid:a1"))

        // Act
        send(f, addDentist("urn:uuid:a1"))
        send(f, addDentist("urn:uuid:a2"))

        // Assert
        assertEquals(1, f.events.size)
        assertEquals(f.replies[0], f.replies[1])
        assertEquals("event already exists", f.replies[2].text("summary"))
    }

    @Test
    fun anUpdateChangesOnlyTheFieldsGivenAndMovesTheWholeEventWhenOnlyTheStartChanges() {
        // Arrange
        val f = granted(GrantPolicy.Auto, *bothSwitches)
        f.seed("ev-1", "Dentist", day + 10 * hour, day + 11 * hour)

        // Act
        send(f, write("Update", "urn:uuid:u1", """{"type": "schema:Event", "schema:identifier": "ev-1", "schema:startDate": "2026-10-20T14:00:00Z", "schema:description": "Bring forms"}"""))

        // Assert
        val event = f.events.single()
        assertEquals(listOf("Dentist", "Bring forms", day + 14 * hour, day + 15 * hour), listOf(event.title, event.description, event.startMillis, event.endMillis))
    }

    @Test
    fun anUpdateCannotMakeATimedEventEndBeforeItStartsOrTurnItIntoHalfADayEvent() {
        // Arrange
        val f = granted(GrantPolicy.Auto, *bothSwitches)
        f.seed("ev-1", "Dentist", day + 10 * hour, day + 11 * hour)

        // Act
        send(f, write("Update", "urn:uuid:u1", """{"type": "schema:Event", "schema:identifier": "ev-1", "schema:endDate": "2026-10-20T09:00:00Z"}"""))
        send(f, write("Update", "urn:uuid:u2", """{"type": "schema:Event", "schema:identifier": "ev-1", "schema:endDate": "2026-10-21"}"""))

        // Assert
        assertEquals(day + 11 * hour, f.events.single().endMillis)
        assertTrue(f.replies.all { it.text("type") == "Reject" })
    }

    @Test
    fun aRemoveDeletesTheEventAndAnUnknownEventIsRejected() {
        // Arrange
        val f = granted(GrantPolicy.Auto, *bothSwitches)
        f.seed("ev-1", "Dentist", day + 10 * hour, day + 11 * hour)

        // Act
        send(f, write("Remove", "urn:uuid:d1", """{"type": "schema:Event", "schema:identifier": "ev-1"}"""))
        send(f, write("Remove", "urn:uuid:d2", """{"type": "schema:Event", "schema:identifier": "ev-1"}"""))
        send(f, write("Update", "urn:uuid:u1", """{"type": "schema:Event", "schema:identifier": "nope", "schema:name": "X"}"""))

        // Assert
        assertEquals(emptyList(), f.events)
        assertEquals(listOf("Accept", "Reject", "Reject"), f.replies.map { it.text("type") })
        assertEquals(listOf("unknown event", "unknown event"), f.replies.drop(1).map { it.text("summary") })
    }

    @Test
    fun theSwitchesAreSeparateAndAGrantOnOneHomeDoesNotCoverAnother() {
        // Arrange
        val readOnly = granted(GrantPolicy.Auto, AssistantCapability.CalendarRead)
        val writeOnly = granted(GrantPolicy.Auto, AssistantCapability.CalendarWrite)

        // Act
        send(readOnly, addDentist())
        send(writeOnly, read("urn:uuid:r1"))
        send(writeOnly, read("urn:uuid:r2", objectId = "urn:vesta:document:home-2:calendar"))

        // Assert
        assertEquals(NotPermittedSummary, readOnly.replies.single().text("summary"))
        assertEquals(emptyList(), readOnly.events)
        assertEquals(listOf(NotPermittedSummary, NotPermittedSummary), writeOnly.replies.map { it.text("summary") })
    }

    @Test
    fun anAskGrantHoldsAWriteUntilAllowOnceAndThenAppliesTheResentCommand() {
        // Arrange
        val f = granted(GrantPolicy.Ask, *bothSwitches)
        send(f, addDentist())
        val held = f.access.pendingCommands.value.single()

        // Act
        f.access.allowOnce(held)
        send(f, addDentist())

        // Assert
        assertEquals("Add the event Dentist to the calendar", held.summary)
        assertEquals(WaitingForApprovalSummary, f.replies.first().text("summary"))
        assertEquals(1, f.events.size)
    }

    @Test
    fun thePrivateCalendarIsNeverReachableAndItsAddressIsNotACalendarAddress() {
        // Arrange
        val f = granted(GrantPolicy.Auto, *bothSwitches)
        runBlocking { f.private.save(CalendarEvent("secret", EventCalendar.Private, "Surprise party", false, day, day + hour)) }

        // Act
        send(f, read("urn:uuid:r1", objectId = "urn:vesta:document:home-1:private-calendar"))
        send(f, read("urn:uuid:r2"))

        // Assert
        assertEquals("Reject", f.replies[0].text("type"))
        assertEquals("0", f.replies[1].getValue("result").jsonObject.text("totalItems"))
    }
}
