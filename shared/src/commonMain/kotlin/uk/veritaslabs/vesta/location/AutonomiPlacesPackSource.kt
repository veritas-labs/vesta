package uk.veritaslabs.vesta.location

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import uk.veritaslabs.vesta.autonomi.AutonomiClient

/** Which places pack to read: a pointer (preferred, it can move to a newer release) and/or a fixed root chunk address. */
data class PlacesPackConfig(val pointer: String?, val root: String?)

/**
 * Reads the places pack (ADR-039) from Autonomi chunks: the root, the directory and the category table ([load]), then the data
 * chunks a set of localities needs ([fetchUnits]). Every chunk is read from [cache] first and stored only after it has decoded,
 * so a bad chunk is never kept; with the chunks cached everything works offline.
 *
 * Verification is as for [AutonomiLocalityPackSource]: the factories pass [Blake3ChunkVerifier], so a chunk whose BLAKE3 hash is
 * not its address is neither used nor cached.
 */
class AutonomiPlacesPackSource(
    private val client: AutonomiClient,
    private val config: PlacesPackConfig,
    private val cache: ChunkCache,
    private val inflater: Inflater,
    private val verifier: ChunkVerifier = ChunkVerifier.None,
) : PlacesPackSource {

    private class Failure(message: String) : Exception(message)

    private var lastResolved: String? = null

    override suspend fun isCachedLocally(): Boolean {
        val rootAddress = (if (config.pointer != null) lastResolved else null) ?: config.root ?: return false
        val root = cache.get(rootAddress)?.let { runCatching { PlacesRoot.parse(it) }.getOrNull() } ?: return false
        return cache.get(root.directoryAddress) != null && cache.get(root.categoriesAddress) != null
    }

    override suspend fun preflight(): PlacesPackState? = when {
        config.pointer == null && config.root == null -> PlacesPackState.Unconfigured
        !client.supportsChunks && !isCachedLocally() -> PlacesPackState.NotAvailableOnDevice
        else -> null
    }

    override suspend fun load(): PlacesPackState {
        preflight()?.let { return it }
        return try {
            val fetched = mutableMapOf<String, ByteArray>()
            val root = PlacesRoot.parse(chunk(resolveRootAddress(), fetched))
            val directory = PlacesDirectory.parse(chunk(root.directoryAddress, fetched), inflater)
            val categories = CategoryTable.parse(chunk(root.categoriesAddress, fetched), inflater)
            require(directory.chunkCount == root.chunkAddresses.size) {
                "directory has ${directory.chunkCount} chunks but the root lists ${root.chunkAddresses.size}"
            }
            fetched.forEach { (address, bytes) -> cache.put(address, bytes) }
            PlacesPackState.Ready(root, directory, categories)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Failure) {
            PlacesPackState.Unreachable(e.message ?: "places data unavailable")
        } catch (e: Exception) {
            PlacesPackState.Unreachable("places data did not decode: ${e.message}")
        }
    }

    override suspend fun fetchUnits(
        ready: PlacesPackState.Ready,
        rows: Collection<Int>,
        includeNearby: Boolean,
        onProgress: (fetchedChunks: Int, totalChunks: Int, fetchedBytes: Long) -> Unit,
    ): PlacesFetchResult {
        val directory = ready.directory
        fun needed(row: Int) = directory.rangesFor(row).filter { includeNearby || !it.nearby }
        // Chunks in the order the rows first need them, so the nearest localities arrive first.
        val order = rows.flatMap { row -> needed(row).map { it.chunk } }.distinct()
        val decoded = mutableMapOf<PlaceRange, List<PlaceRecord>>()
        val lock = Mutex()
        var done = 0
        var bytes = 0L
        var failed = 0
        val permits = Semaphore(MAX_CONCURRENT_CHUNKS)

        coroutineScope {
            order.map { index ->
                async {
                    permits.withPermit {
                        val ranges = rows.flatMap { needed(it) }.filter { it.chunk == index }.distinct()
                        val result = readChunk(ready.root.chunkAddresses[index], ranges)
                        lock.withLock {
                            done++
                            if (result == null) {
                                failed++
                            } else {
                                bytes += result.second
                                decoded.putAll(result.first)
                            }
                            onProgress(done, order.size, bytes)
                        }
                    }
                }
            }.awaitAll()
        }
        val places = rows.associateWith { row -> needed(row).flatMap { decoded[it].orEmpty() } }
        return PlacesFetchResult(places, failed)
    }

    /** The decoded ranges of one data chunk and the bytes downloaded for it, or null when it could not be had or decoded. */
    private suspend fun readChunk(address: String, ranges: List<PlaceRange>): Pair<Map<PlaceRange, List<PlaceRecord>>, Long>? {
        cache.get(address)?.takeIf { verifier.matches(address, it) }?.let { cached ->
            decode(cached, ranges)?.let { return it to 0L }
        }
        val downloaded = try {
            client.chunkGet(address)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        if (!verifier.matches(address, downloaded)) return null
        val result = decode(downloaded, ranges) ?: return null
        cache.put(address, downloaded)
        return result to downloaded.size.toLong()
    }

    private fun decode(compressed: ByteArray, ranges: List<PlaceRange>): Map<PlaceRange, List<PlaceRecord>>? = try {
        val chunk = PlaceUnit.decompress(compressed, inflater)
        ranges.associateWith { PlaceUnit.decodeRange(chunk, it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun resolveRootAddress(): String {
        config.pointer?.let { pointer ->
            client.pointerResolve(pointer)?.let {
                lastResolved = it
                return it
            }
        }
        return config.root ?: throw Failure("places pointer could not be resolved")
    }

    private suspend fun chunk(address: String, fetched: MutableMap<String, ByteArray>): ByteArray {
        cache.get(address)?.takeIf { verifier.matches(address, it) }?.let { return it }
        val bytes = client.chunkGet(address) ?: throw Failure("chunk $address could not be fetched")
        if (!verifier.matches(address, bytes)) throw Failure("chunk failed verification")
        fetched[address] = bytes
        return bytes
    }

    private companion object {
        const val MAX_CONCURRENT_CHUNKS = 4
    }
}
