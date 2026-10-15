package uk.veritaslabs.vesta.mailbox

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.storeEmptyPantry
import uk.veritaslabs.vesta.automerge.storeEmptyShoppingList
import uk.veritaslabs.vesta.datasync.HomeRosters
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.db.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails
import uk.veritaslabs.vesta.ui.screens.ShoppingItemDetails

/** The mailbox writer and reader (ADR-020 plan, slice 3) over real documents and the fake mesh's group store. */
class MailboxTest {

    private val homeId = "home-1"
    private val network = FakeX0xNetwork()

    /** One device: its own database, documents, writer and reader, with the Home's mailbox store once [open]. */
    private inner class Device(val agentId: String, val creator: Boolean = false) {
        val database: VestaDatabase = openInMemoryDatabase()
        val profiles = ProfileRepository(database)
        val rosters = HomeRosters(database, profiles)
        val pantries = PantrySources(database)
        val lists = ShoppingListSources(database, pantries)
        val node = network.node(agentId)
        var storeId: String? = null
        val merchants = uk.veritaslabs.vesta.automerge.MerchantsSources(database)
        val documents = HomeMailboxDocuments(pantries, lists, uk.veritaslabs.vesta.automerge.CookbookSources(database), rosters, merchants)
        var writer = MailboxWriter(node, database, documents) { storeId }
        val reader = MailboxReader(node, database, documents) { storeId }

        init {
            runBlocking {
                database.homeQueries.insert(homeId, "Maple Street", "family", 1L)
                if (!creator) {
                    // A Home that came from another device: its documents start empty and fill from the creator's.
                    storeEmptyPantry(database, homeId)
                    storeEmptyShoppingList(database, homeId)
                }
            }
        }

        suspend fun addToPantry(title: String) =
            pantries.forHome(homeId)!!.save(PantryItemDetails(primaryCategory = "Food", title = title, packageSize = "1 kg", stockCount = 1))

        suspend fun pantryTitles() = pantries.forHome(homeId)!!.contents.first().items.map { it.title }.sorted()

        suspend fun addToList(title: String) =
            lists.forHome(homeId)!!.save(ShoppingItemDetails(primaryCategory = "Food", title = title, quantity = 2))

        suspend fun listTitles() = lists.forHome(homeId)!!.items.first().map { it.title }.sorted()

        suspend fun entryKeys() = node.listStoreKeys(storeId!!).map { it.key }.sorted()
    }

    /** Two devices of a household, in one group with the `mailbox` store open; [ann] made the Home. */
    private inner class Household {
        val ann = Device("agent-ann", creator = true)
        val bob = Device("agent-bob")

        suspend fun open(): Household {
            val groupId = ann.node.createGroup("Home")
            bob.node.joinGroup(ann.node.createGroupInvite(groupId))
            ann.storeId = ann.node.openGroupStore(groupId, "mailbox")
            bob.storeId = bob.node.openGroupStore(groupId, "mailbox")
            return this
        }
    }

    @Test
    fun pantryChangesReachAnotherDeviceThroughTheStoreAlone() = runBlocking {
        // Arrange
        val home = Household().open()
        home.ann.addToPantry("Flour")
        home.ann.addToPantry("Rice")

        // Act
        val written = home.ann.writer.deposit(homeId, pantryDocumentId(homeId))
        val applied = home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(1, written)
        assertEquals(1, applied)
        assertEquals(listOf("Flour", "Rice"), home.bob.pantryTitles())
        assertEquals(emptyList(), home.ann.node.sentTo)
    }

    @Test
    fun shoppingListAndRosterTravelToo() = runBlocking {
        // Arrange
        val home = Household().open()
        home.ann.addToList("Milk")
        home.ann.rosters.admit(homeId, "member-ann", "Ann", "admin", "agent-ann")

        // Act
        home.ann.writer.deposit(homeId)
        home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(listOf("Milk"), home.bob.listTitles())
        assertEquals(setOf("member-ann"), home.bob.rosters.devices(homeId).keys)
        assertEquals(emptyList(), home.bob.pantryTitles()) // listing an item does not put it in the Pantry
    }

    @Test
    fun merchantsTravelThroughTheStoreToo() = runBlocking {
        // Arrange
        val home = Household().open()
        home.ann.merchants.forHome(homeId)!!.save(uk.veritaslabs.vesta.ui.screens.Merchant(name = "Dave's Veg Stall"))

        // Act
        home.ann.writer.deposit(homeId, uk.veritaslabs.vesta.automerge.merchantsDocumentId(homeId))
        home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(listOf("Dave's Veg Stall"), home.bob.merchants.forHome(homeId)!!.merchants.first().map { it.name })
    }

    @Test
    fun depositingOrFetchingAgainWithNothingNewDoesNothing() = runBlocking {
        // Arrange
        val home = Household().open()
        home.ann.addToPantry("Flour")
        home.ann.writer.deposit(homeId)
        home.bob.reader.fetch(homeId)

        // Act
        val written = home.ann.writer.deposit(homeId)
        val applied = home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(0, written)
        assertEquals(0, applied)
        assertEquals(listOf("Flour"), home.bob.pantryTitles())
    }

    @Test
    fun laterEditsAreDepositedAsOnlyWhatChangedAndNumberingContinuesAfterARestart() = runBlocking {
        // Arrange
        val home = Household().open()
        home.ann.addToPantry("Flour")
        home.ann.writer.deposit(homeId, pantryDocumentId(homeId))
        home.bob.reader.fetch(homeId)
        val firstSize = home.ann.node.listStoreKeys(home.ann.storeId!!).single().size!!
        home.ann.addToPantry("Rice")
        home.ann.writer = MailboxWriter(home.ann.node, home.ann.database, home.ann.documents) { home.ann.storeId } // as after a restart

        // Act
        home.ann.writer.deposit(homeId, pantryDocumentId(homeId))
        val applied = home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(listOf("agent-ann/0000000000", "agent-ann/0000000001"), home.ann.entryKeys())
        val secondSize = home.ann.node.listStoreKeys(home.ann.storeId!!).first { it.key.endsWith("1") }.size!!
        assertTrue(secondSize < firstSize, "the second deposit carries one item, the first the whole seeded document")
        assertEquals(1, applied)
        assertEquals(listOf("Flour", "Rice"), home.bob.pantryTitles())
    }

    @Test
    fun aLargeChangeIsCutIntoPartsUnderTheValueLimitAndPutBackTogether() = runBlocking {
        // Arrange
        val home = Household().open()
        repeat(900) { home.ann.addToPantry("Item number $it with a reasonably long descriptive title to add bulk") }

        // Act
        val written = home.ann.writer.deposit(homeId, pantryDocumentId(homeId))
        val applied = home.bob.reader.fetch(homeId)

        // Assert
        assertTrue(written > 1, "expected several parts, wrote $written")
        assertTrue(home.ann.node.listStoreKeys(home.ann.storeId!!).all { it.size!! < 65_536 })
        assertEquals(1, applied)
        assertEquals(900, home.bob.pantryTitles().size)
    }

    @Test
    fun anEntryThatHasNotArrivedHoldsBackTheOnesAfterItUntilItDoes() = runBlocking {
        // Arrange
        val home = Household().open()
        val docId = pantryDocumentId(homeId)
        home.ann.addToPantry("Flour")
        home.ann.writer.deposit(homeId, docId)
        home.ann.addToPantry("Rice")
        home.ann.writer.deposit(homeId, docId)
        home.ann.addToPantry("Oats")
        home.ann.writer.deposit(homeId, docId)
        val second = "agent-ann/0000000001"
        val held = home.ann.node.getValue(home.ann.storeId!!, second)!!
        home.ann.node.deleteValue(home.ann.storeId!!, second)

        // Act
        val before = home.bob.reader.fetch(homeId)
        val titlesBefore = home.bob.pantryTitles()
        home.ann.node.putValue(home.ann.storeId!!, second, held)
        val after = home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(1, before)
        assertEquals(listOf("Flour"), titlesBefore)
        assertEquals(2, after)
        assertEquals(listOf("Flour", "Oats", "Rice"), home.bob.pantryTitles())
    }

    /** Puts [bytes] in [home]'s store as Ann's entry number [sequence]. */
    private suspend fun Household.plant(sequence: Long, bytes: ByteArray) =
        ann.node.putValue(ann.storeId!!, MailboxEntry.key("agent-ann", sequence), bytes)

    @Test
    fun aDepositCutShortByANewerOneIsSkippedAndTheNewerOneApplied() = runBlocking {
        // Arrange
        val home = Household().open()
        val docId = pantryDocumentId(homeId)
        home.ann.addToPantry("Flour")
        val changes = home.ann.pantries.forHome(homeId)!!.changesSince(ByteArray(0))
        home.plant(0, MailboxEntry(docId, deposit = 7, part = 0, partCount = 2, payload = byteArrayOf(1, 2, 3)).encode())
        home.plant(1, MailboxEntry.split(docId, changes, deposit = 8).single().encode())

        // Act
        val applied = home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(1, applied)
        assertEquals(listOf("Flour"), home.bob.pantryTitles())
    }

    @Test
    fun partsOfTwoAttemptsAreNotJoined() = runBlocking {
        // Arrange
        val home = Household().open()
        val docId = pantryDocumentId(homeId)
        home.ann.addToPantry("Flour")
        val changes = home.ann.pantries.forHome(homeId)!!.changesSince(ByteArray(0))
        val half = changes.size / 2
        home.plant(0, MailboxEntry(docId, deposit = 1, part = 0, partCount = 2, payload = changes.copyOfRange(0, half)).encode())
        home.plant(1, MailboxEntry(docId, deposit = 2, part = 1, partCount = 2, payload = changes.copyOfRange(half, changes.size)).encode())

        // Act
        val applied = home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(0, applied)
        assertEquals(emptyList(), home.bob.pantryTitles())
    }

    @Test
    fun aGroupStillArrivingIsReadAgainAndAppliedOnceComplete() = runBlocking {
        // Arrange
        val home = Household().open()
        val docId = pantryDocumentId(homeId)
        home.ann.addToPantry("Flour")
        val changes = home.ann.pantries.forHome(homeId)!!.changesSince(ByteArray(0))
        val half = changes.size / 2
        val second = MailboxEntry(docId, deposit = 5, part = 1, partCount = 2, payload = changes.copyOfRange(half, changes.size))
        home.plant(0, MailboxEntry(docId, deposit = 5, part = 0, partCount = 2, payload = changes.copyOfRange(0, half)).encode())

        // Act
        val whileIncomplete = home.bob.reader.fetch(homeId)
        home.plant(1, second.encode())
        val whenComplete = home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(0, whileIncomplete)
        assertEquals(1, whenComplete)
        assertEquals(listOf("Flour"), home.bob.pantryTitles())
    }

    @Test
    fun anUnreadableOrDamagedEntryIsSkippedWithoutBlockingTheRest() = runBlocking {
        // Arrange
        val home = Household().open()
        val docId = pantryDocumentId(homeId)
        home.ann.addToPantry("Flour")
        val changes = home.ann.pantries.forHome(homeId)!!.changesSince(ByteArray(0))
        home.plant(0, MailboxEntry("d", 1, 0, 1, byteArrayOf()).encode().also { it[0] = 9 }) // a newer format
        home.plant(1, MailboxEntry(docId, 1, 0, 1, byteArrayOf(1, 2, 3, 4, 5)).encode()) // not Automerge changes
        home.plant(2, MailboxEntry("unknown:document", 1, 0, 1, changes).encode()) // a document this version lacks
        home.plant(3, MailboxEntry.split(docId, changes).single().encode())

        // Act
        val applied = home.bob.reader.fetch(homeId)
        val again = home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(1, applied)
        assertEquals(0, again)
        assertEquals(listOf("Flour"), home.bob.pantryTitles())
    }

    @Test
    fun aDeviceDoesNotReadItsOwnEntries() = runBlocking {
        // Arrange
        val home = Household().open()
        home.ann.addToPantry("Flour")
        home.ann.writer.deposit(homeId)

        // Act
        val applied = home.ann.reader.fetch(homeId)

        // Assert
        assertEquals(0, applied)
    }

    @Test
    fun aHomeWithNoOpenStoreWritesAndReadsNothing() = runBlocking {
        // Arrange
        val home = Household().open()
        home.ann.addToPantry("Flour")
        home.ann.storeId = null
        home.bob.storeId = null

        // Act
        val written = home.ann.writer.deposit(homeId)
        val applied = home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(0, written)
        assertEquals(0, applied)
    }

    @Test
    fun twoDevicesThatEachEditConvergeThroughTheStoreWithNeitherOnlineWithTheOther() = runBlocking {
        // Arrange
        val home = Household().open()
        home.ann.addToPantry("Flour")
        home.ann.writer.deposit(homeId)
        home.bob.reader.fetch(homeId)
        network.offline += "agent-ann"
        home.bob.addToPantry("Rice")
        home.ann.addToPantry("Oats") // Ann edits too, while offline

        // Act
        home.bob.writer.deposit(homeId)
        network.comeOnline("agent-ann")
        home.ann.writer.deposit(homeId)
        home.ann.reader.fetch(homeId)
        home.bob.reader.fetch(homeId)

        // Assert
        assertEquals(listOf("Flour", "Oats", "Rice"), home.ann.pantryTitles())
        assertEquals(listOf("Flour", "Oats", "Rice"), home.bob.pantryTitles())
        assertContentEquals(
            home.ann.documents.open(homeId, pantryDocumentId(homeId))!!.heads(),
            home.bob.documents.open(homeId, pantryDocumentId(homeId))!!.heads(),
        )
    }
}
