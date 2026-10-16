package uk.veritaslabs.vesta.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import uk.veritaslabs.vesta.calendar.CalendarDate

class TasksCommandParserTest {

    private val context = """["https://www.w3.org/ns/activitystreams", {"schema": "https://schema.org/"}]"""

    private fun payload(type: String, node: String?, document: String = "urn:vesta:document:home-1:tasks") =
        if (type == "Read") {
            """{"@context": $context, "type": "Read", "id": "urn:uuid:t1", "object": {"type": "Collection", "id": "$document"}}"""
        } else {
            """{"@context": $context, "type": "$type", "id": "urn:uuid:t1", "target": {"type": "Collection", "id": "$document"}, "object": $node}"""
        }.encodeToByteArray()

    @Test
    fun parsesAReadOfAHomesTasks() {
        // Arrange
        val bytes = payload("Read", null)

        // Act
        val result = AgentCommandParser.parse(bytes)

        // Assert
        assertEquals(ParseResult.Command(ReadTasks("urn:uuid:t1", "home-1")), result)
    }

    @Test
    fun theUsersPrivateTasksCannotBeAddressed() {
        // Arrange
        val read = payload("Read", null, document = "urn:vesta:document:home-1:private-tasks")
        val add = payload("Add", """{"type": "schema:Action", "schema:name": "x"}""", document = "urn:vesta:document:home-1:private-tasks")

        // Act
        val readResult = AgentCommandParser.parse(read)
        val addResult = AgentCommandParser.parse(add)

        // Assert
        assertIs<ParseResult.Invalid>(readResult)
        assertIs<ParseResult.Invalid>(addResult)
    }

    @Test
    fun parsesAnAddWithADueDate() {
        // Arrange
        val bytes = payload("Add", """{"type": "schema:Action", "schema:name": " Take the bins out ", "schema:description": "Both", "schema:endTime": "2026-10-14"}""")

        // Act
        val result = AgentCommandParser.parse(bytes)

        // Assert
        assertEquals(
            ParseResult.Command(AddTask("urn:uuid:t1", "home-1", "Take the bins out", "Both", CalendarDate(2026, 10, 14))),
            result,
        )
    }

    @Test
    fun parsesUpdateAsDoneOrNotDoneAndRemoveByKeyOrName() {
        // Arrange
        val done = payload("Update", """{"type": "schema:Action", "schema:identifier": "k1", "schema:actionStatus": "schema:CompletedActionStatus"}""")
        val notDone = payload("Update", """{"type": "schema:Action", "schema:name": "Bins", "schema:actionStatus": "schema:PotentialActionStatus"}""")
        val remove = payload("Remove", """{"type": "schema:Action", "schema:name": "Bins"}""")

        // Act
        val results = listOf(done, notDone, remove).map(AgentCommandParser::parse)

        // Assert
        assertEquals(ParseResult.Command(SetTaskDone("urn:uuid:t1", "home-1", "k1", null, true)), results[0])
        assertEquals(ParseResult.Command(SetTaskDone("urn:uuid:t1", "home-1", null, "Bins", false)), results[1])
        assertEquals(ParseResult.Command(RemoveTask("urn:uuid:t1", "home-1", null, "Bins")), results[2])
    }

    @Test
    fun rejectsBadTaskCommandsKeepingTheCommandId() {
        // Arrange
        val cases = mapOf(
            "title is required" to payload("Add", """{"type": "schema:Action"}"""),
            "endTime must be a date" to payload("Add", """{"type": "schema:Action", "schema:name": "x", "schema:endTime": "tomorrow"}"""),
            "endTime must be a date " to payload("Add", """{"type": "schema:Action", "schema:name": "x", "schema:endTime": "2026-02-30"}"""),
            "title is too long" to payload("Add", """{"type": "schema:Action", "schema:name": "${"x".repeat(201)}"}"""),
            "schema:actionStatus must be" to payload("Update", """{"type": "schema:Action", "schema:name": "x"}"""),
            "schema:identifier or schema:name is required" to payload("Remove", """{"type": "schema:Action"}"""),
            "object must be a schema:Action" to payload("Add", """{"type": "schema:Product", "schema:name": "x"}"""),
        )

        // Act / Assert
        cases.forEach { (expected, bytes) ->
            val result = AgentCommandParser.parse(bytes)
            assertIs<ParseResult.Invalid>(result)
            assertEquals("urn:uuid:t1", result.commandId)
            assertEquals(true, result.reason.contains(expected.trim()), "'${result.reason}' should mention '$expected'")
        }
    }
}
