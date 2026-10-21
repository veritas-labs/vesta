package uk.veritaslabs.vesta.location

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.autonomi.StandaloneAutonomiClient

/**
 * Reads the real places pack from a running antd devnet (ADR-039). Runs only when `VESTA_ANTD_PORT` and `VESTA_PLACES_ROOT`
 * are both set; otherwise it does nothing. Prints timings.
 */
class RealDevnetPlacesPackTest {

    private val port = System.getenv("VESTA_ANTD_PORT")?.trim()?.toIntOrNull()
    private val rootAddress = System.getenv("VESTA_PLACES_ROOT")?.trim()?.takeIf { it.isNotEmpty() }

    @Test
    fun fetchesRealLocalitiesFromTheDevnet() = runBlocking {
        // Arrange
        val port = port ?: return@runBlocking
        val rootAddress = rootAddress ?: return@runBlocking
        val cacheDir = kotlin.io.path.createTempDirectory("places-cache").toFile()
        try {
            val source = AutonomiPlacesPackSource(
                StandaloneAutonomiClient(port), PlacesPackConfig(null, rootAddress), DiskChunkCache(cacheDir), JvmInflater(),
            )

            // Act
            val (state, loadTime) = measureTimedValue { source.load() }
            val ready = assertIs<PlacesPackState.Ready>(state)
            val directory = ready.directory
            val withPlaces = (0 until directory.size).filter { directory.insideCount(it) + directory.nearbyCount(it) > 0 }
            val five = withPlaces.take(5)
            val (fiveResult, fiveTime) = measureTimedValue { source.fetchUnits(ready, five, includeNearby = true) }
            val largest = withPlaces.maxBy { directory.bytesFor(listOf(it), true) }
            val largestChunks = directory.chunksFor(listOf(largest), true).size
            val (largestResult, largestTime) = measureTimedValue { source.fetchUnits(ready, listOf(largest), includeNearby = true) }
            val (_, cachedTime) = measureTimedValue { source.fetchUnits(ready, listOf(largest), includeNearby = true) }

            // Assert
            println("PLACES load: ${loadTime}, ${directory.size} rows, ${directory.chunkCount} chunks")
            println("PLACES five rows $five: ${fiveTime}, ${directory.chunksFor(five, true).size} chunks, ${directory.bytesFor(five, true)} bytes, ${fiveResult.places.values.sumOf { it.size }} places, failed ${fiveResult.failedChunks}")
            println("PLACES largest row $largest: ${largestTime}, $largestChunks chunks, ${directory.bytesFor(listOf(largest), true)} bytes, ${largestResult.places.getValue(largest).size} places, failed ${largestResult.failedChunks}; cached refetch $cachedTime")
            assertEquals(0, fiveResult.failedChunks)
            assertEquals(0, largestResult.failedChunks)
            assertEquals(
                directory.insideCount(largest) + directory.nearbyCount(largest),
                largestResult.places.getValue(largest).size,
            )
            assertTrue(File(cacheDir, rootAddress).isFile)
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
