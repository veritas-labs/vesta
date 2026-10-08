package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.automerge.Document

class ShoppingListDocumentTest {

    private fun newList() = Document.load(newShoppingListDocument())

    private fun Document.addMilk(quantity: Int = 1) =
        addShoppingItem(this, "Dairy", "Milk", "Whole Milk", quantity, "2 pint", barcode = null)

    @Test
    fun newDocumentReadsBackAsEmpty() {
        // Arrange
        val doc = newList()

        // Act
        val items = doc.readShoppingList()

        // Assert
        assertEquals(emptyList(), items)
    }

    @Test
    fun addedItemReadsBackKeyedByNormalizedTitle() {
        // Arrange
        val doc = newList()

        // Act
        val key = doc.addMilk(quantity = 2)

        // Assert
        assertEquals("whole milk", key)
        assertEquals(
            ShoppingItem("whole milk", "Dairy", "Milk", "Whole Milk", 2, "2 pint", null),
            doc.readShoppingItem(key),
        )
    }

    @Test
    fun addingTheSameProductAgainAddsTheQuantities() {
        // Arrange
        val doc = newList()
        doc.addMilk(quantity = 2)

        // Act
        doc.addMilk(quantity = 3)

        // Assert
        assertEquals(listOf(5), doc.readShoppingList().map { it.quantity })
    }

    @Test
    fun barcodeIsTheKeyWhenPresent() {
        // Arrange
        val doc = newList()

        // Act
        val key = addShoppingItem(doc, "Condiments", null, "Ketchup", 1, "", barcode = "5000157024671")

        // Assert
        assertEquals("5000157024671", key)
        assertNull(doc.readShoppingItem(key)?.secondaryCategory)
    }

    @Test
    fun listIsSortedByCategoriesThenTitle() {
        // Arrange
        val doc = newList()
        addShoppingItem(doc, "Pantry", "Rice", "Basmati", 1, "", null)
        addShoppingItem(doc, "Dairy", "Milk", "Oat Milk", 1, "", null)
        addShoppingItem(doc, "Dairy", "Cheese", "Cheddar", 1, "", null)
        addShoppingItem(doc, "dairy", "Milk", "Almond Milk", 1, "", null)

        // Act
        val titles = doc.readShoppingList().map { it.title }

        // Assert
        assertEquals(listOf("Cheddar", "Almond Milk", "Oat Milk", "Basmati"), titles)
    }

    @Test
    fun updateRewritesTheItemInPlace() {
        // Arrange
        val doc = newList()
        val key = doc.addMilk()

        // Act
        val newKey = updateShoppingItem(doc, key, "Dairy", null, "Whole Milk", 4, "4 pint", barcode = null)

        // Assert
        assertEquals(key, newKey)
        assertEquals(
            listOf(ShoppingItem(key, "Dairy", null, "Whole Milk", 4, "4 pint", null)),
            doc.readShoppingList(),
        )
    }

    @Test
    fun renamingAnItemMovesItToTheNewKey() {
        // Arrange
        val doc = newList()
        val key = doc.addMilk(quantity = 2)

        // Act
        val newKey = updateShoppingItem(doc, key, "Dairy", "Milk", "Skimmed Milk", 2, "2 pint", null)

        // Assert
        assertEquals("skimmed milk", newKey)
        assertNull(doc.readShoppingItem(key))
        assertEquals(listOf("Skimmed Milk"), doc.readShoppingList().map { it.title })
    }

    @Test
    fun renamingOntoAnExistingItemMergesTheQuantities() {
        // Arrange
        val doc = newList()
        val key = doc.addMilk(quantity = 2)
        addShoppingItem(doc, "Dairy", "Milk", "Skimmed Milk", 1, "2 pint", null)

        // Act
        updateShoppingItem(doc, key, "Dairy", "Milk", "Skimmed Milk", 2, "2 pint", null)

        // Assert
        assertEquals(listOf("Skimmed Milk" to 3), doc.readShoppingList().map { it.title to it.quantity })
    }

    @Test
    fun updatingAMissingItemDoesNothing() {
        // Arrange
        val doc = newList()

        // Act
        val result = updateShoppingItem(doc, "nope", "Dairy", null, "Milk", 1, "", null)

        // Assert
        assertNull(result)
        assertEquals(emptyList(), doc.readShoppingList())
    }

    @Test
    fun removeDeletesTheItem() {
        // Arrange
        val doc = newList()
        val key = doc.addMilk()

        // Act
        removeShoppingItem(doc, key)

        // Assert
        assertEquals(emptyList(), doc.readShoppingList())
    }

    @Test
    fun ensurePantryItemCreatesAnAbsentProductAndLeavesAPresentOneAlone() {
        // Arrange
        val pantry = Document.load(newPantryDocument())

        // Act
        val key = ensurePantryItem(pantry, "Dairy", "Milk", "Whole Milk", "2 pint", null, now = { "t1" })
        val created = pantry.readPantryItem(key)
        ensurePantryItem(pantry, "Other", null, "Whole Milk", "1 litre", null, now = { "t2" })
        val reused = pantry.readPantryItem(key)

        // Assert
        assertEquals(1, created?.useCount)
        assertEquals(0, created?.stockCount)
        assertEquals(created?.copy(useCount = 2, lastUsedAt = "t2"), reused)
    }

    @Test
    fun ensurePantryItemWithoutCountingUseKeepsTheUseCount() {
        // Arrange
        val pantry = Document.load(newPantryDocument())
        val key = ensurePantryItem(pantry, "Dairy", "Milk", "Whole Milk", "2 pint", null)

        // Act
        ensurePantryItem(pantry, "Dairy", "Milk", "Whole Milk", "2 pint", null, countUse = false)

        // Assert
        assertEquals(1, pantry.readPantryItem(key)?.useCount)
    }

    @Test
    fun ensurePantryItemRefreshingFieldsUpdatesAPresentProductButNotItsCounters() {
        // Arrange
        val pantry = Document.load(newPantryDocument())
        val key = ensurePantryItem(pantry, "Dairy", "Milk", "Whole Milk", "2 pint", null, now = { "t1" })

        // Act
        ensurePantryItem(
            pantry, "Chilled", null, "Whole Milk", "4 pint", null,
            countUse = false, refreshFields = true, now = { "t2" },
        )

        // Assert
        assertEquals(PantryItem(key, "Chilled", null, "Whole Milk", "4 pint", null, 1, "t1", 0), pantry.readPantryItem(key))
    }

    @Test
    fun tagsAreStoredTrimmedWithoutBlanksOrRepeatsIgnoringCase() {
        // Arrange
        val doc = newList()

        // Act
        val key = addShoppingItem(
            doc, "Dairy", "Milk", "Whole Milk", 1, "2 pint", null,
            tags = listOf(" Farm shop ", "", "farm SHOP", "Butcher"),
        )

        // Assert
        assertEquals(listOf("Farm shop", "Butcher"), doc.readShoppingItem(key)?.tags)
    }

    @Test
    fun anItemWithoutTagsReadsBackWithNone() {
        // Arrange
        val doc = newList()

        // Act
        val key = doc.addMilk()

        // Assert
        assertEquals(emptyList(), doc.readShoppingItem(key)?.tags)
    }

    @Test
    fun updatingAnItemReplacesItsTags() {
        // Arrange
        val doc = newList()
        val key = addShoppingItem(doc, "Dairy", "Milk", "Whole Milk", 1, "2 pint", null, tags = listOf("Supermarket"))

        // Act
        updateShoppingItem(doc, key, "Dairy", "Milk", "Whole Milk", 1, "2 pint", null, tags = listOf("Farm shop"))

        // Assert
        assertEquals(listOf("Farm shop"), doc.readShoppingItem(key)?.tags)
    }

    @Test
    fun updatingAnItemWithNoTagsClearsThem() {
        // Arrange
        val doc = newList()
        val key = addShoppingItem(doc, "Dairy", "Milk", "Whole Milk", 1, "2 pint", null, tags = listOf("Supermarket"))

        // Act
        updateShoppingItem(doc, key, "Dairy", "Milk", "Whole Milk", 1, "2 pint", null)

        // Assert
        assertEquals(emptyList(), doc.readShoppingItem(key)?.tags)
    }

    @Test
    fun addingTheSameProductAgainKeepsTheTagsOfBothAdds() {
        // Arrange
        val doc = newList()
        addShoppingItem(doc, "Dairy", "Milk", "Whole Milk", 1, "2 pint", null, tags = listOf("Farm shop"))

        // Act
        addShoppingItem(doc, "Dairy", "Milk", "Whole Milk", 1, "2 pint", null, tags = listOf("farm shop", "Supermarket"))

        // Assert
        assertEquals(listOf("Farm shop", "Supermarket"), doc.readShoppingList().single().tags)
    }

    @Test
    fun tagsSurviveSavingAndLoading() {
        // Arrange
        val doc = newList()
        val key = addShoppingItem(doc, "Dairy", "Milk", "Whole Milk", 1, "2 pint", null, tags = listOf("Farm shop"))

        // Act
        val reloaded = Document.load(doc.save())

        // Assert
        assertEquals(listOf("Farm shop"), reloaded.readShoppingItem(key)?.tags)
    }
}
