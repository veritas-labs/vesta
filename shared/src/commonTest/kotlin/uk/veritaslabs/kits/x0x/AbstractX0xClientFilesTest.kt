package uk.veritaslabs.kits.x0x

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AbstractX0xClientFilesTest {

    private class Call(val method: String, val url: String, val body: String?)

    /** An [AbstractX0xClient] whose HTTP layer answers from [respond] and whose event stream is driven by [frames]. */
    private class StubClient(var respond: (Call) -> X0xHttpResult = { X0xHttpResult(200, "{}") }) : AbstractX0xClient() {
        val calls = mutableListOf<Call>()
        var onEvent: (String) -> Unit = {}
        var streamUrl: String? = null

        override val endpoint = "stub"
        override suspend fun resolveDaemon() = "http://daemon" to "tok"

        override suspend fun request(method: String, url: String, bearerToken: String?, jsonBody: String?): X0xHttpResult {
            val call = Call(method, url, jsonBody)
            calls += call
            return respond(call)
        }

        override suspend fun openStream(url: String, bearerToken: String?, onEvent: (String) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream {
            streamUrl = url
            this.onEvent = onEvent
            return X0xEventStream { onClose(null) }
        }
    }

    @Test
    fun `sendFile posts the path and the checksum and returns the transfer id`() = runTest {
        // Arrange
        val client = StubClient { X0xHttpResult(200, """{"ok":true,"transfer_id":"t-1"}""") }

        // Act
        val id = client.sendFile("agent-b", "abc.webp", 1234, "abc", "/data/blobs/ab/abc")

        // Assert
        val call = client.calls.single()
        val body = Json.parseToJsonElement(call.body!!).jsonObject
        assertEquals("t-1", id)
        assertEquals("POST http://daemon/files/send", "${call.method} ${call.url}")
        assertEquals("agent-b", body["agent_id"]!!.jsonPrimitive.content)
        assertEquals("abc.webp", body["filename"]!!.jsonPrimitive.content)
        assertEquals("1234", body["size"]!!.jsonPrimitive.content)
        assertEquals("abc", body["sha256"]!!.jsonPrimitive.content)
        assertEquals("/data/blobs/ab/abc", body["path"]!!.jsonPrimitive.content)
        assertNull(body["data_b64"])
    }

    @Test
    fun `acceptFile and rejectFile post to the transfer`() = runTest {
        // Arrange
        val client = StubClient()

        // Act
        client.acceptFile("t/1")
        client.rejectFile("t-2")

        // Assert
        assertEquals(listOf("POST http://daemon/files/accept/t%2F1", "POST http://daemon/files/reject/t-2"), client.calls.map { "${it.method} ${it.url}" })
    }

    @Test
    fun `getFileTransfer reads the record at the top level or under transfer, and answers null for 404`() = runTest {
        // Arrange
        val flat = StubClient { X0xHttpResult(200, """{"transfer_id":"t","state":"Complete","sha256":"h","output_path":"/x/y","extra":1}""") }
        val wrapped = StubClient { X0xHttpResult(200, """{"transfer":{"transfer_id":"t","state":"Failed","error":"SHA-256 mismatch"}}""") }
        val missing = StubClient { X0xHttpResult(404, """{"error":"not found"}""") }

        // Act
        val a = flat.getFileTransfer("t")
        val b = wrapped.getFileTransfer("t")
        val c = missing.getFileTransfer("t")

        // Assert
        assertEquals(X0xFileTransfer("t", state = "Complete", sha256 = "h", outputPath = "/x/y"), a)
        assertEquals(X0xFileTransfer("t", state = "Failed", error = "SHA-256 mismatch"), b)
        assertNull(c)
    }

    @Test
    fun `listFileTransfers parses the transfers array`() = runTest {
        // Arrange
        val client = StubClient { X0xHttpResult(200, """{"transfers":[{"transfer_id":"a","state":"Pending","size":5},{"transfer_id":"b"}]}""") }

        // Act
        val transfers = client.listFileTransfers()

        // Assert
        assertEquals(listOf("a", "b"), transfers.map { it.transferId })
        assertEquals(5L, transfers[0].size)
    }

    @Test
    fun `an API error surfaces as X0xApiException`() = runTest {
        // Arrange
        val client = StubClient { X0xHttpResult(400, """{"error":"bad sha"}""") }

        // Act
        val failure = assertFailsWith<X0xApiException> { client.sendFile("a", "f", 1, "h", "/p") }

        // Assert
        assertEquals("bad sha", failure.message)
    }

    @Test
    fun `fileEvents reads file frames from the events endpoint and ignores everything else`() = runTest {
        // Arrange
        val client = StubClient()
        val events = mutableListOf<X0xFileEvent>()
        client.fileEvents({ events += it })

        // Act
        client.onEvent("""{"type":"message","data":{"topic":"t","payload":"AA=="}}""")
        client.onEvent("""{"type":"file:offer","data":{"filename":"abc.webp","sender":"agent-a","size":99,"transfer_id":"t-1"}}""")
        client.onEvent("""{"type":"file:complete","data":{"filename":"abc.webp","path":"/tr/t-1_abc.webp","sha256":"abc","transfer_id":"t-1"}}""")
        client.onEvent("""{"type":"file:failed","data":{"transfer_id":"t-2","error":"boom"}}""")
        client.onEvent("""{"type":"file:progress","data":{"transfer_id":"t-1"}}""")
        client.onEvent("""{"type":"file:offer","data":{"filename":"x"}}""")
        client.onEvent("not json")

        // Assert
        assertEquals("http://daemon/events", client.streamUrl)
        assertEquals(
            listOf(
                X0xFileEvent.Offer("t-1", "abc.webp", 99, "agent-a"),
                X0xFileEvent.Complete("t-1", "abc.webp", "abc", "/tr/t-1_abc.webp"),
                X0xFileEvent.Failed("t-2", "boom"),
            ),
            events,
        )
    }

    @Test
    fun `gossipEvents still sees message frames and not file frames`() = runTest {
        // Arrange
        val client = StubClient()
        val topics = mutableListOf<String?>()
        client.gossipEvents({ topics += it.topic })

        // Act
        client.onEvent("""{"type":"file:offer","data":{"filename":"a","sender":"s","size":1,"transfer_id":"t"}}""")
        client.onEvent("""{"type":"message","data":{"topic":"news"}}""")

        // Assert
        assertEquals(listOf<String?>("news"), topics)
    }
}
