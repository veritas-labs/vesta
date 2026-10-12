package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import uk.veritaslabs.vesta.ui.screens.MerchantLinkChange
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails

class PantryDocumentSourceMerchantsTest {

    private fun milk(merchantKeys: List<String>? = null, key: String? = null, title: String = "Milk") =
        PantryItemDetails(key = key, primaryCategory = "Dairy", title = title, merchantKeys = merchantKeys)

    private suspend fun PantryDocumentSource.merchantsOf(key: String) =
        contents.first().items.first { it.key == key }.merchantKeys

    @Test
    fun savingAnItemWithMerchantsPublishesThemInOrder() = runBlocking {
        // Arrange
        val source = PantryDocumentSource(Document.load(newPantryDocument()))

        // Act
        source.save(milk(listOf("tesco", "butcher")))

        // Assert
        assertEquals(listOf("tesco", "butcher"), source.merchantsOf("milk"))
    }

    @Test
    fun savingWithoutMerchantsLeavesTheExistingOnesAlone() = runBlocking {
        // Arrange
        val source = PantryDocumentSource(Document.load(newPantryDocument()))
        source.save(milk(listOf("tesco")))

        // Act
        source.save(milk(merchantKeys = null, key = "milk"))

        // Assert
        assertEquals(listOf("tesco"), source.merchantsOf("milk"))
    }

    @Test
    fun savingWithAnEmptyListClearsTheMerchants() = runBlocking {
        // Arrange
        val source = PantryDocumentSource(Document.load(newPantryDocument()))
        source.save(milk(listOf("tesco")))

        // Act
        source.save(milk(merchantKeys = emptyList(), key = "milk"))

        // Assert
        assertEquals(emptyList(), source.merchantsOf("milk"))
    }

    @Test
    fun renamingAnItemMovesItsMerchantsToTheNewKey() = runBlocking {
        // Arrange
        val source = PantryDocumentSource(Document.load(newPantryDocument()))
        source.save(milk(listOf("tesco", "butcher")))

        // Act
        source.save(milk(merchantKeys = null, key = "milk", title = "Oat Milk"))

        // Assert
        assertEquals(listOf("oat milk"), source.contents.first().items.map { it.key })
        assertEquals(listOf("tesco", "butcher"), source.merchantsOf("oat milk"))
    }

    @Test
    fun changingMerchantsInABatchUpdatesEverySelectedItem() = runBlocking {
        // Arrange
        val source = PantryDocumentSource(Document.load(newPantryDocument()))
        source.save(milk(listOf("tesco")))
        source.save(milk(title = "Eggs"))
        source.save(milk(title = "Bread"))

        // Act
        source.changeMerchants(setOf("milk", "eggs"), MerchantLinkChange.Add(listOf("butcher")))

        // Assert
        assertEquals(listOf("tesco", "butcher"), source.merchantsOf("milk"))
        assertEquals(listOf("butcher"), source.merchantsOf("eggs"))
        assertEquals(emptyList(), source.merchantsOf("bread"))
    }

    @Test
    fun aBatchChangeTellsSyncAboutTheLocalEdit() = runBlocking {
        // Arrange
        var told = 0
        val source = PantryDocumentSource(Document.load(newPantryDocument()), onLocalChange = { told++ })
        source.save(milk())
        val before = told

        // Act
        source.changeMerchants(setOf("milk"), MerchantLinkChange.Replace(listOf("tesco")))

        // Assert
        assertEquals(before + 1, told)
    }
}
