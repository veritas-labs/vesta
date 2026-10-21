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
        assertEquals(listOf("Newport"), rows.map { it.name })
        assertEquals(NearbyRelation.InsideOf, rows.single().relation)
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
