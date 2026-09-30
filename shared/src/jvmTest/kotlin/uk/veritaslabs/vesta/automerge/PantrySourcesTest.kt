package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.db.openInMemoryDatabase

class PantrySourcesTest {

    @Test
    fun returnsTheSameSourceForAHomeEveryTime() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val sources = PantrySources(database)

        // Act
        val first = assertNotNull(sources.forHome("home-1"))
        val second = sources.forHome("home-1")

        // Assert
        assertSame(first, second)
    }

    @Test
    fun returnsNullForAHomeThatDoesNotExist() = runBlocking {
        // Arrange
        val sources = PantrySources(openInMemoryDatabase())

        // Act
        val source = sources.forHome("missing")

        // Assert
        assertNull(source)
    }
}
