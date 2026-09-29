package uk.veritaslabs.vesta.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.screens.HomeListItem

class HomeRepositoryTest {

    @Test
    fun emitsEmptyWhenThereAreNoHomes() = runTest {
        // Arrange
        val repository = HomeRepository(openInMemoryDatabase())

        // Act
        val homes = repository.homes.first()

        // Assert
        assertEquals(emptyList(), homes)
    }

    @Test
    fun emitsStoredHomes() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        db.homeQueries.insert("the-johnsons", "The Johnsons", "family", 1L)
        val repository = HomeRepository(db)

        // Act
        val homes = repository.homes.first()

        // Assert
        assertEquals(listOf(HomeListItem(id = "the-johnsons", name = "The Johnsons")), homes)
    }

    @Test
    fun reEmitsWhenAHomeIsInserted() = runTest {
        // Arrange
        val db = openInMemoryDatabase()
        val repository = HomeRepository(db)
        val before = repository.homes.first()

        // Act
        db.homeQueries.insert("flat-4b", "Flat 4B", "flatshare", 1L)
        val after = repository.homes.first()

        // Assert
        assertEquals(0, before.size)
        assertEquals(listOf("Flat 4B"), after.map { it.name })
    }
}
