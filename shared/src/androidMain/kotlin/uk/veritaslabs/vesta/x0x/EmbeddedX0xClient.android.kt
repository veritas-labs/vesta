package uk.veritaslabs.vesta.x0x

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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

private class EmbeddedX0xClient : X0xClient {

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

    override suspend fun health(): X0xHealth? {
        val d = daemon() ?: return null
        val body = httpGet("http://127.0.0.1:${d.port}/health", d.apiToken) ?: return null
        return X0xHealth.parse(body)
    }
}

actual fun createX0xClient(): X0xClient = EmbeddedX0xClient()
