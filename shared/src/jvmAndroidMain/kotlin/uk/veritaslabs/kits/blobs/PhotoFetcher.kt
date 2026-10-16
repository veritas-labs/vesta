package uk.veritaslabs.kits.blobs

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface PhotoFetchResult {
    class Fetched(val bytes: ByteArray) : PhotoFetchResult

    class Failed(val reason: PhotoRejection) : PhotoFetchResult
}

/** Downloads the file behind a photo link; what it got is then encoded like any picked photo. */
internal fun interface PhotoFetcher {
    suspend fun fetch(url: String): PhotoFetchResult
}

/** Fetches `http` and `https` links, giving up on files over [PhotoLimits.MaxInputBytes], slow servers and anything but a 200 answer. */
internal class HttpPhotoFetcher(private val connectTimeoutMillis: Int = 10_000, private val readTimeoutMillis: Int = 20_000) : PhotoFetcher {
    override suspend fun fetch(url: String): PhotoFetchResult = withContext(Dispatchers.IO) {
        val address = runCatching { URI(url.trim()).takeIf { it.scheme.equals("http", true) || it.scheme.equals("https", true) }?.toURL() }.getOrNull()
            ?: return@withContext PhotoFetchResult.Failed(PhotoRejection.Unreachable)
        val connection = runCatching { address.openConnection() as HttpURLConnection }.getOrNull()
            ?: return@withContext PhotoFetchResult.Failed(PhotoRejection.Unreachable)
        try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Vesta")
            connection.setRequestProperty("Accept", "image/*")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext PhotoFetchResult.Failed(PhotoRejection.Unreachable)
            if (connection.contentLengthLong > PhotoLimits.MaxInputBytes) return@withContext PhotoFetchResult.Failed(PhotoRejection.TooLarge)
            // Read one byte past the limit so a server that lies about (or omits) its length is still caught.
            val bytes = connection.inputStream.use { it.readUpTo(PhotoLimits.MaxInputBytes.toInt() + 1) }
            if (bytes.size > PhotoLimits.MaxInputBytes) PhotoFetchResult.Failed(PhotoRejection.TooLarge) else PhotoFetchResult.Fetched(bytes)
        } catch (e: java.io.IOException) {
            PhotoFetchResult.Failed(PhotoRejection.Unreachable)
        } finally {
            connection.disconnect()
        }
    }
}

/** Up to [limit] bytes of this stream (`readNBytes(int)` needs Android 13). */
private fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
