package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.automerge.Document
import uk.veritaslabs.vesta.ui.screens.MerchantLinkChange

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
    fun aBatchAddAppendsToEachSelectedEntryAndSkipsOnesItAlreadyHas() {
        // Arrange
        val doc = pantryWith("Milk", "Eggs", "Bread")
        setPantryItemMerchants(doc, "milk", listOf("tesco"))

        // Act
        val changed = changePantryMerchants(doc, listOf("milk", "eggs"), MerchantLinkChange.Add(listOf("tesco", "butcher")))

        // Assert
        assertEquals(2, changed)
        assertEquals(listOf("tesco", "butcher"), doc.readPantryItem("milk")?.merchantKeys)
        assertEquals(listOf("tesco", "butcher"), doc.readPantryItem("eggs")?.merchantKeys)
        assertEquals(emptyList(), doc.readPantryItem("bread")?.merchantKeys)
    }

    @Test
    fun aBatchRemoveTakesTheMerchantsOffTheSelectedEntriesOnly() {
        // Arrange
        val doc = pantryWith("Milk", "Eggs")
        setPantryItemMerchants(doc, "milk", listOf("tesco", "butcher"))
        setPantryItemMerchants(doc, "eggs", listOf("tesco", "butcher"))

        // Act
        changePantryMerchants(doc, listOf("milk"), MerchantLinkChange.Remove(setOf("tesco")))

        // Assert
        assertEquals(listOf("butcher"), doc.readPantryItem("milk")?.merchantKeys)
        assertEquals(listOf("tesco", "butcher"), doc.readPantryItem("eggs")?.merchantKeys)
    }

    @Test
    fun aBatchReplaceGivesEverySelectedEntryExactlyTheNewOrder() {
        // Arrange
        val doc = pantryWith("Milk", "Eggs")
        setPantryItemMerchants(doc, "milk", listOf("tesco"))
        setPantryItemMerchants(doc, "eggs", listOf("butcher", "stall"))

        // Act
        changePantryMerchants(doc, listOf("milk", "eggs"), MerchantLinkChange.Replace(listOf("stall", "tesco")))

        // Assert
        assertEquals(listOf("stall", "tesco"), doc.readPantryItem("milk")?.merchantKeys)
        assertEquals(listOf("stall", "tesco"), doc.readPantryItem("eggs")?.merchantKeys)
    }

    @Test
    fun aBatchThatChangesNothingWritesNothing() {
        // Arrange
        val doc = pantryWith("Milk")
        setPantryItemMerchants(doc, "milk", listOf("tesco"))
        val before = doc.heads.toList()

        // Act
        val changed = changePantryMerchants(doc, listOf("milk", "missing"), MerchantLinkChange.Add(listOf("tesco")))

        // Assert
        assertEquals(0, changed)
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
