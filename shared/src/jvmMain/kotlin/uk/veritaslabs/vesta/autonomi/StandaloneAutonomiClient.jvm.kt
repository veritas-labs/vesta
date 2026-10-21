package uk.veritaslabs.vesta.autonomi

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private suspend fun httpGet(url: String, readTimeoutMs: Int = 3_000): String? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = readTimeoutMs
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

/** A chunk fetch can cross the network to several nodes, so it gets far longer than the health check. */
private const val CHUNK_READ_TIMEOUT_MS = 30_000

@Serializable
private data class ChunkDto(val data: String? = null)

@Serializable
private data class PointerResolveDto(val target: String? = null)

private val json = Json { ignoreUnknownKeys = true }

private fun isAddress(hex: String) = hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

/**
 * Desktop client: no daemon is embedded on the JVM — the app drives the
 * standalone `antd` REST daemon (see
 * [WithAutonomi/ant-sdk](https://github.com/WithAutonomi/ant-sdk#port-discovery)).
 * The REST port is read from antd's `daemon.port` file in its standard SDK
 * data directory, falling back to antd's documented default port (8082) when
 * no port file is found — the same discovery order every antd SDK uses.
 */
internal class StandaloneAutonomiClient(
    /** Fixed REST port; null means env `VESTA_ANTD_PORT`, then `daemon.port` discovery. */
    private val fixedPort: Int? = null,
) : AutonomiClient {

    private val mutex = Mutex()
    private var resolvedPort: Int? = fixedPort

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
        System.getenv("VESTA_ANTD_PORT")?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }?.let { return it }
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

    override val supportsChunks: Boolean = true

    override suspend fun chunkGet(addressHex: String): ByteArray? {
        if (!isAddress(addressHex)) return null
        val body = httpGet("http://127.0.0.1:${port()}/v1/chunks/$addressHex", CHUNK_READ_TIMEOUT_MS) ?: return null
        val data = runCatching { json.decodeFromString<ChunkDto>(body).data }.getOrNull() ?: return null
        return runCatching { Base64.getDecoder().decode(data) }.getOrNull()
    }

    /** A 404 (route absent, as in antd 0.15.0) or any other failure is "unsupported", not an error. */
    override suspend fun pointerResolve(addressHex: String): String? {
        if (!isAddress(addressHex)) return null
        val body = httpGet("http://127.0.0.1:${port()}/v1/pointers/$addressHex/resolve", CHUNK_READ_TIMEOUT_MS) ?: return null
        return runCatching { json.decodeFromString<PointerResolveDto>(body).target }.getOrNull()?.takeIf(::isAddress)
    }
}

actual fun createAutonomiClient(): AutonomiClient = StandaloneAutonomiClient()
