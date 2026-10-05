package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails

class ShoppingListDocumentSourceTest {

    private val milk = ShoppingItemDetails(
        primaryCategory = "Dairy",
        secondaryCategory = "Milk",
        title = "Whole Milk",
        quantity = 2,
        packageSize = "2 pint",
    )

    private fun newSources(): Pair<PantryDocumentSource, ShoppingListDocumentSource> {
        val pantry = PantryDocumentSource(Document.load(newPantryDocument()))
        return pantry to ShoppingListDocumentSource(Document.load(newShoppingListDocument()), pantry)
    }

    @Test
    fun savedItemIsPublishedOnTheList() = runBlocking {
        // Arrange
        val (_, shopping) = newSources()

        // Act
        shopping.save(milk)
        val items = shopping.items.first()

        // Assert
        assertEquals(listOf("whole milk" to 2), items.map { it.key to it.quantity })
    }

    @Test
    fun anItemTheListGainsIsAddedToThePantryWithNoStock() = runBlocking {
        // Arrange
        val (pantry, shopping) = newSources()

        // Act
        shopping.save(milk)
        val pantryItems = pantry.contents.first().items

        // Assert
        assertEquals(listOf("whole milk" to 0), pantryItems.map { it.key to it.stockCount })
        assertEquals("Dairy", pantryItems.single().primaryCategory)
    }

    @Test
    fun anItemAlreadyInThePantryKeepsItsPantryDetailsAndStock() = runBlocking {
        // Arrange
        val (pantry, shopping) = newSources()
        pantry.mutate {
            val key = upsertPantryItem(it, "Dairy", "Milk", "Whole Milk", "1 litre", barcode = null)
            adjustStock(it, key, 3)
        }

        // Act
        shopping.save(milk)
        val pantryItem = pantry.contents.first().items.single()

        // Assert
        assertEquals(3, pantryItem.stockCount)
        assertEquals("1 litre", pantryItem.packageSize)
    }

    @Test
    fun addingTheSameProductTwiceAddsTheQuantities() = runBlocking {
        // Arrange
        val (_, shopping) = newSources()
        shopping.save(milk)

        // Act
        shopping.save(milk.copy(quantity = 1))

        // Assert
        assertEquals(listOf(3), shopping.items.first().map { it.quantity })
    }

    @Test
    fun editingAnItemChangesItInPlaceWithoutTouchingThePantryUseCount() = runBlocking {
        // Arrange
        val (pantry, shopping) = newSources()
        shopping.save(milk)

        // Act
        shopping.save(milk.copy(key = "whole milk", quantity = 6, packageSize = "4 pint"))

        // Assert
        val item = shopping.items.first().single()
        assertEquals(6, item.quantity)
        assertEquals("4 pint", item.packageSize)
        assertEquals(listOf("whole milk"), pantry.contents.first().items.map { it.key })
    }

    @Test
    fun removedItemLeavesTheListButStaysInThePantry() = runBlocking {
        // Arrange
        val (pantry, shopping) = newSources()
        shopping.save(milk)

        // Act
        shopping.remove("whole milk")

        // Assert
        assertEquals(emptyList(), shopping.items.first())
        assertEquals(1, pantry.contents.first().items.size)
    }

    @Test
    fun reopeningAHomesListRestoresItsItems() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val pantry = openPantryDocumentSource(database, "home-1")
        openShoppingListDocumentSource(database, "home-1", pantry).save(milk)

        // Act
        val reopened = openShoppingListDocumentSource(database, "home-1", pantry)

        // Assert
        assertEquals(listOf("whole milk"), reopened.items.first().map { it.key })
    }

    @Test
    fun aNewListIsStoredImmediatelyAndRemovedWithItsHome() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert("home-1", "Home", "family", 0L)
        val pantry = openPantryDocumentSource(database, "home-1")

        // Act
        openShoppingListDocumentSource(database, "home-1", pantry)
        val before = database.automergeDocumentQueries.selectById(shoppingListDocumentId("home-1")).awaitAsOneOrNull()
        database.homeQueries.deleteById("home-1")
        val after = database.automergeDocumentQueries.selectById(shoppingListDocumentId("home-1")).awaitAsOneOrNull()

        // Assert
        assertEquals("shopping-list", before?.feature)
        assertNull(after)
    }
}
