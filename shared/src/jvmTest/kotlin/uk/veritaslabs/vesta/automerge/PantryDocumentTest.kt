package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.automerge.Document

class PantryDocumentTest {

    @Test
    fun newDocumentReadsBackAsEmpty() {
        // Arrange
        val doc = Document.load(newPantryDocument())

        // Act
        val items = doc.readPantry()

        // Assert
        assertEquals(emptyList(), items)
    }

    @Test
    fun firstUpsertCreatesAnEntryWithUseCountOneAndStockCountZero() {
        // Arrange
        val doc = Document.load(newPantryDocument())

        // Act
        val key = upsertPantryItem(
            doc,
            primaryCategory = "Condiments",
            secondaryCategory = "Ketchup",
            title = "Heinz Ketchup",
            packageSize = "6 pack",
            barcode = "5000157024671",
            now = { "2026-09-28T09:00:00Z" },
        )

        // Assert
        assertEquals("5000157024671", key)
        assertEquals(
            PantryItem(
                id = "5000157024671",
                primaryCategory = "Condiments",
                secondaryCategory = "Ketchup",
                title = "Heinz Ketchup",
                packageSize = "6 pack",
                barcode = "5000157024671",
                useCount = 1,
                lastUsedAt = "2026-09-28T09:00:00Z",
                stockCount = 0,
            ),
            doc.readPantryItem(key),
        )
    }

    @Test
    fun dedupKeyIsNormalizedTitleWhenThereIsNoBarcode() {
        // Arrange
        val doc = Document.load(newPantryDocument())

        // Act
        val key = upsertPantryItem(
            doc,
            primaryCategory = "Dairy",
            secondaryCategory = "Milk",
            title = "  Whole Milk  ",
            packageSize = "2 pint",
            barcode = null,
            now = { "2026-09-28T09:00:00Z" },
        )

        // Assert
        assertEquals("whole milk", key)
    }

    @Test
    fun secondUpsertAtTheSameDedupKeyIncrementsUseCountAndRefreshesFields() {
        // Arrange
        val doc = Document.load(newPantryDocument())
        val key = upsertPantryItem(
            doc,
            primaryCategory = "Condiments",
            secondaryCategory = "Ketchup",
            title = "Heinz Ketchup",
            packageSize = "6 pack",
            barcode = "5000157024671",
            now = { "2026-09-28T09:00:00Z" },
        )

        // Act
        upsertPantryItem(
            doc,
            primaryCategory = "Condiments",
            secondaryCategory = "Ketchup",
            title = "Heinz Ketchup",
            packageSize = "12 pack",
            barcode = "5000157024671",
            now = { "2026-09-29T09:00:00Z" },
        )

        // Assert
        val item = doc.readPantryItem(key)
        assertEquals(2, item?.useCount)
        assertEquals("12 pack", item?.packageSize)
        assertEquals("2026-09-29T09:00:00Z", item?.lastUsedAt)
    }

    @Test
    fun readPantryOrdersByUseCountDescendingThenLastUsedAtDescending() {
        // Arrange
        val doc = Document.load(newPantryDocument())
        val ketchupKey = upsertPantryItem(
            doc, "Condiments", "Ketchup", "Heinz Ketchup", "6 pack", "111", now = { "2026-09-20T09:00:00Z" },
        )
        upsertPantryItem(doc, "Condiments", "Ketchup", "Heinz Ketchup", "6 pack", "111", now = { "2026-09-27T09:00:00Z" })
        val milkKey = upsertPantryItem(doc, "Dairy", "Milk", "Whole Milk", "2 pint", null, now = { "2026-09-28T09:00:00Z" })

        // Act
        val order = doc.readPantry().map { it.id }

        // Assert
        assertEquals(listOf(ketchupKey, milkKey), order)
    }

    @Test
    fun adjustStockIncrementsAndDecrementsAnExistingEntry() {
        // Arrange
        val doc = Document.load(newPantryDocument())
        val key = upsertPantryItem(doc, "Dairy", "Milk", "Whole Milk", "2 pint", null, now = { "2026-09-28T09:00:00Z" })

        // Act
        adjustStock(doc, key, 6)
        adjustStock(doc, key, -2)

        // Assert
        assertEquals(4, doc.readPantryItem(key)?.stockCount)
    }

    @Test
    fun adjustStockIsANoOpWhenTheEntryDoesNotExist() {
        // Arrange
        val doc = Document.load(newPantryDocument())

        // Act
        adjustStock(doc, "does-not-exist", 6)

        // Assert
        assertNull(doc.readPantryItem("does-not-exist"))
    }

    @Test
    fun correctStockComputesADeltaFromTheCurrentValue() {
        // Arrange
        val doc = Document.load(newPantryDocument())
        val key = upsertPantryItem(doc, "Dairy", "Milk", "Whole Milk", "2 pint", null, now = { "2026-09-28T09:00:00Z" })
        adjustStock(doc, key, 6)

        // Act
        correctStock(doc, key, 3)

        // Assert
        assertEquals(3, doc.readPantryItem(key)?.stockCount)
    }

    @Test
    fun deletePantryItemRemovesTheEntry() {
        // Arrange
        val doc = Document.load(newPantryDocument())
        val key = upsertPantryItem(doc, "Dairy", "Milk", "Whole Milk", "2 pint", null, now = { "2026-09-28T09:00:00Z" })

        // Act
        deletePantryItem(doc, key)

        // Assert
        assertNull(doc.readPantryItem(key))
        assertEquals(emptyList(), doc.readPantry())
    }

    @Test
    fun deletedEntryIsRecreatedByTheNextUpsertAtTheSameKey() {
        // Arrange
        val doc = Document.load(newPantryDocument())
        val key = upsertPantryItem(doc, "Dairy", "Milk", "Whole Milk", "2 pint", null, now = { "2026-09-28T09:00:00Z" })
        deletePantryItem(doc, key)

        // Act
        upsertPantryItem(doc, "Dairy", "Milk", "Whole Milk", "2 pint", null, now = { "2026-09-29T09:00:00Z" })

        // Assert
        val item = doc.readPantryItem(key)
        assertEquals(1, item?.useCount)
        assertEquals(0, item?.stockCount)
    }

    @Test
    fun readPantryRoundTripsThroughSaveAndLoad() {
        // Arrange
        val doc = Document.load(newPantryDocument())
        val key = upsertPantryItem(doc, "Dairy", "Milk", "Whole Milk", "2 pint", null, now = { "2026-09-28T09:00:00Z" })
        adjustStock(doc, key, 4)

        // Act
        val reloaded = Document.load(doc.save())

        // Assert
        assertEquals(doc.readPantryItem(key), reloaded.readPantryItem(key))
    }
}
