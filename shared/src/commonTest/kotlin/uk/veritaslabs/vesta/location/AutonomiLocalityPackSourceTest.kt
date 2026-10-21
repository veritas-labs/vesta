package uk.veritaslabs.vesta.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.vesta.autonomi.AutonomiClient
import uk.veritaslabs.vesta.autonomi.AutonomiStatus

class AutonomiLocalityPackSourceTest {

    private val rootAddress = "cd".repeat(32)
    private val namesAddress = "ab".repeat(32)
    private val adjacencyAddress = "01".repeat(32)
    private val pointerAddress = "ee".repeat(32)

    private class FakeClient(
        val chunks: MutableMap<String, ByteArray> = mutableMapOf(),
        val pointers: Map<String, String> = emptyMap(),
        override val supportsChunks: Boolean = true,
    ) : AutonomiClient {
        var calls = 0
        override val endpoint = "fake"
        override suspend fun connectionStatus(): AutonomiStatus? = null
        override suspend fun chunkGet(addressHex: String): ByteArray? = chunks[addressHex].also { calls++ }
        override suspend fun pointerResolve(addressHex: String): String? = pointers[addressHex].also { calls++ }
    }

    private class MemoryCache : ChunkCache {
        val entries = mutableMapOf<String, ByteArray>()
        override fun get(address: String) = entries[address]
        override fun put(address: String, bytes: ByteArray) {
            entries[address] = bytes
        }
    }

    private fun goodChunks() = mutableMapOf(
        rootAddress to TinyPack.rootChunk(),
        namesAddress to TinyPack.namesChunk(),
        adjacencyAddress to TinyPack.adjacencyChunk(),
    )

    private fun source(
        client: AutonomiClient,
        cache: ChunkCache = MemoryCache(),
        config: LocalityPackConfig = LocalityPackConfig(pointer = null, root = rootAddress),
        verifier: ChunkVerifier = ChunkVerifier.None,
    ) = AutonomiLocalityPackSource(client, config, cache, TinyPack.identity, verifier)

    @Test
    fun loadsThePackFromTheThreeChunks() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks()))

        // Act
        val state = source.load()

        // Assert
        val ready = assertIs<LocalityPackState.Ready>(state)
        assertEquals("GB", ready.root.country)
        assertEquals(TinyPack.specs.size, ready.pack.size)
    }

    @Test
    fun aSecondLoadMakesNoClientCalls() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val source = source(client)
        source.load()
        val callsAfterFirst = client.calls

        // Act
        val state = source.load()

        // Assert
        assertIs<LocalityPackState.Ready>(state)
        assertEquals(callsAfterFirst, client.calls)
        assertTrue(source.isCachedLocally())
    }

    @Test
    fun isNotCachedBeforeTheFirstLoad() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks()))

        // Act
        val cached = source.isCachedLocally()

        // Assert
        assertFalse(cached)
    }

    @Test
    fun loadsOfflineOnceCached() = runTest {
        // Arrange
        val cache = MemoryCache()
        source(FakeClient(goodChunks()), cache).load()
        val offline = FakeClient()

        // Act
        val state = source(offline, cache).load()

        // Assert
        assertIs<LocalityPackState.Ready>(state)
        assertEquals(0, offline.calls)
    }

    @Test
    fun isUnconfiguredWithNeitherPointerNorRoot() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks()), config = LocalityPackConfig(null, null))

        // Act
        val state = source.load()

        // Assert
        assertEquals(LocalityPackState.Unconfigured, state)
    }

    @Test
    fun prefersThePointerTarget() = runTest {
        // Arrange
        val client = FakeClient(goodChunks(), pointers = mapOf(pointerAddress to rootAddress))
        val config = LocalityPackConfig(pointer = pointerAddress, root = "99".repeat(32))
        val source = source(client, config = config)

        // Act
        val state = source.load()

        // Assert
        assertIs<LocalityPackState.Ready>(state)
    }

    @Test
    fun fallsBackToTheRootWhenThePointerDoesNotResolve() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val config = LocalityPackConfig(pointer = pointerAddress, root = rootAddress)
        val source = source(client, config = config)

        // Act
        val state = source.load()

        // Assert
        assertIs<LocalityPackState.Ready>(state)
    }

    @Test
    fun aPointerAloneThatDoesNotResolveIsUnreachableAndNotCached() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks()), config = LocalityPackConfig(pointer = pointerAddress, root = null))

        // Act
        val state = source.load()

        // Assert
        assertIs<LocalityPackState.Unreachable>(state)
        assertFalse(source.isCachedLocally())
    }

    @Test
    fun aPointerTargetIsRememberedForTheCachedCheck() = runTest {
        // Arrange
        val client = FakeClient(goodChunks(), pointers = mapOf(pointerAddress to rootAddress))
        val source = source(client, config = LocalityPackConfig(pointer = pointerAddress, root = null))

        // Act
        source.load()

        // Assert
        assertTrue(source.isCachedLocally())
    }

    @Test
    fun aChunkThatDoesNotDecodeIsUnreachable() = runTest {
        // Arrange
        val chunks = goodChunks().apply { put(namesAddress, TinyPack.namesChunk(magic = "XXXX")) }
        val source = source(FakeClient(chunks))

        // Act
        val state = source.load()

        // Assert
        assertIs<LocalityPackState.Unreachable>(state)
    }

    @Test
    fun aPackThatDoesNotDecodeLeavesNothingInTheCache() = runTest {
        // Arrange
        val chunks = goodChunks().apply { put(namesAddress, TinyPack.namesChunk(magic = "XXXX")) }
        val cache = MemoryCache()

        // Act
        source(FakeClient(chunks), cache).load()

        // Assert
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun aRootThatDoesNotDecodeIsUnreachable() = runTest {
        // Arrange
        val chunks = goodChunks().apply { put(rootAddress, byteArrayOf(1, 2, 3)) }
        val source = source(FakeClient(chunks))

        // Act
        val state = source.load()

        // Assert
        assertIs<LocalityPackState.Unreachable>(state)
    }

    @Test
    fun aVerifierThatRejectsMakesTheLoadUnreachable() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks()), verifier = { _, _ -> false })

        // Act
        val state = source.load()

        // Assert
        assertEquals(LocalityPackState.Unreachable("chunk failed verification"), state)
    }

    @Test
    fun aClientWithoutChunkSupportAndAnEmptyCacheIsNotAvailableOnDevice() = runTest {
        // Arrange
        val source = source(FakeClient(supportsChunks = false))

        // Act
        val state = source.load()

        // Assert
        assertEquals(LocalityPackState.NotAvailableOnDevice, state)
    }

    @Test
    fun aClientWithoutChunkSupportStillUsesACache() = runTest {
        // Arrange
        val cache = MemoryCache()
        source(FakeClient(goodChunks()), cache).load()

        // Act
        val state = source(FakeClient(supportsChunks = false), cache).load()

        // Assert
        assertIs<LocalityPackState.Ready>(state)
    }

    @Test
    fun anUnreachableClientWithAnEmptyCacheIsUnreachable() = runTest {
        // Arrange
        val source = source(FakeClient())

        // Act
        val state = source.load()

        // Assert
        assertIs<LocalityPackState.Unreachable>(state)
    }

    @Test
    fun preflightIsUnconfiguredWithNeitherPointerNorRootAndMakesNoCalls() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val source = source(client, config = LocalityPackConfig(pointer = null, root = null))

        // Act
        val state = source.preflight()

        // Assert
        assertEquals(LocalityPackState.Unconfigured, state)
        assertEquals(0, client.calls)
    }

    @Test
    fun preflightIsNotAvailableWhenTheClientCannotFetchChunksAndNothingIsCached() = runTest {
        // Arrange
        val client = FakeClient(goodChunks(), supportsChunks = false)
        val source = source(client)

        // Act
        val state = source.preflight()

        // Assert
        assertEquals(LocalityPackState.NotAvailableOnDevice, state)
        assertEquals(0, client.calls)
    }

    @Test
    fun preflightCanProceedWhenConfiguredAndTheClientSupportsChunks() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val source = source(client)

        // Act
        val state = source.preflight()

        // Assert
        assertEquals(null, state)
        assertEquals(0, client.calls)
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun withTheBlake3VerifierChunksThatMatchTheirAddressLoad() = runTest {
        // Arrange: the names and adjacency addresses sit at offsets 23 and 55 of the root chunk
        val names = TinyPack.namesChunk()
        val adjacency = TinyPack.adjacencyChunk()
        val root = TinyPack.rootChunk()
        Blake3.hash(names).copyInto(root, 23)
        Blake3.hash(adjacency).copyInto(root, 55)
        val rootAddress = hex(Blake3.hash(root))
        val chunks = mutableMapOf(
            rootAddress to root,
            hex(Blake3.hash(names)) to names,
            hex(Blake3.hash(adjacency)) to adjacency,
        )
        val source = source(FakeClient(chunks), config = LocalityPackConfig(null, rootAddress), verifier = Blake3ChunkVerifier)

        // Act
        val state = source.load()

        // Assert
        assertIs<LocalityPackState.Ready>(state)
    }

    @Test
    fun withTheBlake3VerifierAChunkThatDoesNotMatchItsAddressIsRejectedAndNotCached() = runTest {
        // Arrange
        val cache = MemoryCache()
        val source = source(FakeClient(goodChunks()), cache, verifier = Blake3ChunkVerifier)

        // Act
        val state = source.load()

        // Assert
        assertEquals(LocalityPackState.Unreachable("chunk failed verification"), state)
        assertTrue(cache.entries.isEmpty())
    }
}
