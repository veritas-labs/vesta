package uk.veritaslabs.vesta.location

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Decodes the real GB chunks built by `scripts/gers-index` (ADR-039). It needs the chunk files, so it runs only when
 * `VESTA_LOCALITY_FIXTURE_DIR` names a directory holding `root.bin`, `names.bin` and `adjacency.bin` (the builder's
 * `<work>/out`); otherwise it does nothing.
 */
class RealLocalityPackTest {

    private val dir = System.getenv("VESTA_LOCALITY_FIXTURE_DIR")?.let(::File)?.takeIf { it.isDirectory }

    @Test
    fun decodesTheGbPackWithTheMeasuredFigures() {
        // Arrange
        val dir = dir ?: return
        val root = LocalityRoot.parse(File(dir, "root.bin").readBytes())

        // Act
        val pack = LocalityPack.parse(File(dir, "names.bin").readBytes(), File(dir, "adjacency.bin").readBytes(), JvmInflater())
        val directory = LocalityDirectory(pack)
        val parentSubtypes = (0 until pack.size).filter { pack.subtype(it) == LocalityDirectory.LOCALITY }
            .groupingBy { pack.parentRow(it).takeIf { parent -> parent >= 0 }?.let { parent -> pack.subtype(parent) } }.eachCount()
        val edges = (0 until pack.size).sumOf { directory.neighbours(pack.uuid(it)).size } / 2

        // Assert
        assertEquals("GB", root.country)
        assertEquals(22_093, pack.size)
        assertEquals(61_355, edges)
        assertEquals(11_741, parentSubtypes["county"])
        assertEquals(10_132, parentSubtypes[LocalityDirectory.LOCALITY])
        assertEquals(58, parentSubtypes["region"])
    }

    @Test
    fun searchesAndSortsNearbyForARealTown() {
        // Arrange
        val dir = dir ?: return
        val pack = LocalityPack.parse(File(dir, "names.bin").readBytes(), File(dir, "adjacency.bin").readBytes(), JvmInflater())
        val directory = LocalityDirectory(pack)

        // Act
        val found = directory.search("newport").firstOrNull { directory.chainText(it.id).contains("Isle of Wight") }
        val nearby = found?.let { NearbyPlaces.rows(it.id, directory) }

        // Assert
        assertNotNull(found)
        assertNotNull(nearby)
        assertTrue(nearby.rows.isNotEmpty())
        assertEquals(nearby.rows.map { it.distanceMetres }, nearby.rows.map { it.distanceMetres }.sorted())
    }
}
