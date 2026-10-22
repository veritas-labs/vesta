package uk.veritaslabs.vesta.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uk.veritaslabs.vesta.location.TinyPack.id

class NearbyPlacesTest {

    private val directory = TinyPack.directory()

    @Test
    fun listsNearestFirstAndLabelsEachRelation() {
        // Arrange
        val newport = id(3)

        // Act
        val page = NearbyPlaces.rows(newport, directory, shown = 10)

        // Assert
        assertEquals(
            listOf("Shide", "Carisbrooke", "Cowes", "Alpha Town", "Beta Town"),
            page.rows.map { it.name },
        )
        assertEquals(
            listOf(
                NearbyRelation.Contains,
                NearbyRelation.NeighbourAndNested,
                NearbyRelation.Neighbour,
                NearbyRelation.Neighbour,
                NearbyRelation.Neighbour,
            ),
            page.rows.map { it.relation },
        )
        assertTrue(page.rows.zipWithNext().all { (a, b) -> a.distanceMetres <= b.distanceMetres })
        assertFalse(page.hasMore)
    }

    @Test
    fun neverIncludesTheHomeItself() {
        // Arrange
        val newport = id(3)

        // Act
        val ids = NearbyPlaces.rows(newport, directory).rows.map { it.id }

        // Assert
        assertFalse(newport in ids)
    }

    @Test
    fun theLocalityHomeIsNestedInIsListedAsInsideOf() {
        // Arrange
        val shide = id(13)

        // Act
        val rows = NearbyPlaces.rows(shide, directory).rows

        // Assert
        assertEquals("Newport", rows.first().name)
        assertEquals(NearbyRelation.InsideOf, rows.first().relation)
        assertTrue(rows.drop(1).all { it.relation == NearbyRelation.Further })
    }

    @Test
    fun aHomeWithFewCandidatesIsWidenedToTheNeighboursOfItsNeighbours() {
        // Arrange: Cowes touches only Newport and Alpha Town
        val cowes = id(5)

        // Act
        val rows = NearbyPlaces.rows(cowes, directory).rows

        // Assert: Carisbrooke and Beta Town touch those, not Cowes, so they are offered as further away
        val byName = rows.associate { it.name to it.relation }
        assertEquals(NearbyRelation.Neighbour, byName["Newport"])
        assertEquals(NearbyRelation.Neighbour, byName["Alpha Town"])
        assertEquals(NearbyRelation.Further, byName["Carisbrooke"])
        assertEquals(NearbyRelation.Further, byName["Beta Town"])
        assertTrue(rows.zipWithNext().all { (a, b) -> a.distanceMetres <= b.distanceMetres })
    }

    @Test
    fun aHomeWithEnoughCandidatesIsNotWidened() {
        // Arrange: a hub touching ten places, each of which touches one more place
        val specs = listOf(TinyPack.Spec("Hub", "locality", null, 50.0, -1.0)) +
            (1..10).map { TinyPack.Spec("Spoke $it", "locality", null, 50.0 + it * 0.01, -1.0) } +
            TinyPack.Spec("Outer", "locality", null, 51.0, -1.0)
        val edges = (1..10).map { 0 to it } + (1 to 11)
        val big = LocalityDirectory(TinyPack.pack(specs, edges))

        // Act
        val rows = NearbyPlaces.rows(id(0), big, shown = 50).rows

        // Assert
        assertEquals(10, rows.size)
        assertTrue(rows.none { it.relation == NearbyRelation.Further })
    }

    @Test
    fun equalDistancesAreOrderedByName() {
        // Arrange
        val newport = id(3)

        // Act
        val rows = NearbyPlaces.rows(newport, directory).rows

        // Assert
        val tied = rows.filter { it.name.endsWith("Town") }
        assertEquals(tied[0].distanceMetres, tied[1].distanceMetres)
        assertEquals(listOf("Alpha Town", "Beta Town"), tied.map { it.name })
    }

    @Test
    fun showMoreReturnsTheRestAndOnlyThenClearsHasMore() {
        // Arrange
        val newport = id(3)

        // Act
        val first = NearbyPlaces.rows(newport, directory, shown = 2)
        val more = NearbyPlaces.rows(newport, directory, shown = 5)

        // Assert
        assertEquals(listOf("Shide", "Carisbrooke"), first.rows.map { it.name })
        assertTrue(first.hasMore)
        assertEquals(5, more.rows.size)
        assertFalse(more.hasMore)
    }

    @Test
    fun anIslandWithNothingNestedHasNoRows() {
        // Arrange
        val koln = id(6)

        // Act
        val page = NearbyPlaces.rows(koln, directory)

        // Assert
        assertTrue(page.rows.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun anUnknownHomeHasNoRows() {
        // Arrange
        val unknown = "ffffffff-ffff-ffff-ffff-ffffffffffff"

        // Act
        val page = NearbyPlaces.rows(unknown, directory)

        // Assert
        assertTrue(page.rows.isEmpty())
    }
}
