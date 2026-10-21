package uk.veritaslabs.vesta.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.vesta.autonomi.AutonomiClient
import uk.veritaslabs.vesta.autonomi.AutonomiStatus

@OptIn(ExperimentalCoroutinesApi::class)
class AutonomiPlacesPackSourceTest {

    private val rootAddress = "cd".repeat(32)
    private val directoryAddress = "d1".repeat(32)
    private val categoriesAddress = "c1".repeat(32)
    private val chunk0 = "01".repeat(32)
    private val chunk1 = "02".repeat(32)

    private open class FakeClient(
        val chunks: MutableMap<String, ByteArray> = mutableMapOf(),
        override val supportsChunks: Boolean = true,
    ) : AutonomiClient {
        var calls = 0
        val requested = mutableListOf<String>()
        override val endpoint = "fake"
        override suspend fun connectionStatus(): AutonomiStatus? = null
        override suspend fun chunkGet(addressHex: String): ByteArray? {
            calls++
            requested += addressHex
            return chunks[addressHex]
        }
        override suspend fun pointerResolve(addressHex: String): String? = null
    }

    private class MemoryCache : ChunkCache {
        val entries = mutableMapOf<String, ByteArray>()
        override fun get(address: String) = entries[address]
        override fun put(address: String, bytes: ByteArray) {
            entries[address] = bytes
        }
    }

    private val built = TinyPlaces.build()

    private fun goodChunks() = mutableMapOf(
        rootAddress to TinyPlaces.rootChunk(),
        directoryAddress to TinyPlaces.directory(),
        categoriesAddress to TinyPlaces.categoryTable(),
        chunk0 to built.chunks[0],
        chunk1 to built.chunks[1],
    )

    private fun source(
        client: AutonomiClient,
        cache: ChunkCache = MemoryCache(),
        config: PlacesPackConfig = PlacesPackConfig(pointer = null, root = rootAddress),
    ) = AutonomiPlacesPackSource(client, config, cache, TinyPlaces.identity)

    private suspend fun ready(source: AutonomiPlacesPackSource) = assertIs<PlacesPackState.Ready>(source.load())

    @Test
    fun loadsRootDirectoryAndCategories() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks()))

        // Act
        val state = source.load()

        // Assert
        val ready = assertIs<PlacesPackState.Ready>(state)
        assertEquals("GB", ready.root.country)
        assertEquals(4, ready.directory.size)
        assertEquals(TinyPlaces.categories.size, ready.categories.categoryCount)
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
        assertIs<PlacesPackState.Ready>(state)
        assertEquals(callsAfterFirst, client.calls)
        assertTrue(source.isCachedLocally())
    }

    @Test
    fun loadsOfflineFromTheCache() = runTest {
        // Arrange
        val cache = MemoryCache()
        source(FakeClient(goodChunks()), cache).load()
        val offline = source(FakeClient(supportsChunks = false), cache)

        // Act
        val state = offline.load()

        // Assert
        assertIs<PlacesPackState.Ready>(state)
        assertNull(offline.preflight())
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
    fun isUnconfiguredWithNeitherPointerNorRoot() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks()), config = PlacesPackConfig(null, null))

        // Act
        val state = source.load()

        // Assert
        assertEquals(PlacesPackState.Unconfigured, state)
        assertEquals(PlacesPackState.Unconfigured, source.preflight())
    }

    @Test
    fun isNotAvailableOnADeviceWithoutChunkFetchingAndNothingCached() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks(), supportsChunks = false))

        // Act
        val state = source.load()

        // Assert
        assertEquals(PlacesPackState.NotAvailableOnDevice, state)
        assertEquals(PlacesPackState.NotAvailableOnDevice, source.preflight())
    }

    @Test
    fun anUndecodableChunkIsUnreachableAndLeavesNothingCached() = runTest {
        // Arrange
        val chunks = goodChunks().apply { put(categoriesAddress, TinyPlaces.categoryTable(magic = "XXXX")) }
        val cache = MemoryCache()
        val source = source(FakeClient(chunks), cache)

        // Act
        val state = source.load()

        // Assert
        assertIs<PlacesPackState.Unreachable>(state)
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun aMissingChunkIsUnreachable() = runTest {
        // Arrange
        val chunks = goodChunks().apply { remove(directoryAddress) }
        val source = source(FakeClient(chunks))

        // Act
        val state = source.load()

        // Assert
        assertIs<PlacesPackState.Unreachable>(state)
    }

    @Test
    fun fetchesOnlyTheChunksTheRowsNeed() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val source = source(client)
        val ready = ready(source)
        client.requested.clear()

        // Act
        val result = source.fetchUnits(ready, listOf(1), includeNearby = true)

        // Assert
        assertEquals(listOf(chunk1), client.requested)
        assertEquals(0, result.failedChunks)
        assertEquals(listOf("Place 5", "Place 6", "Place 7"), result.places.getValue(1).map { it.name })
    }

    @Test
    fun returnsInsideThenNearbyPlacesPerRow() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks()))
        val ready = ready(source)

        // Act
        val result = source.fetchUnits(ready, listOf(0, 2), includeNearby = true)

        // Assert
        assertEquals(listOf(false, false, false, true), result.places.getValue(0).map { it.nearby })
        assertEquals(listOf("Café Nero", "Place 2", "Place 3", "Place 4"), result.places.getValue(0).map { it.name })
        assertEquals(listOf("Nearby Pub", "Place 9"), result.places.getValue(2).map { it.name })
        assertEquals(listOf(true, true), result.places.getValue(2).map { it.nearby })
    }

    @Test
    fun excludingNearbySkipsChunksOnlyNearbyPlacesNeed() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val source = source(client)
        val ready = ready(source)
        client.requested.clear()

        // Act
        val result = source.fetchUnits(ready, listOf(2), includeNearby = false)

        // Assert
        assertTrue(client.requested.isEmpty())
        assertEquals(emptyList(), result.places.getValue(2))
        assertEquals(0, result.failedChunks)
    }

    @Test
    fun excludingNearbyKeepsInsidePlaces() = runTest {
        // Arrange
        val source = source(FakeClient(goodChunks()))
        val ready = ready(source)

        // Act
        val result = source.fetchUnits(ready, listOf(0), includeNearby = false)

        // Assert
        assertEquals(3, result.places.getValue(0).size)
        assertTrue(result.places.getValue(0).none { it.nearby })
    }

    @Test
    fun aRowWithNoPlacesFetchesNothing() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val source = source(client)
        val ready = ready(source)
        client.requested.clear()
        val progress = mutableListOf<Triple<Int, Int, Long>>()

        // Act
        val result = source.fetchUnits(ready, listOf(3), includeNearby = true) { a, b, c -> progress += Triple(a, b, c) }

        // Assert
        assertTrue(client.requested.isEmpty())
        assertTrue(progress.isEmpty())
        assertEquals(emptyList(), result.places.getValue(3))
    }

    @Test
    fun reportsProgressInChunksAndBytes() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val source = source(client)
        val ready = ready(source)
        val progress = mutableListOf<Triple<Int, Int, Long>>()

        // Act
        source.fetchUnits(ready, listOf(0, 1), includeNearby = true) { a, b, c -> progress += Triple(a, b, c) }

        // Assert
        assertEquals(listOf(1, 2), progress.map { it.first })
        assertEquals(listOf(2, 2), progress.map { it.second })
        assertEquals(built.chunks[0].size.toLong(), progress[0].third)
        assertEquals((built.chunks[0].size + built.chunks[1].size).toLong(), progress[1].third)
    }

    @Test
    fun fetchesChunksInTheOrderTheRowsFirstNeedThem() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val source = source(client)
        val ready = ready(source)
        client.requested.clear()

        // Act
        source.fetchUnits(ready, listOf(1, 0), includeNearby = true)

        // Assert
        assertEquals(listOf(chunk1, chunk0), client.requested)
    }

    @Test
    fun aSecondFetchMakesNoClientCalls() = runTest {
        // Arrange
        val client = FakeClient(goodChunks())
        val source = source(client)
        val ready = ready(source)
        source.fetchUnits(ready, listOf(0, 1, 2), includeNearby = true)
        val calls = client.calls
        val progress = mutableListOf<Long>()

        // Act
        val result = source.fetchUnits(ready, listOf(0, 1, 2), includeNearby = true) { _, _, bytes -> progress += bytes }

        // Assert
        assertEquals(calls, client.calls)
        assertEquals(0, result.failedChunks)
        assertEquals(listOf(0L, 0L), progress)
    }

    @Test
    fun aFailingChunkGivesAPartialResultAndTheOtherChunkIsCached() = runTest {
        // Arrange
        val chunks = goodChunks().apply { remove(chunk1) }
        val cache = MemoryCache()
        val source = source(FakeClient(chunks), cache)
        val ready = ready(source)

        // Act
        val result = source.fetchUnits(ready, listOf(0, 1), includeNearby = true)

        // Assert
        assertEquals(1, result.failedChunks)
        assertEquals(4, result.places.getValue(0).size)
        assertEquals(emptyList(), result.places.getValue(1))
        assertTrue(cache.entries.containsKey(chunk0))
        assertFalse(cache.entries.containsKey(chunk1))
    }

    @Test
    fun aChunkThatThrowsCountsAsFailed() = runTest {
        // Arrange
        val throwing = object : FakeClient(goodChunks()) {
            override suspend fun chunkGet(addressHex: String): ByteArray? =
                if (addressHex == chunk1) throw IllegalStateException("boom") else super.chunkGet(addressHex)
        }
        val source = source(throwing)
        val ready = ready(source)

        // Act
        val result = source.fetchUnits(ready, listOf(0, 1), includeNearby = true)

        // Assert
        assertEquals(1, result.failedChunks)
        assertEquals(4, result.places.getValue(0).size)
    }

    @Test
    fun aDataChunkThatDoesNotDecodeIsNeverCached() = runTest {
        // Arrange
        val chunks = goodChunks().apply { put(chunk1, "VGDX".encodeToByteArray() + ByteArray(8)) }
        val cache = MemoryCache()
        val source = source(FakeClient(chunks), cache)
        val ready = ready(source)

        // Act
        val result = source.fetchUnits(ready, listOf(0, 1), includeNearby = true)

        // Assert
        assertEquals(1, result.failedChunks)
        assertFalse(cache.entries.containsKey(chunk1))
        assertTrue(cache.entries.containsKey(chunk0))
    }

    @Test
    fun aChunkWhoseRangesDoNotDecodeIsNeverCached() = runTest {
        // Arrange
        val chunks = goodChunks().apply { put(chunk1, "VGD1".encodeToByteArray() + ByteArray(3)) }
        val cache = MemoryCache()
        val source = source(FakeClient(chunks), cache)
        val ready = ready(source)

        // Act
        val result = source.fetchUnits(ready, listOf(1), includeNearby = true)

        // Assert
        assertEquals(1, result.failedChunks)
        assertFalse(cache.entries.containsKey(chunk1))
    }

    @Test
    fun cancellingMidFetchKeepsTheChunksAlreadyCached() = runTest {
        // Arrange
        val gate = CompletableDeferred<Unit>()
        val slow = object : FakeClient(goodChunks()) {
            override suspend fun chunkGet(addressHex: String): ByteArray? {
                if (addressHex == chunk1) gate.await()
                return super.chunkGet(addressHex)
            }
        }
        val cache = MemoryCache()
        val source = source(slow, cache)
        val ready = ready(source)

        // Act
        val job = launch { source.fetchUnits(ready, listOf(0, 1), includeNearby = true) }
        runCurrent()
        job.cancel()
        advanceUntilIdle()

        // Assert
        assertTrue(job.isCancelled)
        assertTrue(cache.entries.containsKey(chunk0))
        assertFalse(cache.entries.containsKey(chunk1))
    }

    @Test
    fun neverRunsMoreThanFourFetchesAtOnce() = runTest {
        // Arrange
        val count = 12
        val units = List(count) { TinyPlaces.UnitSpec(it, false, listOf(TinyPlaces.place(it + 1))) }
        val rowUnits = listOf(List(count) { it })
        val bigBuilt = TinyPlaces.build(units, rowUnits)
        val addresses = List(count) { "%02x".format(it + 1).repeat(32) }
        val chunks = mutableMapOf(
            rootAddress to rootWith(addresses),
            directoryAddress to TinyPlaces.directory(List(count) { 100 }, bigBuilt.ranges),
            categoriesAddress to TinyPlaces.categoryTable(),
        )
        addresses.forEachIndexed { i, a -> chunks[a] = bigBuilt.chunks[i] }
        var running = 0
        var peak = 0
        val counting = object : FakeClient(chunks) {
            override suspend fun chunkGet(addressHex: String): ByteArray? {
                if (addressHex in addresses) {
                    running++
                    peak = maxOf(peak, running)
                    delay(100)
                    running--
                }
                return super.chunkGet(addressHex)
            }
        }
        val source = source(counting)
        val ready = ready(source)

        // Act
        val result = source.fetchUnits(ready, listOf(0), includeNearby = true)

        // Assert
        assertEquals(4, peak)
        assertEquals(0, result.failedChunks)
        assertEquals(count, result.places.getValue(0).size)
    }

    private fun rootWith(addresses: List<String>): ByteArray {
        val base = TinyPlaces.rootChunk(chunks = emptyList(), declaredCount = addresses.size)
        return base + addresses.flatMap { a -> a.chunked(2).map { it.toInt(16).toByte() } }.toByteArray()
    }
}
