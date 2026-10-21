package uk.veritaslabs.vesta.location

import kotlin.coroutines.cancellation.CancellationException
import uk.veritaslabs.vesta.autonomi.AutonomiClient

/** Which pack to read: a pointer (preferred, it can move to a newer release) and/or a fixed root chunk address. */
data class LocalityPackConfig(val pointer: String?, val root: String?)

/** Stores chunks by address. Chunks are immutable and content-addressed, so an entry never goes stale. */
interface ChunkCache {
    fun get(address: String): ByteArray?
    fun put(address: String, bytes: ByteArray)
}

/** Checks that a chunk's bytes belong to its address. */
fun interface ChunkVerifier {
    fun matches(addressHex: String, bytes: ByteArray): Boolean

    companion object {
        /** Accepts everything. */
        val None = ChunkVerifier { _, _ -> true }
    }
}

/**
 * Reads the locality pack (ADR-039) from Autonomi chunks: the root chunk, then the names and adjacency chunks it
 * lists, each read from [cache] first and stored after a successful fetch. A configured [LocalityPackConfig.pointer]
 * is resolved through the client on every load (it can move, so its target is never cached) and, when the client
 * cannot resolve it, [LocalityPackConfig.root] is used. With the chunks cached, [load] works offline.
 *
 * Known gap: an Autonomi chunk address is BLAKE3 of its content, but the repo has no BLAKE3 and adds none for this,
 * so by default nothing is verified ([ChunkVerifier.None]) and a node could serve wrong bytes for an address. The
 * decoders reject malformed chunks, but a well-formed forgery would pass. A [verifier] that implements BLAKE3 closes it.
 */
class AutonomiLocalityPackSource(
    private val client: AutonomiClient,
    private val config: LocalityPackConfig,
    private val cache: ChunkCache,
    private val inflater: Inflater,
    private val verifier: ChunkVerifier = ChunkVerifier.None,
) : LocalityPackSource {

    private class Failure(message: String) : Exception(message)

    /** The last address the pointer resolved to, so [isCachedLocally] can answer without the network. */
    private var lastResolved: String? = null

    override suspend fun isCachedLocally(): Boolean {
        val rootAddress = (if (config.pointer != null) lastResolved else null) ?: config.root ?: return false
        val root = cache.get(rootAddress)?.let { runCatching { LocalityRoot.parse(it) }.getOrNull() } ?: return false
        return cache.get(root.namesAddress) != null && cache.get(root.adjacencyAddress) != null
    }

    override suspend fun load(): LocalityPackState {
        if (config.pointer == null && config.root == null) return LocalityPackState.Unconfigured
        if (!client.supportsChunks && !isCachedLocally()) return LocalityPackState.NotAvailableOnDevice
        return try {
            val rootAddress = resolveRootAddress()
            val root = LocalityRoot.parse(chunk(rootAddress))
            val names = chunk(root.namesAddress)
            val adjacency = chunk(root.adjacencyAddress)
            LocalityPackState.Ready(root, LocalityPack.parse(names, adjacency, inflater))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Failure) {
            LocalityPackState.Unreachable(e.message ?: "locality data unavailable")
        } catch (e: Exception) {
            LocalityPackState.Unreachable("locality data did not decode: ${e.message}")
        }
    }

    private suspend fun resolveRootAddress(): String {
        config.pointer?.let { pointer ->
            client.pointerResolve(pointer)?.let {
                lastResolved = it
                return it
            }
        }
        return config.root ?: throw Failure("locality pointer could not be resolved")
    }

    private suspend fun chunk(address: String): ByteArray {
        cache.get(address)?.takeIf { verifier.matches(address, it) }?.let { return it }
        val bytes = client.chunkGet(address) ?: throw Failure("chunk $address could not be fetched")
        if (!verifier.matches(address, bytes)) throw Failure("chunk failed verification")
        cache.put(address, bytes)
        return bytes
    }
}
