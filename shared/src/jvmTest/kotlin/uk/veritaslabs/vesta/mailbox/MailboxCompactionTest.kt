package uk.veritaslabs.vesta.mailbox

import uk.veritaslabs.vesta.getDeviceName
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.storeEmptyPantry
import uk.veritaslabs.vesta.automerge.storeEmptyShoppingList
import uk.veritaslabs.vesta.datasync.HomeRosters
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.PantryItemDetails

/** Garbage collection of a Home's mailbox (ADR-020 plan, slice 5): snapshots, deleting old entries, and readers that jump. */
class MailboxCompactionTest {

    private val homeId = "home-1"
    private val network = FakeX0xNetwork()
    private var now = 0L

    private fun day(n: Int) = n.days.inWholeMilliseconds

    private inner class Device(val agentId: String, val creator: Boolean = false) {
        val database: KitsDatabase = openInMemoryDatabase()
        val profiles = ProfileRepository(database, deviceName = ::getDeviceName)
        val rosters = HomeRosters(database, profiles, deviceName = ::getDeviceName)
        val pantries = PantrySources(database)
        val lists = ShoppingListSources(database, pantries)
        val node = network.node(agentId)
        var storeId: String? = null
        val documents = HomeMailboxDocuments(uk.veritaslabs.vesta.datasync.TestHomeDocuments(database, rosters, publishRosters = {}, sources = uk.veritaslabs.vesta.automerge.VestaHomeSources(database, pantries = pantries, shoppingLists = lists)).registry)
        val writer = MailboxWriter(node, database, documents, nowMillis = { now }) { storeId }
        val reader = MailboxReader(node, database, documents) { storeId }

        init {
            runBlocking {
                database.homeQueries.insert(homeId, "Maple Street", "family", 1L)
                if (!creator) {
                    storeEmptyPantry(database, homeId)
                    storeEmptyShoppingList(database, homeId)
                }
            }
        }

        suspend fun add(title: String) =
            pantries.forHome(homeId)!!.save(PantryItemDetails(primaryCategory = "Food", title = title, packageSize = "1 kg", stockCount = 1))

        suspend fun titles() = pantries.forHome(homeId)!!.contents.first().items.map { it.title }.sorted()

        suspend fun keys() = node.listStoreKeys(storeId!!).map { it.key }.sorted()

        suspend fun entryKeys() = keys().filter { MailboxEntry.parseKey(it) != null }

        suspend fun snapshotKeys() = keys().filter { SnapshotKeys.isSnapshotKey(it) }

        /** One entry per call (the Pantry alone), so the numbering the tests expect is easy to follow. */
        suspend fun depositPantry() = writer.deposit(homeId, pantryDocumentId(homeId))

        suspend fun heads() = documents.open(homeId, pantryDocumentId(homeId))!!.heads()
    }

    private val groupId by lazy { runBlocking { ann.node.createGroup("Home") } }
    private val ann = Device("agent-ann", creator = true)
    private val bob = Device("agent-bob")

    /** Ann (who made the Home) and Bob in one group; [late] joins later, as a new device would. */
    private suspend fun open(): Device {
        bob.node.joinGroup(ann.node.createGroupInvite(groupId))
        ann.storeId = ann.node.openGroupStore(groupId, "mailbox")
        bob.storeId = bob.node.openGroupStore(groupId, "mailbox")
        return ann
    }

    private suspend fun late(): Device {
        val carol = Device("agent-carol")
        carol.node.joinGroup(ann.node.createGroupInvite(groupId))
        carol.storeId = carol.node.openGroupStore(groupId, "mailbox")
        return carol
    }

    @Test
    fun aSnapshotIsPublishedAtOnceThenEveryIntervalAndOldEntriesAreDeletedOnceASnapshotCoveringThemIsOldEnough() = runBlocking {
        // Arrange
        open()
        ann.add("Flour")
        ann.depositPantry()
        ann.add("Rice")
        ann.depositPantry()

        // Act and Assert: the first snapshot has no earlier one to wait for, and keeps the entries a while
        ann.writer.compact(homeId)
        assertTrue(ann.snapshotKeys().contains("snapshot/agent-ann/0000000001/manifest"))
        assertEquals(2, ann.entryKeys().size)

        // not another until the interval has passed
        ann.add("Oats")
        ann.depositPantry()
        now = day(14)
        ann.writer.compact(homeId)
        assertTrue(ann.snapshotKeys().none { it.contains("/0000000002/") })

        // then the second replaces the first
        now = day(16)
        ann.writer.compact(homeId)
        assertTrue(ann.snapshotKeys().contains("snapshot/agent-ann/0000000002/manifest"))
        assertTrue(ann.snapshotKeys().none { it.contains("/0000000001/") })

        // the first is a retention window old: the entries it covers go, the one after it stays
        now = day(31)
        ann.writer.compact(homeId)
        assertEquals(listOf("agent-ann/0000000002"), ann.entryKeys())

        // and so does the rest once the second is old enough
        now = day(47)
        ann.writer.compact(homeId)
        assertEquals(emptyList(), ann.entryKeys())
        assertTrue(ann.snapshotKeys().isNotEmpty())
    }

    @Test
    fun aDeviceThatJoinsAfterTheEntriesAreGoneBuildsTheSameDocumentFromTheSnapshotAndTheTail() = runBlocking {
        // Arrange
        open()
        ann.add("Flour")
        ann.depositPantry()
        now = day(16)
        ann.writer.compact(homeId) // snapshot of Flour
        ann.add("Rice")
        ann.depositPantry()
        now = day(50)
        ann.writer.compact(homeId) // deletes the entry the snapshot covers, and snapshots again
        ann.add("Oats")
        ann.depositPantry()
        val carol = late()

        // Act
        val applied = carol.reader.fetch(homeId)

        // Assert
        assertTrue(applied >= 2, "expected the snapshot and the tail, applied $applied")
        assertEquals(listOf("Flour", "Oats", "Rice"), carol.titles())
        assertContentEquals(ann.heads(), carol.heads())
    }

    @Test
    fun aReaderPartWayThroughJumpsOverEntriesTheWriterHasDeleted() = runBlocking {
        // Arrange
        open()
        ann.add("Flour")
        ann.depositPantry()
        bob.reader.fetch(homeId) // Bob has entry 0
        ann.add("Rice")
        ann.depositPantry()
        ann.add("Oats")
        ann.depositPantry()
        now = day(16)
        ann.writer.compact(homeId)
        now = day(50)
        ann.writer.compact(homeId) // entries 0..2 are deleted, the snapshot remains

        // Act
        bob.reader.fetch(homeId)

        // Assert
        assertEquals(emptyList(), ann.entryKeys())
        assertEquals(listOf("Flour", "Oats", "Rice"), bob.titles())
    }

    @Test
    fun theWritersDeletionsReachTheOtherReplicasAndOnlyTheNewestSnapshotIsKept() = runBlocking {
        // Arrange
        open()
        ann.add("Flour")
        ann.depositPantry()
        now = day(16)
        ann.writer.compact(homeId)
        val firstGeneration = ann.snapshotKeys().filter { it.contains("/0000000001/") }
        ann.add("Rice")
        ann.depositPantry()

        // Act
        now = day(32)
        ann.writer.compact(homeId) // second snapshot; the first is superseded and deleted at once
        val afterSecond = ann.snapshotKeys()
        now = day(80)
        ann.writer.compact(homeId)

        // Assert
        assertTrue(firstGeneration.isNotEmpty())
        assertTrue(afterSecond.none { it.contains("/0000000001/") })
        assertTrue(afterSecond.any { it.contains("/0000000002/manifest") })
        assertEquals(emptyList(), bob.entryKeys()) // Bob's replica no longer has the entries either
        assertEquals(ann.snapshotKeys(), bob.snapshotKeys())
    }

    @Test
    fun theStoreStaysBoundedOverSixMonthsAndAFreshDeviceStillConverges() = runBlocking {
        // Arrange
        open()
        val sizes = mutableListOf<Int>()

        // Act: an item every day for 180 days, compacting daily as the sync rounds would
        for (d in 1..180) {
            now = day(d)
            ann.add("Item $d")
            ann.depositPantry()
            ann.writer.compact(homeId)
            bob.reader.fetch(homeId)
            if (d % 30 == 0) sizes += ann.keys().size
        }
        val carol = late()
        carol.reader.fetch(homeId)

        // Assert
        val entries = ann.entryKeys().size
        assertTrue(entries <= 50, "entries of the last ~45 days only, found $entries")
        assertTrue(sizes.last() <= sizes[2] + 15, "the store keeps growing: $sizes")
        assertEquals(180, carol.titles().size)
        assertEquals(180, bob.titles().size)
        assertContentEquals(ann.heads(), carol.heads())
    }

    @Test
    fun aSnapshotWithoutItsManifestOrWithAChunkMissingIsNotUsed() = runBlocking {
        // Arrange
        open()
        ann.add("Flour")
        ann.depositPantry()
        now = day(16)
        ann.writer.compact(homeId)
        now = day(50)
        ann.writer.compact(homeId) // entry 0 deleted: only the snapshot has Flour
        val manifestKey = ann.snapshotKeys().single { it.endsWith("/manifest") }
        val manifest = ann.node.getValue(ann.storeId!!, manifestKey)!!
        ann.node.deleteValue(ann.storeId!!, manifestKey)
        val carol = late()

        // Act
        val withoutManifest = carol.reader.fetch(homeId)
        ann.node.putValue(ann.storeId!!, manifestKey, manifest)
        val chunkKey = ann.snapshotKeys().first { !it.endsWith("/manifest") }
        val chunk = ann.node.getValue(ann.storeId!!, chunkKey)!!
        ann.node.deleteValue(ann.storeId!!, chunkKey)
        val withoutChunk = carol.reader.fetch(homeId)
        ann.node.putValue(ann.storeId!!, chunkKey, chunk)
        val complete = carol.reader.fetch(homeId)

        // Assert
        assertEquals(0, withoutManifest)
        assertEquals(0, withoutChunk)
        assertTrue(complete > 0)
        assertEquals(listOf("Flour"), carol.titles())
    }

    @Test
    fun aWriterWithNoRecordOfItsOwnNumbersContinuesAfterTheOnesASnapshotCovers() = runBlocking {
        // Arrange
        open()
        ann.add("Flour")
        ann.depositPantry()
        ann.add("Rice")
        ann.depositPantry()
        now = day(16)
        ann.writer.compact(homeId)
        now = day(50)
        ann.writer.compact(homeId) // every entry deleted; the snapshot covers through 1
        val restored = Device("agent-ann", creator = true) // the same agent on a database with no record of what it wrote
        restored.storeId = ann.storeId
        restored.add("Oats")

        // Act
        restored.writer.deposit(homeId, pantryDocumentId(homeId))

        // Assert
        assertEquals(listOf("agent-ann/0000000002"), restored.entryKeys())
        assertNotNull(restored.node.getValue(ann.storeId!!, "agent-ann/0000000002"))
        assertNull(restored.node.getValue(ann.storeId!!, "agent-ann/0000000000"))
    }

    @Test
    fun snapshotKeysAndManifestsRoundTrip() {
        // Arrange
        val key = SnapshotKeys.chunk("agent-1", 3, 12)
        val manifest = SnapshotManifest(coversThrough = 4_000_000_000L, chunkCount = 70_000)

        // Act
        val parsed = SnapshotKeys.parse(key)
        val decoded = SnapshotManifest.decode(manifest.encode())!!

        // Assert
        assertEquals("snapshot/agent-1/0000000003/0000000012", key)
        assertEquals("agent-1", parsed!!.writer)
        assertEquals(3L, parsed.generation)
        assertEquals(12, parsed.chunk)
        assertNull(SnapshotKeys.parse(SnapshotKeys.manifest("agent-1", 3))!!.chunk)
        assertNull(MailboxEntry.parseKey(key))
        assertEquals(4_000_000_000L, decoded.coversThrough)
        assertEquals(70_000, decoded.chunkCount)
        assertNull(SnapshotManifest.decode(ByteArray(13)))
    }
}
