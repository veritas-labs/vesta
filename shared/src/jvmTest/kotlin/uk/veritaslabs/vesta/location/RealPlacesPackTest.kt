package uk.veritaslabs.vesta.location

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Decodes the real GB places pack built by `scripts/gers-index` (ADR-039). Runs only when `VESTA_PLACES_FIXTURE_DIR` names a
 * directory holding `root.bin` and the directory, category and data chunks named by their hex address; the locality row-count
 * check also needs `VESTA_LOCALITY_FIXTURE_DIR` (as in [RealLocalityPackTest]). Otherwise it does nothing.
 */
class RealPlacesPackTest {

    private val dir = System.getenv("VESTA_PLACES_FIXTURE_DIR")?.let(::File)?.takeIf { it.isDirectory }
    private val inflater = JvmInflater()

    private fun load(dir: File): Triple<PlacesRoot, PlacesDirectory, CategoryTable> {
        val root = PlacesRoot.parse(File(dir, "root.bin").readBytes())
        val directory = PlacesDirectory.parse(File(dir, root.directoryAddress).readBytes(), inflater)
        val categories = CategoryTable.parse(File(dir, root.categoriesAddress).readBytes(), inflater)
        return Triple(root, directory, categories)
    }

    @Test
    fun matchesThePublishedFigures() {
        // Arrange
        val dir = dir ?: return

        // Act
        val (root, directory, categories) = load(dir)
        val inside = (0 until directory.size).sumOf { directory.insideCount(it).toLong() }
        val nearby = (0 until directory.size).sumOf { directory.nearbyCount(it).toLong() }
        val withPlaces = (0 until directory.size).count { directory.insideCount(it) + directory.nearbyCount(it) > 0 }

        // Assert
        assertEquals("GB", root.country)
        assertEquals(381, root.chunkAddresses.size)
        assertEquals(root.chunkAddresses.size, directory.chunkCount)
        assertEquals(22_093, directory.size)
        assertEquals(1_649_461L, inside)
        assertEquals(1_124_119L, nearby)
        assertEquals(17_559, withPlaces)
        assertEquals(1_778, categories.categoryCount)
        assertEquals(14, categories.groupCount)
        (0 until directory.chunkCount).forEach {
            assertEquals(File(dir, root.chunkAddresses[it]).length(), directory.chunkSize(it).toLong())
        }
    }

    @Test
    fun decodesEveryUnitToPlausibleUkPlaces() {
        // Arrange
        val dir = dir ?: return
        val (root, directory, categories) = load(dir)
        // Act
        val byChunk = (0 until directory.size).flatMap { directory.rangesFor(it) }.groupBy { it.chunk }
        var total = 0
        var outOfBounds = 0
        var badRecords = 0
        byChunk.forEach { (chunk, ranges) ->
            val bytes = PlaceUnit.decompress(File(dir, root.chunkAddresses[chunk]).readBytes(), inflater)
            ranges.forEach { range ->
                PlaceUnit.decodeRange(bytes, range).forEach {
                    total++
                    if (it.latitude !in 49.0..61.5 || it.longitude !in -9.0..2.5) outOfBounds++
                    if (it.categoryIndex !in 0 until categories.categoryCount || it.name.isEmpty()) badRecords++
                }
            }
        }

        // Assert
        assertEquals(1_649_461 + 1_124_119, total)
        assertEquals(0, outOfBounds, "places outside the UK bounding box")
        assertEquals(0, badRecords)
    }

    @Test
    fun rowCountMatchesTheLocalityNamesTable() {
        // Arrange
        val dir = dir ?: return
        val localityDir = System.getenv("VESTA_LOCALITY_FIXTURE_DIR")?.let(::File)?.takeIf { it.isDirectory } ?: return
        val (_, directory, _) = load(dir)

        // Act
        val pack = LocalityPack.parse(File(localityDir, "names.bin").readBytes(), File(localityDir, "adjacency.bin").readBytes(), inflater)

        // Assert
        assertEquals(pack.size, directory.size)
    }
}
