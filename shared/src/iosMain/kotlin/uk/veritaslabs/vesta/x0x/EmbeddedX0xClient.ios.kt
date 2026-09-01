@file:OptIn(ExperimentalForeignApi::class)

package uk.veritaslabs.vesta.x0x

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataTaskWithRequest

internal actual suspend fun httpGet(url: String, bearerToken: String?): String? {
    val nsUrl = NSURL.URLWithString(url)
        ?: return null
    val request = NSMutableURLRequest.requestWithURL(nsUrl)
    request.HTTPMethod = "GET"
    bearerToken?.let { request.setValue("Bearer $it", forHTTPHeaderField = "Authorization") }

    return suspendCancellableCoroutine { continuation ->
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, error ->
            val body = when {
                error != null -> null
                data == null -> null
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val bytes = data.bytes as CPointer<ByteVar>?
                    bytes?.readBytes(data.length.toInt())?.decodeToString()
                }
            }
            continuation.resume(body)
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

private class EmbeddedX0xClient : X0xClient {

    private val mutex = Mutex()
    private var started: EmbeddedX0x? = null

    override val endpoint: String
        get() = started?.let { "embedded · 127.0.0.1:${it.port}" } ?: "embedded x0x daemon"

    private fun documentsDir(): String? =
        (NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL)?.path

    private suspend fun daemon(): EmbeddedX0x? = mutex.withLock {
        started ?: run {
            val dir = documentsDir() ?: return@withLock null
            // First run generates the agent's ML-DSA-65 identity, so this can
            // take a moment — keep it off the main thread.
            withContext(Dispatchers.Default) {
                EmbeddedX0x.start(
                    dataDir = "$dir/x0x",
                    identityDir = "$dir/x0x-identity",
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
