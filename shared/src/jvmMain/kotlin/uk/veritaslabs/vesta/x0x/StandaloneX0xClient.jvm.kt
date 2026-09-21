package uk.veritaslabs.vesta.x0x

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val sharedHttpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build()

internal actual suspend fun httpRequest(method: String, url: String, bearerToken: String?, jsonBody: String?): X0xHttpResult =
    withContext(Dispatchers.IO) {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
        bearerToken?.let { builder.header("Authorization", "Bearer $it") }

        val bodyPublisher = if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
            HttpRequest.BodyPublishers.ofString(jsonBody)
        } else {
            HttpRequest.BodyPublishers.noBody()
        }
        builder.method(method, bodyPublisher)

        val response = sharedHttpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        X0xHttpResult(response.statusCode(), response.body())
    }

internal actual suspend fun openSseStream(url: String, bearerToken: String?, onEvent: (String) -> Unit): X0xEventStream =
    withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofDays(1))
            .header("Accept", "text/event-stream")
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .GET()
            .build()

        val response = sharedHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() >= 400) {
            val body = response.body().bufferedReader().use { it.readText() }
            throw X0xApiException(response.statusCode(), body.ifBlank { "x0x API error (HTTP ${response.statusCode()})" })
        }

        val closed = AtomicBoolean(false)
        val reader = BufferedReader(InputStreamReader(response.body(), StandardCharsets.UTF_8))
        val thread = Thread({
            try {
                val dataLines = mutableListOf<String>()
                while (!closed.get()) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
                        line.isEmpty() && dataLines.isNotEmpty() -> {
                            val payload = dataLines.joinToString("\n")
                            dataLines.clear()
                            runCatching { onEvent(payload) }
                        }
                        line.startsWith(":") -> Unit // comment / keepalive
                        else -> dataLines.clear()
                    }
                }
            } catch (_: Exception) {
                // Stream ended or was closed.
            } finally {
                runCatching { reader.close() }
            }
        }, "x0x-sse-$url").apply {
            isDaemon = true
            start()
        }

        X0xEventStream {
            if (closed.compareAndSet(false, true)) {
                runCatching { reader.close() }
                thread.interrupt()
            }
        }
    }

/**
 * Desktop client: no daemon is embedded on the JVM — the app drives the
 * standalone `x0xd` REST API. The loopback port and bearer token are read
 * from x0x's standard data directory (override with the `X0X_DATA_DIR`
 * environment variable).
 */
private class StandaloneX0xClient : AbstractX0xClient() {

    private data class LocalDaemon(val port: Int, val token: String?)

    private val mutex = Mutex()
    private var resolved: LocalDaemon? = null

    private fun resolve(): LocalDaemon? {
        val candidates = buildList {
            System.getenv("X0X_DATA_DIR")?.let { add(File(it)) }
            System.getProperty("user.home")?.let { home ->
                add(File(home, ".local/share/x0x"))                 // Linux
                add(File(home, "Library/Application Support/x0x"))   // macOS
            }
        }
        for (dir in candidates) {
            // api.port holds e.g. "127.0.0.1:12700" (or a bare port).
            val portFile = dir.resolve("api.port")
            val port = if (portFile.exists()) {
                portFile.readText().trim().substringAfterLast(':').toIntOrNull()
            } else {
                null
            }
            port?.let {
                val tokenFile = dir.resolve("api-token")
                val token = if (tokenFile.exists()) tokenFile.readText().trim().ifEmpty { null } else null
                return LocalDaemon(port, token)
            }
        }
        return null
    }

    private suspend fun localDaemon(): LocalDaemon? = mutex.withLock {
        // Re-resolve while not found (cheap file reads) so a daemon started
        // after the app is picked up by a later poll.
        resolved ?: resolve()?.also { resolved = it }
    }

    override val endpoint: String
        get() = resolved?.let { "x0xd · 127.0.0.1:${it.port}" }
            ?: "standalone x0xd not found — start one with `x0x start`"

    override suspend fun resolveDaemon(): Pair<String, String?>? {
        val d = localDaemon() ?: return null
        return "http://127.0.0.1:${d.port}" to d.token
    }
}

actual fun createX0xClient(): X0xClient = StandaloneX0xClient()
