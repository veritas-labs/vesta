package uk.veritaslabs.vesta.autonomi

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StandaloneAutonomiClientTest {

    private val chunkAddress = "ab".repeat(32)
    private val pointerAddress = "cd".repeat(32)
    private val targetAddress = "ef".repeat(32)
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    private fun reply(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun client(): StandaloneAutonomiClient {
        server.createContext("/v1/chunks/") { exchange ->
            if (exchange.requestURI.path == "/v1/chunks/$chunkAddress") {
                reply(exchange, 200, """{"data":"${Base64.getEncoder().encodeToString(byteArrayOf(9, 8, 7))}"}""")
            } else {
                reply(exchange, 404, """{"error":"not found"}""")
            }
        }
        server.createContext("/v1/pointers/") { exchange ->
            if (exchange.requestURI.path == "/v1/pointers/$pointerAddress/resolve") {
                reply(exchange, 200, """{"kind":"chunk","kind_tag":0,"target":"$targetAddress"}""")
            } else {
                reply(exchange, 404, "")
            }
        }
        server.start()
        return StandaloneAutonomiClient(fixedPort = server.address.port)
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun fetchesAChunk() = runTest {
        // Arrange
        val client = client()

        // Act
        val bytes = client.chunkGet(chunkAddress)

        // Assert
        assertContentEquals(byteArrayOf(9, 8, 7), bytes)
        assertTrue(client.supportsChunks)
    }

    @Test
    fun anUnknownChunkIsNull() = runTest {
        // Arrange
        val client = client()

        // Act
        val bytes = client.chunkGet("11".repeat(32))

        // Assert
        assertNull(bytes)
    }

    @Test
    fun aMalformedAddressIsNull() = runTest {
        // Arrange
        val client = client()

        // Act
        val bytes = client.chunkGet("../health")

        // Assert
        assertNull(bytes)
    }

    @Test
    fun resolvesAPointerToItsTarget() = runTest {
        // Arrange
        val client = client()

        // Act
        val target = client.pointerResolve(pointerAddress)

        // Assert
        assertEquals(targetAddress, target)
    }

    @Test
    fun aMissingPointerRouteIsNullNotAnError() = runTest {
        // Arrange
        val client = client()

        // Act
        val target = client.pointerResolve("12".repeat(32))

        // Assert
        assertNull(target)
    }

    @Test
    fun anUnreachableDaemonIsNull() = runTest {
        // Arrange
        val dead = StandaloneAutonomiClient(fixedPort = 1)

        // Act
        val bytes = dead.chunkGet(chunkAddress)

        // Assert
        assertNull(bytes)
    }
}
