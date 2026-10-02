package uk.veritaslabs.vesta.x0x

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

/**
 * The one daemon every [EmbeddedX0xClient] shares: a second start on the same data directory fails, which
 * left all but the first client (e.g. the connectivity indicator) reporting the daemon offline.
 *
 * It also enforces [X0xNetworkPolicy]: while the device is on mobile data and the user has not allowed
 * it, the daemon is shut down and [daemon] returns null (so clients report offline and their event
 * streams back off and retry); once the policy or the network changes, the next request starts it again.
 */
private object SharedDaemon {
    private val mutex = Mutex()
    private var started: EmbeddedX0x? = null
    private var guard: Boolean = false

    val port: Int? get() = started?.port

    private fun onMobileDataOnly(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun blocked(context: Context): Boolean = !X0xNetworkPolicy.useMobileData.value && onMobileDataOnly(context)

    /** Watches the policy and the default network, stopping the daemon whenever it becomes blocked. */
    private fun startGuard(context: Context) {
        if (guard) return
        guard = true
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val networkChanges = callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { trySend(Unit) }
                override fun onLost(network: Network) { trySend(Unit) }
            }
            manager.registerDefaultNetworkCallback(callback)
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            combine(X0xNetworkPolicy.useMobileData, networkChanges) { _, _ -> }.collect {
                mutex.withLock {
                    if (blocked(context)) {
                        started?.let { runCatching { it.shutdown() } }
                        started = null
                    }
                }
            }
        }
    }

    suspend fun daemon(): EmbeddedX0x? = mutex.withLock {
        val context = VestaAndroid.context ?: return@withLock null
        startGuard(context)
        if (blocked(context)) return@withLock null
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
}

private class EmbeddedX0xClient : AbstractX0xClient() {

    override val endpoint: String
        get() = SharedDaemon.port?.let { "embedded · 127.0.0.1:$it" } ?: "embedded x0x daemon"

    override suspend fun resolveDaemon(): Pair<String, String?>? =
        SharedDaemon.daemon()?.let { it.baseUrl to it.apiToken }
}

actual fun createX0xClient(): X0xClient = EmbeddedX0xClient()
