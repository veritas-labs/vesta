package uk.veritaslabs.vesta.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlacesDecoderTest {

    private val built = TinyPlaces.build()
    private val directory get() = PlacesDirectory.parse(TinyPlaces.directory(), TinyPlaces.identity)

    // ---- root ----

    @Test
    fun parsesTheRoot() {
        // Arrange
        val bytes = TinyPlaces.rootChunk()

        // Act
        val root = PlacesRoot.parse(bytes)

        // Assert
        assertEquals("2026-09-23.1", root.release)
        assertEquals("GB", root.country)
        assertEquals("d1".repeat(32), root.directoryAddress)
        assertEquals("c1".repeat(32), root.categoriesAddress)
        assertEquals(listOf("01".repeat(32), "02".repeat(32)), root.chunkAddresses)
    }

    @Test
    fun rejectsBadRoots() {
        // Arrange
        val badMagic = TinyPlaces.rootChunk(magic = "VGI2")
        val tooMany = TinyPlaces.rootChunk(declaredCount = 3)
        val truncated = TinyPlaces.rootChunk().copyOf(30)

        // Act / Assert
        assertFailsWith<IllegalArgumentException> { PlacesRoot.parse(badMagic) }
        assertFailsWith<IllegalArgumentException> { PlacesRoot.parse(tooMany) }
        assertFailsWith<IllegalArgumentException> { PlacesRoot.parse(truncated) }
    }

    // ---- directory ----

    @Test
    fun parsesDirectoryCountsAndRanges() {
        // Arrange
        val directory = directory

        // Act
        val row0 = directory.rangesFor(0)

        // Assert
        assertEquals(4, directory.size)
        assertEquals(2, directory.chunkCount)
        assertEquals(250, directory.chunkSize(1))
        assertEquals(3, directory.insideCount(0))
        assertEquals(1, directory.nearbyCount(0))
        assertEquals(3, directory.insideCount(1))
        assertEquals(0, directory.nearbyCount(1))
        assertEquals(0, directory.insideCount(2))
        assertEquals(2, directory.nearbyCount(2))
        assertEquals(0, directory.insideCount(3) + directory.nearbyCount(3))
        assertEquals(listOf(false, true), row0.map { it.nearby })
        assertEquals(built.ranges[0], row0)
    }

    @Test
    fun keepsInsideRangesBeforeNearbyAndTheirOrder() {
        // Arrange
        val directory = directory

        // Act
        val row1 = directory.rangesFor(1)

        // Assert
        assertEquals(listOf(2, 1), row1.map { it.count })
        assertTrue(row1[0].offset < row1[1].offset)
    }

    @Test
    fun bytesForCountsADistinctChunkOnce() {
        // Arrange
        val directory = directory

        // Act
        val both = directory.bytesFor(listOf(0, 1, 2))
        val sameChunkTwice = directory.bytesFor(listOf(0, 2, 0))
        val insideOnly = directory.bytesFor(listOf(2), includeNearby = false)
        val nothing = directory.bytesFor(listOf(3))

        // Assert
        assertEquals(350L, both)
        assertEquals(350L, sameChunkTwice)
        assertEquals(0L, insideOnly)
        assertEquals(0L, nothing)
        assertEquals(listOf(0, 1), directory.chunksFor(listOf(1, 0)))
        assertEquals(listOf(0), directory.chunksFor(listOf(0), includeNearby = true))
        assertEquals(listOf(0, 1), directory.chunksFor(listOf(0, 2)))
        assertEquals(listOf(0, 1), directory.chunksFor(listOf(0, 1, 2)))
        assertEquals(listOf(0), directory.chunksFor(listOf(0, 2), includeNearby = false))
    }

    @Test
    fun rejectsABadRowNumber() {
        // Arrange
        val directory = directory

        // Act / Assert
        assertFailsWith<IllegalArgumentException> { directory.rangesFor(4) }
        assertFailsWith<IllegalArgumentException> { directory.insideCount(-1) }
    }

    @Test
    fun rejectsMalformedDirectories() {
        // Arrange
        val good = TinyPlaces.directory()
        val badMagic = TinyPlaces.directory(magic = "VGN2")
        val chunkOutOfRange = TinyPlaces.directory(chunkSizes = listOf(100))
        val moreRowsThanData = TinyPlaces.directory(rowCount = 9)
        val trailing = good + byteArrayOf(0)
        val truncated = good.copyOf(good.size - 3)
        val nearbyFirst = TinyPlaces.directory(ranges = listOf(built.ranges[0].reversed()))

        // Act / Assert
        listOf(badMagic, chunkOutOfRange, moreRowsThanData, trailing, truncated, nearbyFirst).forEach {
            assertFailsWith<IllegalArgumentException> { PlacesDirectory.parse(it, TinyPlaces.identity) }
        }
    }

    // ---- units ----

    @Test
    fun decodesAnInsideUnit() {
        // Arrange
        val range = built.ranges[0][0]

        // Act
        val places = PlaceUnit.decodeRange(PlaceUnit.decompress(built.chunks[0], TinyPlaces.identity), range)

        // Assert
        assertEquals(3, places.size)
        assertEquals(TinyPlaces.uuid(1), places[0].id)
        assertEquals("Café Nero", places[0].name)
        assertEquals(50.701, places[0].latitude, 1e-9)
        assertEquals(-1.301, places[0].longitude, 1e-9)
        assertEquals(1.0, places[0].confidence, 1e-9)
        assertEquals(1, places[1].categoryIndex)
        assertEquals(0.5, places[1].confidence, 1.0 / 255)
        assertEquals(-33.5, places[2].latitude, 1e-9) // a delta crossing zero and the hemisphere
        assertEquals(151.2, places[2].longitude, 1e-9)
        assertTrue(places.none { it.nearby })
    }

    @Test
    fun decodesANearbyUnitAndFlagsIt() {
        // Arrange
        val range = built.ranges[0][1]

        // Act
        val places = PlaceUnit.decodeRange(built.chunks[0], range)

        // Assert
        assertEquals(listOf(TinyPlaces.uuid(4)), places.map { it.id })
        assertTrue(places.single().nearby)
        assertEquals(2, places.single().categoryIndex)
    }

    @Test
    fun decodesEveryUnitOfAChunkAtItsOffset() {
        // Arrange
        val ranges = built.ranges.flatten().filter { it.chunk == 1 }

        // Act
        val ids = ranges.flatMap { PlaceUnit.decodeRange(built.chunks[1], it) }.map { it.id }

        // Assert
        assertEquals(listOf(5, 6, 7, 8).map(TinyPlaces::uuid), ids)
    }

    @Test
    fun rejectsMalformedUnitsAndChunks() {
        // Arrange
        val chunk = built.chunks[0]
        val range = built.ranges[0][0]

        // Act / Assert
        assertFailsWith<IllegalArgumentException> { PlaceUnit.decompress(byteArrayOf(1, 2, 3, 4, 5), TinyPlaces.identity) }
        assertFailsWith<IllegalArgumentException> { PlaceUnit.decompress(byteArrayOf(), TinyPlaces.identity) }
        assertFailsWith<IllegalArgumentException> { PlaceUnit.decodeRange(chunk, range.copy(offset = chunk.size - 2)) } // outside
        assertFailsWith<IllegalArgumentException> { PlaceUnit.decodeRange(chunk, range.copy(length = range.length + 1)) } // trailing
        assertFailsWith<IllegalArgumentException> { PlaceUnit.decodeRange(chunk, range.copy(length = range.length - 1)) } // truncated
        assertFailsWith<IllegalArgumentException> { PlaceUnit.decodeRange(chunk, range.copy(count = 2)) } // counts disagree
        assertFailsWith<IllegalArgumentException> { PlaceUnit.decodeRange(chunk, range.copy(offset = 0)) } // inside the magic
        assertFailsWith<IllegalArgumentException> { PlaceUnit.decodeRange(chunk, range.copy(length = Int.MAX_VALUE)) }
    }

    // ---- categories ----

    @Test
    fun parsesTheCategoryTableAndDerivesDisplayNames() {
        // Arrange
        val bytes = TinyPlaces.categoryTable()

        // Act
        val table = CategoryTable.parse(bytes, TinyPlaces.identity)

        // Assert
        assertEquals(2, table.groupCount)
        assertEquals(3, table.categoryCount)
        assertEquals(listOf("food_and_drink", "services"), table.groupKeys)
        assertEquals(listOf("cafe", "hair_salon", "pub"), table.categoryKeys)
        assertEquals("Hair Salon", table.categoryName(1))
        assertEquals("Food and drink", table.groupName(0))
        assertEquals("Services", table.groupName(1))
        assertEquals(1, table.groupOf(1))
        assertEquals(0, table.groupOf(2))
    }

    @Test
    fun rejectsMalformedCategoryTables() {
        // Arrange
        val good = TinyPlaces.categoryTable()
        val badMagic = TinyPlaces.categoryTable(magic = "VGD1")
        val badGroup = TinyPlaces.categoryTable(cats = listOf("cafe" to 5))
        val truncated = good.copyOf(good.size - 1)
        val trailing = good + byteArrayOf(0)
        val table = CategoryTable.parse(good, TinyPlaces.identity)

        // Act / Assert
        listOf(badMagic, badGroup, truncated, trailing).forEach {
            assertFailsWith<IllegalArgumentException> { CategoryTable.parse(it, TinyPlaces.identity) }
        }
        assertFailsWith<IllegalArgumentException> { table.categoryName(3) }
    }
}
