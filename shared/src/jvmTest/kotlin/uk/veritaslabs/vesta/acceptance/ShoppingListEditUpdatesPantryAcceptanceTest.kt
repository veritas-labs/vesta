package uk.veritaslabs.vesta.acceptance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.vesta.automerge.PantryDocumentSource
import uk.veritaslabs.vesta.automerge.ShoppingListDocumentSource
import uk.veritaslabs.vesta.automerge.newPantryDocument
import uk.veritaslabs.vesta.automerge.newShoppingListDocument
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails

/**
 * Acceptance tests for editing a shopping-list item (see docs/testing/acceptance-tests.md): the Pantry is the
 * household's shared product record, so changing an item's categories or package size on the list changes the
 * same product in the Pantry. Each test is one scenario over the real Automerge-backed list and Pantry sources.
 */
class ShoppingListEditUpdatesPantryAcceptanceTest {

    private val milk = ShoppingItemDetails(
        primaryCategory = "Dairy", secondaryCategory = "Milk", title = "Whole Milk", quantity = 2, packageSize = "2 pint",
    )

    private fun newSources(): Pair<PantryDocumentSource, ShoppingListDocumentSource> {
        val pantry = PantryDocumentSource(Document.load(newPantryDocument()))
        return pantry to ShoppingListDocumentSource(Document.load(newShoppingListDocument()), pantry)
    }

    @Test
    fun changingAListItemsCategoriesChangesThemInThePantry() = runBlocking {
        // Arrange — Given "Whole Milk" on the list, and so in the Pantry under Dairy / Milk
        val (pantry, shopping) = newSources()
        shopping.save(milk)

        // Act — When I edit the list item to be under Chilled with no secondary category
        shopping.save(milk.copy(key = "whole milk", primaryCategory = "Chilled", secondaryCategory = null))

        // Assert — Then the Pantry's "Whole Milk" is under Chilled with no secondary category, still one entry
        val entry = pantry.contents.first().items.single()
        assertEquals("Chilled", entry.primaryCategory)
        assertNull(entry.secondaryCategory)
    }

    @Test
    fun changingAListItemsPackageSizeChangesItInThePantry() = runBlocking {
        // Arrange — Given "Whole Milk", 2 pint, on the list
        val (pantry, shopping) = newSources()
        shopping.save(milk)

        // Act — When I edit the list item's package size to 4 pint
        shopping.save(milk.copy(key = "whole milk", packageSize = "4 pint"))

        // Assert — Then the Pantry's "Whole Milk" is 4 pint
        assertEquals("4 pint", pantry.contents.first().items.single().packageSize)
    }
}
