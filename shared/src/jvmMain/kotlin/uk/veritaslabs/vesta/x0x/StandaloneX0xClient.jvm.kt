package uk.veritaslabs.vesta.x0x

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal actual suspend fun httpGet(url: String, bearerToken: String?): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 3_000
                connection.readTimeout = 3_000
                bearerToken?.let {
                    connection.setRequestProperty("Authorization", "Bearer $it")
                }
                if (connection.responseCode != 200) {
                    null
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

/**
 * Desktop client: no daemon is embedded on the JVM — the app drives the
 * standalone `x0xd` REST API. The loopback port and bearer token are read
 * from x0x's standard data directory (override with the `X0X_DATA_DIR`
 * environment variable).
 */
private class StandaloneX0xClient : X0xClient {

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

    override suspend fun health(): X0xHealth? {
        val d = localDaemon() ?: return null
        val body = httpGet("http://127.0.0.1:${d.port}/health", d.token) ?: return null
        return X0xHealth.parse(body)
    }
}

actual fun createX0xClient(): X0xClient = StandaloneX0xClient()
