package uk.veritaslabs.vesta.location

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.location.TinyPack.id

class LocalityDirectoryTest {

    private val directory = TinyPack.directory()

    @Test
    fun foldsDiacriticsSoKolnFindsKoln() {
        // Arrange
        val query = "koln"

        // Act
        val hits = directory.search(query)

        // Assert
        assertEquals(listOf("Köln"), hits.map { it.name })
        assertEquals(id(6), hits.single().id)
    }

    @Test
    fun foldsCaseAndAccentsInTheQueryToo() {
        // Arrange
        val query = "KÖLN"

        // Act
        val hits = directory.search(query)

        // Assert
        assertEquals(listOf("Köln"), hits.map { it.name })
    }

    @Test
    fun ranksWholeNamePrefixThenWordPrefixThenContains() {
        // Arrange
        val query = "port"

        // Act
        val names = directory.search(query).map { it.name }

        // Assert
        assertEquals(listOf("Port Erin", "Old Port", "Newport", "Newport", "Westport"), names)
    }

    @Test
    fun blankQueryReturnsNothing() {
        // Arrange
        val queries = listOf("", "   ")

        // Act
        val results = queries.map { directory.search(it) }

        // Assert
        results.forEach { assertTrue(it.isEmpty()) }
    }

    @Test
    fun searchOnlyReturnsLocalities() {
        // Arrange
        val query = "england"

        // Act
        val hits = directory.search(query)

        // Assert
        assertTrue(hits.isEmpty())
    }

    @Test
    fun searchHonoursTheLimit() {
        // Arrange
        val query = "port"

        // Act
        val hits = directory.search(query, limit = 2)

        // Assert
        assertEquals(listOf("Port Erin", "Old Port"), hits.map { it.name })
    }

    @Test
    fun tiesOnRankAreBrokenByShorterNameThenByName() {
        // Arrange
        val query = "town"

        // Act
        val names = directory.search(query).map { it.name }

        // Assert
        assertEquals(listOf("Beta Town", "Alpha Town"), names)
    }

    @Test
    fun twoSameNamedLocalitiesAreToldApartByTheirChain() {
        // Arrange
        val newports = directory.search("newport")

        // Act
        val texts = newports.map { directory.chainText(it.id) }.sorted()

        // Assert
        assertEquals(listOf("Newport, England", "Newport, Isle of Wight, England"), texts)
    }

    @Test
    fun chainRunsFromTheLocalityUpToTheCountry() {
        // Arrange
        val newport = id(3)

        // Act
        val chain = directory.chain(newport)

        // Assert
        assertEquals(listOf("Newport", "Isle of Wight", "England", "United Kingdom"), chain.map { it.name })
        assertEquals(listOf("locality", "county", "region", "country"), chain.map { it.subtype })
        assertEquals("Newport, Isle of Wight, England", directory.chainText(newport))
    }

    @Test
    fun neighboursAreSymmetric() {
        // Arrange
        val newport = id(3)
        val cowes = id(5)

        // Act
        val fromNewport = directory.neighbours(newport).map { it.id }
        val fromCowes = directory.neighbours(cowes).map { it.id }

        // Assert
        assertEquals(setOf(id(5), id(7), id(11), id(12)), fromNewport.toSet())
        assertTrue(newport in fromCowes)
    }

    @Test
    fun anIslandHasNoNeighbours() {
        // Arrange
        val koln = id(6)

        // Act
        val neighbours = directory.neighbours(koln)

        // Assert
        assertTrue(neighbours.isEmpty())
    }

    @Test
    fun nestedLocalitiesAreInsideAndContains() {
        // Arrange
        val newport = id(3)
        val carisbrooke = id(7)

        // Act
        val inside = directory.insideOf(carisbrooke)
        val contained = directory.contains(newport).map { it.name }

        // Assert
        assertEquals("Newport", inside?.name)
        assertEquals(listOf("Carisbrooke", "Shide"), contained)
    }

    @Test
    fun insideOfIsNullWhenTheParentIsNotALocality() {
        // Arrange
        val newport = id(3)

        // Act
        val inside = directory.insideOf(newport)

        // Assert
        assertNull(inside)
        assertTrue(directory.contains(id(5)).isEmpty())
    }

    @Test
    fun centroidDecodesDeltasIncludingNegatives() {
        // Arrange
        val newport = id(3)

        // Act
        val centroid = directory.centroid(newport)

        // Assert
        assertEquals(Microdegrees(50_700_000, -1_290_000), centroid)
    }

    @Test
    fun distanceIsHaversineBetweenCentroids() {
        // Arrange
        val newport = id(3)
        val cowes = id(5)

        // Act
        val metres = directory.distanceMetres(newport, cowes)

        // Assert
        assertTrue(metres != null && metres in 6_500.0..6_900.0, "was $metres")
        assertEquals(0.0, directory.distanceMetres(newport, newport))
    }

    @Test
    fun lookupAcceptsTheCanonicalFormInAnyCase() {
        // Arrange
        val upper = id(6).uppercase()

        // Act
        val row = directory.row(upper)

        // Assert
        assertEquals(LocalityRow(id(6), "Köln", "locality"), row)
        assertNull(directory.row("ffffffff-ffff-ffff-ffff-ffffffffffff"))
    }

    @Test
    fun rejectsAnUnknownNamesMagic() {
        // Arrange
        val bad = TinyPack.namesChunk(magic = "VGN1")

        // Act
        val failure = assertFailsWith<IllegalArgumentException> {
            LocalityPack.parse(bad, TinyPack.adjacencyChunk(), TinyPack.identity)
        }

        // Assert
        assertFalse(failure.message.isNullOrBlank())
    }

    @Test
    fun parsesTheRootAndSkipsTheShards() {
        // Arrange
        val bytes = TinyPack.rootChunk()

        // Act
        val root = LocalityRoot.parse(bytes)

        // Assert
        assertEquals(
            LocalityRoot("2026-09-23.1", "GB", "ab".repeat(32), "01".repeat(32)),
            root,
        )
    }

    @Test
    fun rejectsABadRootMagicOrATruncatedShardTable() {
        // Arrange
        val badMagic = TinyPack.rootChunk(magic = "XXXX")
        val truncated = TinyPack.rootChunk().let { it.copyOf(it.size - 5) }

        // Act
        val first = runCatching { LocalityRoot.parse(badMagic) }
        val second = runCatching { LocalityRoot.parse(truncated) }

        // Assert
        assertTrue(first.exceptionOrNull() is IllegalArgumentException)
        assertTrue(second.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun uuidBytesRoundTripToCanonicalText() {
        // Arrange
        val pack = TinyPack.pack()

        // Act
        val ids = (0 until pack.size).map { pack.uuid(it) }

        // Assert
        assertContentEquals((0 until pack.size).map { id(it) }, ids)
    }
}
