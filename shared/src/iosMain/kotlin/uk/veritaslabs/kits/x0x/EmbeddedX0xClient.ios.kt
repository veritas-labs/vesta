@file:OptIn(ExperimentalForeignApi::class)

package uk.veritaslabs.kits.x0x

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSObject
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue

private fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    memScoped { NSData.dataWithBytes(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong()) }
}

private fun NSData.toByteArray(): String {
    @Suppress("UNCHECKED_CAST")
    val bytes = this.bytes as CPointer<ByteVar>?
    return bytes?.readBytes(this.length.toInt())?.decodeToString() ?: ""
}

internal actual suspend fun httpRequest(method: String, url: String, bearerToken: String?, jsonBody: String?): X0xHttpResult {
    val nsUrl = NSURL.URLWithString(url) ?: throw X0xApiException(0, "invalid x0x URL: $url")
    val request = NSMutableURLRequest.requestWithURL(nsUrl)
    request.HTTPMethod = method
    bearerToken?.let { request.setValue("Bearer $it", forHTTPHeaderField = "Authorization") }
    if (jsonBody != null) {
        request.setValue("application/json", forHTTPHeaderField = "Content-Type")
        request.HTTPBody = jsonBody.encodeToByteArray().toNSData()
    }

    return suspendCancellableCoroutine { continuation ->
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            when {
                error != null -> continuation.resumeWithException(X0xApiException(0, error.localizedDescription))
                else -> {
                    val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                    continuation.resume(X0xHttpResult(status, data?.toByteArray() ?: ""))
                }
            }
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

/**
 * Streams `url` as SSE via an [NSURLSessionDataDelegateProtocol] (the completion-handler form of
 * [httpRequest] only fires once the whole response is buffered, which never happens for a stream
 * that stays open) — incoming bytes are buffered until each `\n`-terminated line is complete, and
 * each blank line after one or more `data:` lines flushes an event to [onEvent].
 */
internal actual suspend fun openSseStream(
    url: String,
    bearerToken: String?,
    onEvent: (String) -> Unit,
    onClose: (Throwable?) -> Unit,
): X0xEventStream {
    val nsUrl = NSURL.URLWithString(url) ?: throw X0xApiException(0, "invalid x0x URL: $url")
    val request = NSMutableURLRequest.requestWithURL(nsUrl)
    request.HTTPMethod = "GET"
    request.setValue("text/event-stream", forHTTPHeaderField = "Accept")
    bearerToken?.let { request.setValue("Bearer $it", forHTTPHeaderField = "Authorization") }

    return suspendCancellableCoroutine { continuation ->
        val dataLines = mutableListOf<String>()
        val buffer = StringBuilder()
        val errorBody = StringBuilder()
        var status = 200
        var opened = false
        var closedByUs = false

        lateinit var session: NSURLSession
        lateinit var task: NSURLSessionDataTask

        fun closeStream() {
            closedByUs = true
            task.cancel()
            session.finishTasksAndInvalidate()
        }

        val delegate = object : NSObject(), NSURLSessionDataDelegateProtocol {

            override fun URLSession(
                session: NSURLSession,
                dataTask: NSURLSessionDataTask,
                didReceiveResponse: NSURLResponse,
                completionHandler: (NSURLSessionResponseDisposition) -> Unit,
            ) {
                status = (didReceiveResponse as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 200
                completionHandler(NSURLSessionResponseAllow)
            }

            override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
                val chunk = didReceiveData.toByteArray()

                if (status >= 400) {
                    errorBody.append(chunk)
                    return
                }

                if (!opened) {
                    opened = true
                    continuation.resume(X0xEventStream { closeStream() })
                }

                buffer.append(chunk)
                while (true) {
                    val newlineIndex = buffer.indexOf("\n")
                    if (newlineIndex == -1) break
                    val line = buffer.substring(0, newlineIndex).removeSuffix("\r")
                    buffer.deleteRange(0, newlineIndex + 1)
                    when {
                        line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
                        line.isEmpty() && dataLines.isNotEmpty() -> {
                            val payload = dataLines.joinToString("\n")
                            dataLines.clear()
                            runCatching { onEvent(payload) }
                        }
                        line.startsWith(":") || line.isEmpty() -> Unit
                        else -> dataLines.clear()
                    }
                }
            }

            override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
                if (!opened) {
                    val message = errorBody.toString().ifBlank {
                        didCompleteWithError?.localizedDescription ?: "x0x API error (HTTP $status)"
                    }
                    continuation.resumeWithException(X0xApiException(status, message))
                } else {
                    // Fires once per task: a stream we opened has ended (error only if we didn't cancel it).
                    val failure = didCompleteWithError?.takeUnless { closedByUs }
                        ?.let { X0xApiException(status, it.localizedDescription) }
                    runCatching { onClose(failure) }
                }
            }
        }

        session = NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.defaultSessionConfiguration, delegate, null)
        task = session.dataTaskWithRequest(request)
        task.resume()

        continuation.invokeOnCancellation { closeStream() }
    }
}

private class EmbeddedX0xClient : AbstractX0xClient() {

    /**
     * Every client shares the one daemon: a second start on the same data directory fails, which
     * left all but the first client (e.g. the connectivity indicator) reporting the daemon offline.
     */
    private companion object {
        val mutex = Mutex()
        var started: EmbeddedX0x? = null
    }

    override val endpoint: String
        get() = started?.let { "embedded · 127.0.0.1:${it.port}" } ?: "embedded x0x daemon"

    private fun documentsDir(): String? =
        (platform.Foundation.NSFileManager.defaultManager.URLsForDirectory(
            platform.Foundation.NSDocumentDirectory,
            platform.Foundation.NSUserDomainMask,
        ).firstOrNull() as? NSURL)?.path

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

    override suspend fun resolveDaemon(): Pair<String, String?>? =
        daemon()?.let { it.baseUrl to it.apiToken }
}

actual fun createX0xClient(): X0xClient = EmbeddedX0xClient()
