package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.kits.store.testing.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.screens.PantryListItem

class PantryDocumentSourceTest {

    @Test
    fun emptyDocumentEmitsEmptyContents() = runBlocking {
        // Arrange
        val source = PantryDocumentSource(Document.load(newPantryDocument()))

        // Act
        val contents = source.contents.first()

        // Assert
        assertEquals(emptyList(), contents.items)
        assertEquals(emptyList(), contents.emptyCategories)
    }

    @Test
    fun mutationsArePublishedAsListItems() = runBlocking {
        // Arrange
        val source = PantryDocumentSource(Document.load(newPantryDocument()))

        // Act
        val key = source.mutate {
            upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null)
        }
        source.mutate { adjustStock(it, key, 3) }
        val contents = source.contents.first()

        // Assert
        assertEquals(
            listOf(PantryListItem("whole milk", "Dairy", "Milk", "Whole Milk", "2 pint", stockCount = 3)),
            contents.items,
        )
    }

    @Test
    fun refreshPicksUpChangesMadeDirectlyOnTheDocument() = runBlocking {
        // Arrange
        val document = Document.load(newPantryDocument())
        val source = PantryDocumentSource(document)
        upsertPantryItem(document, "Meat", null, "Beef Mince", "500g", barcode = null)

        // Act
        source.refresh()
        val contents = source.contents.first()

        // Assert
        assertEquals(listOf("beef mince"), contents.items.map { it.key })
    }

    @Test
    fun reopeningAHomesPantryRestoresItsPersistedItems() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val first = openPantryDocumentSource(database, "home-1")
        first.mutate { upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null) }

        // Act
        val reopened = openPantryDocumentSource(database, "home-1")
        val contents = reopened.contents.first()

        // Assert
        assertEquals(listOf("whole milk"), contents.items.map { it.key })
    }

    @Test
    fun eachHomeHasItsOwnPantry() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "One", "family", 0L)
        database.homeQueries.insert("home-2", "Two", "flatshare", 0L)
        val one = openPantryDocumentSource(database, "home-1")
        one.mutate { upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "2 pint", barcode = null) }

        // Act
        val two = openPantryDocumentSource(database, "home-2")

        // Assert
        assertEquals(emptyList(), two.contents.first().items)
        assertEquals(1, one.contents.first().items.size)
    }

    @Test
    fun aNewPantryIsStoredImmediatelyAndRemovedWithItsHome() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)

        // Act
        openPantryDocumentSource(database, "home-1")
        val storedBefore = database.automergeDocumentQueries.selectById(pantryDocumentId("home-1")).awaitAsOneOrNull()
        database.homeQueries.deleteById("home-1")
        val storedAfter = database.automergeDocumentQueries.selectById(pantryDocumentId("home-1")).awaitAsOneOrNull()

        // Assert
        assertEquals("pantry", storedBefore?.feature)
        assertNull(storedAfter)
    }
}
