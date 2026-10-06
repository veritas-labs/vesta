package uk.veritaslabs.vesta.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails

/**
 * A device that joined a Home holds empty placeholder documents until its creator's seeded ones arrive (see
 * `storeEmptyPantry`). An edit made in that window must be kept, and must not cost the creator its own items.
 */
class UnseededEditTest {

    private suspend fun titles(source: PantryDocumentSource) = source.contents.first().items.map { it.title }.sorted()

    private val milk = ShoppingItemDetails(primaryCategory = "Dairy", title = "Whole Milk")
    private val mince = ShoppingItemDetails(primaryCategory = "Meat", title = "Beef Mince")

    /** Exchanges Pantry sync messages until both sides are up to date. */
    private suspend fun sync(a: PantryDocumentSource, b: PantryDocumentSource) {
        val atB = SyncState()
        val atA = SyncState()
        repeat(20) {
            val toB = a.generateSync(atB)?.also { b.receiveSync(atA, it) }
            val toA = b.generateSync(atA)?.also { a.receiveSync(atB, it) }
            if (toB == null && toA == null) return
        }
    }

    private suspend fun sync(a: ShoppingListDocumentSource, b: ShoppingListDocumentSource) {
        val atB = SyncState()
        val atA = SyncState()
        repeat(20) {
            val toB = a.generateSync(atB)?.also { b.receiveSync(atA, it) }
            val toA = b.generateSync(atA)?.also { a.receiveSync(atB, it) }
            if (toB == null && toA == null) return
        }
    }

    @Test
    fun anItemSavedBeforeTheCreatorsPantryArrivesIsKeptAndSoIsTheCreatorsItem() = runBlocking {
        // Arrange: the creator seeded the Pantry and has an item; the joiner still has the empty placeholder
        val creator = PantryDocumentSource(Document.load(newPantryDocument()))
        creator.edit { ensurePantryItem(it, "Dairy", null, "Whole Milk", "", null) }
        val joiner = PantryDocumentSource(Document())

        // Act: the joiner saves an item before any sync, then the two meet
        joiner.edit {
            ensurePantryCategories(it, "Meat", null)
            ensurePantryItem(it, "Meat", null, "Beef Mince", "", null)
        }
        val beforeSync = titles(joiner)
        sync(creator, joiner)

        // Assert: nothing was written to the placeholder, and the edit landed on the creator's document once it arrived
        assertEquals(emptyList(), beforeSync)
        val expected = listOf("Beef Mince", "Whole Milk")
        assertEquals(expected, titles(creator), "creator")
        assertEquals(expected, titles(joiner), "joiner")
    }

    @Test
    fun editsMadeBeforeTheCreatorsPantryArrivesAreAppliedInOrder() = runBlocking {
        // Arrange
        val creator = PantryDocumentSource(Document.load(newPantryDocument()))
        val joiner = PantryDocumentSource(Document())

        // Act: add an item, then delete it, both before the Pantry arrives
        joiner.edit { ensurePantryItem(it, "Dairy", null, "Whole Milk", "", null) }
        joiner.delete("whole milk")
        sync(creator, joiner)

        // Assert
        assertEquals(emptyList(), titles(creator))
        assertEquals(emptyList(), titles(joiner))
    }

    @Test
    fun anItemAddedToTheShoppingListBeforeItsCreatorsListArrivesIsKeptAndLandsInThePantry() = runBlocking {
        // Arrange: the creator has seeded both documents and listed an item; the joiner has placeholders
        val creatorPantry = PantryDocumentSource(Document.load(newPantryDocument()))
        val creatorList = ShoppingListDocumentSource(Document.load(newShoppingListDocument()), creatorPantry)
        creatorList.save(milk)
        val joinerPantry = PantryDocumentSource(Document())
        val joinerList = ShoppingListDocumentSource(Document(), joinerPantry)

        // Act: the joiner lists an item before anything has synced, then the two meet
        joinerList.save(mince)
        sync(creatorList, joinerList)
        sync(creatorPantry, joinerPantry)

        // Assert: both devices have both items on the list and in the Pantry
        val expected = listOf("Beef Mince", "Whole Milk")
        assertEquals(expected, creatorList.items.first().map { it.title }.sorted(), "creator list")
        assertEquals(expected, joinerList.items.first().map { it.title }.sorted(), "joiner list")
        assertEquals(expected, titles(creatorPantry), "creator pantry")
        assertEquals(expected, titles(joinerPantry), "joiner pantry")
    }
}
