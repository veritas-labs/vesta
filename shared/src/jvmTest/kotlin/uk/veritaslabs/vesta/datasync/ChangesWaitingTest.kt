package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.privateCalendarDocumentId
import uk.veritaslabs.vesta.automerge.privateTasksDocumentId
import uk.veritaslabs.vesta.automerge.shoppingListDocumentId
import uk.veritaslabs.kits.store.openInMemoryDatabase
import uk.veritaslabs.kits.x0x.testing.FakeX0xNetwork
import uk.veritaslabs.vesta.ui.screens.HomeShape

class ChangesWaitingTest {

    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun eventually(check: suspend () -> Boolean) {
        withTimeout(10.seconds) { while (!check()) delay(20.milliseconds) }
    }

    private suspend fun ActivityDevice.waiting(homeId: String) = sync.syncActivity.snapshot(homeId).first().changesWaiting

    @Test
    fun aLocalChangeWithNobodyOnlineIsWaiting() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a", mailbox = false)
        val b = ActivityDevice(network, scope, "agent-b", mailbox = false)
        a.pairedWith(b)
        network.offline += "agent-b"
        a.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        a.addToPantry(homeId, "Whole Milk")
        delay(400.milliseconds) // several rounds, in which nothing is acknowledged

        // Assert
        assertTrue(a.waiting(homeId))
        scope.cancel()
    }

    @Test
    fun afterAPeerSyncsAndRepliesNothingIsWaiting() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a", mailbox = false)
        val b = ActivityDevice(network, scope, "agent-b", mailbox = false)
        a.pairedWith(b)
        b.pairedWith(a)
        network.offline += "agent-b"
        a.start()
        b.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        a.addToPantry(homeId, "Whole Milk")
        delay(300.milliseconds)
        assertTrue(a.waiting(homeId))

        // Act
        network.comeOnline("agent-b")

        // Assert
        eventually { !a.waiting(homeId) }
        scope.cancel()
    }

    @Test
    fun aSendThePeerNeverAnsweredIsStillWaiting() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a", mailbox = false)
        val b = ActivityDevice(network, scope, "agent-b", mailbox = false)
        a.pairedWith(b)
        network.hanging += "agent-b"
        network.hangFor = 20.milliseconds
        a.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        a.addToPantry(homeId, "Whole Milk")
        delay(400.milliseconds)

        // Assert
        assertTrue(b.node.sentTo.isEmpty())
        assertTrue(a.node.sentTo.contains("agent-b"))
        assertTrue(a.waiting(homeId))
        scope.cancel()
    }

    @Test
    fun aChangeDepositedInTheHomesMailboxIsNotWaitingEvenWithNobodyOnline() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a")
        val b = ActivityDevice(network, scope, "agent-b")
        a.pairedWith(b)
        network.offline += "agent-b"
        a.start()
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)

        // Act
        a.addToPantry(homeId, "Whole Milk")

        // Assert
        eventually { !a.waiting(homeId) }
        assertTrue(a.database.mailboxQueries.selectHeads(homeId, pantryDocumentId(homeId)).awaitAsOneOrNull() != null)
        scope.cancel()
    }

    @Test
    fun aFreshServiceWithAChangedDocumentAndNoMailboxRecordIsWaiting() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a", mailbox = false)
        val b = ActivityDevice(network, scope, "agent-b", mailbox = false)
        a.pairedWith(b)
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        a.addToPantry(homeId, "Whole Milk")

        // Act
        val waiting = a.waiting(homeId)

        // Assert
        assertTrue(waiting)
        scope.cancel()
    }

    @Test
    fun aHomeWithNoOtherDeviceNeverHasChangesWaiting() = runBlocking {
        // Arrange
        val a = ActivityDevice(network, scope, "agent-a", mailbox = false)
        val homeId = a.homes.create("Maple Street", HomeShape.Family.storageValue)
        a.addToPantry(homeId, "Whole Milk")

        // Act
        val waiting = a.waiting(homeId)

        // Assert
        assertFalse(waiting)
        scope.cancel()
    }

    // The rules themselves, without a mesh: which documents are covered by what.

    private val home = "home"
    private val shared = listOf(pantryDocumentId(home), shoppingListDocumentId(home))
    private val calendar = privateCalendarDocumentId(home)
    private val edited = byteArrayOf(1, 2, 3)

    private fun rules(
        targets: SyncTargets,
        heads: Map<String, ByteArray?>,
        database: uk.veritaslabs.kits.store.KitsDatabase = openInMemoryDatabase(),
    ) = ChangesWaiting(database, { shared }, { listOf(calendar, privateTasksDocumentId(it)) }, { targets }, { _, id -> heads[id] })

    @Test
    fun aMailboxDepositOfTheCurrentHeadsCoversASharedDocument() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert(home, "Home", "family", 0L)
        val rules = rules(SyncTargets(shared = true, own = false), mapOf(shared[0] to edited, shared[1] to null), database)
        rules.update(home, emptySet())
        assertTrue(rules.of(home))

        // Act
        database.mailboxQueries.upsertHeads(home, shared[0], edited)
        rules.update(home, emptySet())

        // Assert
        assertFalse(rules.of(home))
    }

    @Test
    fun aMailboxDepositOfOlderHeadsDoesNotCoverAnEditMadeSince() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert(home, "Home", "family", 0L)
        database.mailboxQueries.upsertHeads(home, shared[0], byteArrayOf(9))
        val rules = rules(SyncTargets(shared = true, own = false), mapOf(shared[0] to edited), database)

        // Act
        rules.update(home, emptySet())

        // Assert
        assertTrue(rules.of(home))
    }

    @Test
    fun aDocumentSomePeerAcknowledgedOrNobodyEditedIsCovered() = runBlocking {
        // Arrange
        val rules = rules(SyncTargets(shared = true, own = false), mapOf(shared[0] to edited, shared[1] to ByteArray(0)))

        // Act
        rules.update(home, setOf(shared[0]))

        // Assert
        assertFalse(rules.of(home))
    }

    @Test
    fun aPrivateDocumentIsCoveredOnlyByTheUsersOwnDevicesAndNotByAMailboxDeposit() = runBlocking {
        // Arrange
        val database = openInMemoryDatabase()
        database.homeQueries.insert(home, "Home", "family", 0L)
        shared.forEach { database.mailboxQueries.upsertHeads(home, it, edited) }
        database.mailboxQueries.upsertHeads(home, calendar, edited)
        val heads = mapOf(shared[0] to edited, shared[1] to edited, calendar to edited)
        val membersOnly = rules(SyncTargets(shared = true, own = false), heads, database)
        val withOwnDevice = rules(SyncTargets(shared = true, own = true), heads, database)

        // Act
        membersOnly.update(home, shared.toSet())
        withOwnDevice.update(home, shared.toSet())
        val acknowledgedByOwn = rules(SyncTargets(shared = true, own = true), heads, database).also { it.update(home, shared.toSet() + calendar) }

        // Assert
        assertFalse(membersOnly.of(home)) // another member's device is somewhere the private calendar cannot go
        assertTrue(withOwnDevice.of(home))
        assertFalse(acknowledgedByOwn.of(home))
    }

    @Test
    fun nothingIsWaitingWhenThereIsNowhereToSend() = runBlocking {
        // Arrange
        val rules = rules(SyncTargets(shared = false, own = false), mapOf(shared[0] to edited, calendar to edited))

        // Act
        rules.update(home, emptySet())

        // Assert
        assertEquals(false, rules.of(home))
    }
}
