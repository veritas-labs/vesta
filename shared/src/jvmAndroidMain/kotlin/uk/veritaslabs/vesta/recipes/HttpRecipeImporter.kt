package uk.veritaslabs.vesta.recipes

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MaxPageBytes = 5 * 1024 * 1024

/** Downloads the page behind a recipe link as text; null if it can't be reached or is too big. */
fun interface PageFetcher {
    suspend fun fetch(url: String): String?
}

/** Fetches `http` and `https` pages, giving up on pages over 5 MB, slow servers and anything but a 200 answer. */
class HttpPageFetcher(private val connectTimeoutMillis: Int = 10_000, private val readTimeoutMillis: Int = 20_000) : PageFetcher {
    override suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        val address = runCatching { URI(url.trim()).takeIf { it.scheme.equals("http", true) || it.scheme.equals("https", true) }?.toURL() }.getOrNull()
            ?: return@withContext null
        val connection = runCatching { address.openConnection() as HttpURLConnection }.getOrNull() ?: return@withContext null
        try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = true
            // Many recipe sites turn away clients that don't look like a browser.
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Vesta)")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            if (connection.contentLengthLong > MaxPageBytes) return@withContext null
            val charset = Regex("""charset=["']?([\w-]+)""", RegexOption.IGNORE_CASE).find(connection.contentType.orEmpty())?.groupValues?.get(1)
                ?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
            val bytes = connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                // Read one byte past the limit so a server that lies about (or omits) its length is still caught.
                while (out.size() <= MaxPageBytes) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            if (bytes.size > MaxPageBytes) null else String(bytes, charset)
        } catch (e: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

/** Imports a recipe by downloading the page with [pages] and reading its schema.org recipe data (see [parseRecipePage]). */
class HttpRecipeImporter(private val pages: PageFetcher = HttpPageFetcher()) : RecipeImporter {
    override suspend fun import(url: String): RecipeImportResult {
        val address = url.trim()
        val html = pages.fetch(address) ?: return RecipeImportResult.Failed(RecipeImportFailure.Unreachable)
        return parseRecipePage(html, address) ?: RecipeImportResult.Failed(RecipeImportFailure.NoRecipe)
    }
}
