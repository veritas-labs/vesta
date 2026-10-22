package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.automerge.Document

/**
 * The Pantry document's `merchantKeys` field is dormant (ADR-026, amended 2026-10-22): no screen edits it, but it stays in
 * the document so devices on older builds, which still write it, round-trip without data loss.
 */
class PantryDocumentMerchantsTest {

    private fun pantryWith(vararg titles: String): Document {
        val doc = Document.load(newPantryDocument())
        titles.forEach { upsertPantryItem(doc, "Dairy", null, it, "", barcode = null) }
        return doc
    }

    @Test
    fun anEntryWithNoMerchantsReadsAsEmpty() {
        // Arrange
        val doc = pantryWith("Milk")

        // Act
        val item = doc.readPantryItem("milk")

        // Assert
        assertEquals(emptyList(), item?.merchantKeys)
    }

    @Test
    fun settingMerchantsStoresThemInPriorityOrderWithoutRepeatsOrBlanks() {
        // Arrange
        val doc = pantryWith("Milk")

        // Act
        setPantryItemMerchants(doc, "milk", listOf("tesco", "", "butcher", "tesco"))

        // Assert
        assertEquals(listOf("tesco", "butcher"), doc.readPantryItem("milk")?.merchantKeys)
    }

    @Test
    fun settingMerchantsOnAMissingEntryDoesNothing() {
        // Arrange
        val doc = pantryWith("Milk")
        val before = doc.heads.toList()

        // Act
        setPantryItemMerchants(doc, "nothing", listOf("tesco"))

        // Assert
        assertNull(doc.readPantryItem("nothing"))
        assertEquals(before, doc.heads.toList())
    }

    @Test
    fun settingTheSameMerchantsAgainWritesNothing() {
        // Arrange
        val doc = pantryWith("Milk")
        setPantryItemMerchants(doc, "milk", listOf("tesco"))
        val before = doc.heads.toList()

        // Act
        setPantryItemMerchants(doc, "milk", listOf("tesco"))

        // Assert
        assertEquals(before, doc.heads.toList())
    }

    @Test
    fun editingMerchantsAndStockOnTwoDevicesAtOnceKeepsBoth() {
        // Arrange
        val one = pantryWith("Milk")
        val two = Document.load(one.save())

        // Act
        setPantryItemMerchants(one, "milk", listOf("tesco"))
        adjustStock(two, "milk", 3)
        one.merge(two)

        // Assert
        val item = one.readPantryItem("milk")
        assertEquals(listOf("tesco"), item?.merchantKeys)
        assertEquals(3, item?.stockCount)
    }
}
