package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uk.veritaslabs.vesta.devicesync.FakeX0xNetwork
import uk.veritaslabs.vesta.syncactivity.SyncEventFilter
import uk.veritaslabs.vesta.syncactivity.SyncEventKind
import uk.veritaslabs.vesta.syncactivity.SyncVerdict
import uk.veritaslabs.vesta.ui.screens.HomeShape
import uk.veritaslabs.vesta.x0x.X0xDirectMessage

/**
 * Input this version can't use is noted in Sync activity and otherwise dropped as before (docs/adr/ADR-034-document-format-versioning.md, WP4):
 * a message of a known family with an unknown `kind`, and a sync message for a document type this version lacks. The mailbox's newer-version
 * entries are in `MailboxTest`.
 */
class UnsupportedInputActivityTest {

    private val network = FakeX0xNetwork()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The devices' sync loops run on this scope; left running they starve later tests sharing the default dispatcher.
    @AfterTest
    fun stopTheDevices() = scope.cancel()

    private fun json(text: String) = X0xDirectMessage(sender = "agent-b", payload = Base64.Default.encode(text.encodeToByteArray()), verified = true)

    private suspend fun ActivityDevice.events(homeId: String) = recorder.flush().let { database.syncEventQueries.selectPage(homeId, Long.MAX_VALUE, 100).awaitAsList() }

    /** Device A with a Home and a paired device B (which sends the messages; it is never started). */
    private suspend fun device(): Pair<ActivityDevice, String> {
        val a = ActivityDevice(network, scope, "agent-a")
        a.devices.add("agent-b", "agent-b")
        return a to a.homes.create("Maple Street", HomeShape.Family.storageValue)
    }

    @Test
    fun anUnknownDeviceSyncKindFromAPairedDeviceIsNotedOnItsHomes() = runBlocking {
        // Arrange
        val (a, home) = device()
        val second = a.homes.create("Beach House", HomeShape.Family.storageValue)

        // Act
        a.sync.handle(json("""{"kind":"devicesync.teleport","name":"Bob"}"""))

        // Assert
        listOf(home, second).forEach { homeId ->
            val noted = a.events(homeId).single()
            assertEquals(SyncEventKinds.UnknownMessage, noted.kind)
            assertEquals("devicesync.teleport", noted.detail)
            assertEquals("agent-b", noted.peerAgent)
            assertEquals(SyncModules.Members, noted.module)
            assertEquals(1L, noted.count)
        }
    }

    @Test
    fun anUnknownHomeGroupKindIsNotedOnlyOnTheHomeItNames() = runBlocking {
        // Arrange
        val (a, home) = device()
        val other = a.homes.create("Beach House", HomeShape.Family.storageValue)

        // Act
        a.sync.handle(json("""{"kind":"homegroup.invite.v2","homeId":"$home"}"""))

        // Assert
        assertEquals(listOf("homegroup.invite.v2"), a.events(home).map { it.detail })
        assertEquals(emptyList(), a.events(other))
    }

    @Test
    fun messagesThatAreNotOfAKnownFamilyOrAreKnownAreNotNoted() = runBlocking {
        // Arrange
        val (a, home) = device()
        val notOurs = listOf(
            """{"kind":"pantry.add","item":"Milk"}""", // an agent command
            """{"kind":"home.join.v9"}""", // another family's kind
            """{"type":"devicesync.teleport"}""", // no kind
            """{"kind":7}""", // not a string
            """{"kind":"devicesync.hello","name":"Bob"}""", // a known kind
            "not json at all",
            "[1,2,3]",
        )

        // Act
        notOurs.forEach { a.sync.handle(json(it)) }
        a.sync.handle(X0xDirectMessage(sender = "agent-b", payload = null, verified = true))

        // Assert
        assertEquals(emptyList(), a.events(home))
    }

    @Test
    fun anUnverifiedMessageOrOneFromAStrangerIsNotNoted() = runBlocking {
        // Arrange
        val (a, home) = device()
        val body = Base64.Default.encode("""{"kind":"devicesync.teleport"}""".encodeToByteArray())

        // Act
        a.sync.handle(X0xDirectMessage(sender = "agent-b", payload = body, verified = false))
        a.sync.handle(X0xDirectMessage(sender = "agent-stranger", payload = body, verified = true))

        // Assert
        assertEquals(emptyList(), a.events(home))
    }

    @Test
    fun aRetryingPeerFoldsIntoOneRowAndNoPayloadContentIsStored() = runBlocking {
        // Arrange
        val (a, home) = device()
        val message = json("""{"kind":"devicesync.teleport <b>x</b>","secret":"hunter2","name":"Bob's phone"}""")

        // Act
        repeat(3) { a.sync.handle(message) }

        // Assert
        val noted = a.events(home).single()
        assertEquals(3L, noted.count)
        assertEquals("devicesync.teleport__b_x__b_", noted.detail)
        assertFalse(noted.toString().contains("hunter2") || noted.toString().contains("Bob"))
    }

    @Test
    fun aSyncMessageForADocumentTypeThisVersionLacksIsNotedOnItsHome() = runBlocking {
        // Arrange
        val (a, home) = device()
        val unknown = SyncEnvelope("$home:chores-v2", byteArrayOf(1, 2, 3)).encode()

        // Act
        a.sync.handle(X0xDirectMessage(sender = "agent-b", payload = Base64.Default.encode(unknown), verified = true))

        // Assert
        val noted = a.events(home).single()
        assertEquals(SyncEventKinds.UnsupportedVersion, noted.kind)
        assertEquals("chores-v2", noted.detail)
        assertEquals("agent-b", noted.peerAgent)
        assertEquals(SyncModules.Mailbox, noted.module)
    }

    @Test
    fun aDocumentIdOfNoHomeOfOursOrFromAStrangerIsNotNoted() = runBlocking {
        // Arrange
        val (a, home) = device()
        val ofAnotherHome = SyncEnvelope("some-other-home:chores", byteArrayOf(1)).encode()
        val ofOurs = SyncEnvelope("$home:chores", byteArrayOf(1)).encode()

        // Act
        a.sync.handle(X0xDirectMessage(sender = "agent-b", payload = Base64.Default.encode(ofAnotherHome), verified = true))
        a.sync.handle(X0xDirectMessage(sender = "agent-stranger", payload = Base64.Default.encode(ofOurs), verified = true))

        // Assert
        assertEquals(emptyList(), a.events(home))
    }

    @Test
    fun theUnsupportedVersionMakesTheVerdictSayANewerVersionIsNeededAndShowsUnderProblems() = runBlocking {
        // Arrange
        val (a, home) = device()
        val quiet = a.homes.create("Beach House", HomeShape.Family.storageValue)
        a.recorder.unsupportedVersion(home, SyncModules.Mailbox, "agent-b", "v2")
        a.recorder.unknownMessage(home, "agent-b", "devicesync.teleport")
        a.recorder.flush()

        // Act
        val snapshot = a.sync.syncActivity.snapshot(home).first()
        val quietSnapshot = a.sync.syncActivity.snapshot(quiet).first()
        val problems = a.sync.syncActivity.events(home, SyncEventFilter.Problems, null, null, 20)

        // Assert
        assertTrue(snapshot.needsNewerVersion)
        assertEquals(SyncVerdict.NeedsNewerVersion, SyncVerdict.of(snapshot.copy(daemonReachable = true, roundInProgress = false), System.currentTimeMillis()))
        assertFalse(quietSnapshot.needsNewerVersion)
        assertEquals(setOf(SyncEventKind.UnsupportedVersion, SyncEventKind.UnknownMessage), problems.map { it.kind }.toSet())
    }
}
