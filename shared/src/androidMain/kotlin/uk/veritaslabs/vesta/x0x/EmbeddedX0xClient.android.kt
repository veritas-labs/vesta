package uk.veritaslabs.vesta.x0x

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-level init installed by `MainActivity` before the UI is created, so
 * shared code can reach app-private storage: the embedded x0x daemon's data
 * and identity directories, and the local SQLite database
 * (`uk.veritaslabs.vesta.db.DatabaseDriverFactory`) both live there — never
 * written anywhere else.
 */
object VestaAndroid {
    @Volatile
    var context: Context? = null
        private set

    val filesDir: File?
        get() = context?.filesDir

    fun install(context: Context) {
        this.context = context.applicationContext
    }
}

internal actual suspend fun httpRequest(method: String, url: String, bearerToken: String?, jsonBody: String?): X0xHttpResult =
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            bearerToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(jsonBody.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            X0xHttpResult(status, body)
        } finally {
            connection.disconnect()
        }
    }

internal actual suspend fun openSseStream(
    url: String,
    bearerToken: String?,
    onEvent: (String) -> Unit,
    onClose: (Throwable?) -> Unit,
): X0xEventStream =
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5_000
        connection.readTimeout = 0 // long-lived stream
        connection.setRequestProperty("Accept", "text/event-stream")
        bearerToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }

        val status = connection.responseCode
        if (status >= 400) {
            val body = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            connection.disconnect()
            throw X0xApiException(status, body.ifBlank { "x0x API error (HTTP $status)" })
        }

        val closed = AtomicBoolean(false)
        val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
        val thread = Thread({
            var failure: Throwable? = null
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
            } catch (e: Exception) {
                // Stream ended or was closed; only a failure we didn't ask for is an error.
                if (!closed.get()) failure = e
            } finally {
                runCatching { reader.close() }
                connection.disconnect()
                runCatching { onClose(failure) }
            }
        }, "x0x-sse-$url").apply {
            isDaemon = true
            start()
        }

        X0xEventStream {
            if (closed.compareAndSet(false, true)) {
                runCatching { reader.close() }
                connection.disconnect()
                thread.interrupt()
            }
        }
    }

private class EmbeddedX0xClient : AbstractX0xClient() {

    private val mutex = Mutex()
    private var started: EmbeddedX0x? = null

    override val endpoint: String
        get() = started?.let { "embedded · 127.0.0.1:${it.port}" } ?: "embedded x0x daemon"

    private suspend fun daemon(): EmbeddedX0x? = mutex.withLock {
        started ?: run {
            val appDir = VestaAndroid.filesDir ?: return@withLock null
            // First run generates the agent's ML-DSA-65 identity, so this can
            // take a moment — keep it off the UI thread.
            withContext(Dispatchers.IO) {
                EmbeddedX0x.start(
                    dataDir = appDir.resolve("x0x").path,
                    identityDir = appDir.resolve("x0x-identity").path,
                )
            }
        }?.also { started = it }
    }

    override suspend fun resolveDaemon(): Pair<String, String?>? =
        daemon()?.let { it.baseUrl to it.apiToken }
}

actual fun createX0xClient(): X0xClient = EmbeddedX0xClient()
