package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails

/** Saving an item from the app never loses the dormant `merchantKeys` of its Pantry entry (ADR-026, amended 2026-10-22). */
class PantryDocumentSourceMerchantsTest {

    private fun milk(key: String? = null, title: String = "Milk", stockCount: Int = 0) =
        PantryItemDetails(key = key, primaryCategory = "Dairy", title = title, stockCount = stockCount)

    /** The source over a document whose "milk" entry was given [merchants] by an older device. */
    private fun sourceWithMerchants(merchants: List<String>): Pair<PantryDocumentSource, Document> {
        val doc = Document.load(newPantryDocument())
        upsertPantryItem(doc, "Dairy", null, "Milk", "", barcode = null)
        setPantryItemMerchants(doc, "milk", merchants)
        return PantryDocumentSource(doc) to doc
    }

    @Test
    fun savingAnEditLeavesTheEntrysMerchantsUntouched() = runBlocking {
        // Arrange
        val (source, doc) = sourceWithMerchants(listOf("tesco", "butcher"))

        // Act
        source.save(milk(key = "milk", stockCount = 5))

        // Assert
        assertEquals(listOf("tesco", "butcher"), doc.readPantryItem("milk")?.merchantKeys)
        assertEquals(5, doc.readPantryItem("milk")?.stockCount)
    }

    @Test
    fun renamingAnItemMovesItsMerchantsToTheNewKey() = runBlocking {
        // Arrange
        val (source, doc) = sourceWithMerchants(listOf("tesco", "butcher"))

        // Act
        source.save(milk(key = "milk", title = "Oat Milk"))

        // Assert
        assertEquals(listOf("oat milk"), source.contents.first().items.map { it.key })
        assertEquals(listOf("tesco", "butcher"), doc.readPantryItem("oat milk")?.merchantKeys)
    }

    @Test
    fun anEditTellsSyncAboutTheLocalChange() = runBlocking {
        // Arrange
        var told = 0
        val source = PantryDocumentSource(Document.load(newPantryDocument()), onLocalChange = { told++ })
        source.save(milk())
        val before = told

        // Act
        source.save(milk(key = "milk", stockCount = 2))

        // Assert
        assertEquals(true, told > before)
    }
}
