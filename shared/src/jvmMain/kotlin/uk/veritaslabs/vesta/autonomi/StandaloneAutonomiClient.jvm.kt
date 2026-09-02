package uk.veritaslabs.vesta.autonomi

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
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
 * standalone `antd` REST daemon (see
 * [WithAutonomi/ant-sdk](https://github.com/WithAutonomi/ant-sdk#port-discovery)).
 * The REST port is read from antd's `daemon.port` file in its standard SDK
 * data directory, falling back to antd's documented default port (8082) when
 * no port file is found — the same discovery order every antd SDK uses.
 */
private class StandaloneAutonomiClient : AutonomiClient {

    private val mutex = Mutex()
    private var resolvedPort: Int? = null

    private fun sdkDataDir(): File? {
        val os = System.getProperty("os.name", "").lowercase()
        return when {
            os.contains("win") -> System.getenv("APPDATA")?.let { File(it, "ant/sdk") }
            os.contains("mac") -> System.getProperty("user.home")
                ?.let { File(it, "Library/Application Support/ant/sdk") }
            else -> {
                val xdgBase = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotEmpty() }?.let(::File)
                    ?: System.getProperty("user.home")?.let { File(it, ".local/share") }
                xdgBase?.let { File(it, "ant/sdk") }
            }
        }
    }

    private fun resolvePort(): Int {
        val portFile = sdkDataDir()?.resolve("daemon.port")
        val fromFile = portFile
            ?.takeIf { it.exists() }
            ?.readLines()
            ?.getOrNull(0)
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it in 1..65535 }
        return fromFile ?: 8082
    }

    private suspend fun port(): Int = mutex.withLock {
        resolvedPort ?: resolvePort().also { resolvedPort = it }
    }

    override val endpoint: String
        get() = "antd · 127.0.0.1:${resolvedPort ?: resolvePort()}"

    override suspend fun connectionStatus(): AutonomiStatus? {
        val body = httpGet("http://127.0.0.1:${port()}/health") ?: return null
        return AutonomiStatus.parse(body)
    }
}

actual fun createAutonomiClient(): AutonomiClient = StandaloneAutonomiClient()
