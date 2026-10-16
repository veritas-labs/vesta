package uk.veritaslabs.kits.blobs

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class HttpPhotoFetcherTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/ok") { it.sendResponseHeaders(200, 3); it.responseBody.use { out -> out.write(byteArrayOf(1, 2, 3)) } }
        createContext("/missing") { it.sendResponseHeaders(404, -1); it.close() }
        createContext("/huge") { it.sendResponseHeaders(200, PhotoLimits.MaxInputBytes + 1); it.close() }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"

    @AfterTest
    fun stop() = server.stop(0)

    private fun fetch(url: String) = runBlocking { HttpPhotoFetcher().fetch(url) }

    @Test
    fun aLinkThatAnswersGivesItsBytes() {
        // Arrange / Act
        val result = fetch("$base/ok")

        // Assert
        assertContentEquals(byteArrayOf(1, 2, 3), assertIs<PhotoFetchResult.Fetched>(result).bytes)
    }

    @Test
    fun aLinkThatAnswersWithAnErrorIsUnreachable() {
        // Arrange / Act
        val result = fetch("$base/missing")

        // Assert
        assertEquals(PhotoRejection.Unreachable, assertIs<PhotoFetchResult.Failed>(result).reason)
    }

    @Test
    fun aFileOverTheSizeLimitIsTooLargeAndNotRead() {
        // Arrange / Act
        val result = fetch("$base/huge")

        // Assert
        assertEquals(PhotoRejection.TooLarge, assertIs<PhotoFetchResult.Failed>(result).reason)
    }

    @Test
    fun somethingThatIsNotAWebAddressIsUnreachable() {
        // Arrange / Act
        val results = listOf("not a url", "file:///etc/passwd", "ftp://example.com/a.jpg").map { fetch(it) }

        // Assert
        results.forEach { assertEquals(PhotoRejection.Unreachable, assertIs<PhotoFetchResult.Failed>(it).reason) }
    }
}
