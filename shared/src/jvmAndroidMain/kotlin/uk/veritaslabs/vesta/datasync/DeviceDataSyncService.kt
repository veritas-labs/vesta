package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.SyncState
import uk.veritaslabs.vesta.automerge.HomeRegistryDocumentId
import uk.veritaslabs.vesta.automerge.PantrySources
import uk.veritaslabs.vesta.automerge.ProfileDocumentId
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.devicesync.DeviceDataSync
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage
import uk.veritaslabs.vesta.x0x.X0xEventHub

private const val PantrySuffix = ":pantry"
private const val MaxBufferedPerHome = 64

/**
 * Syncs the user's data between their own paired devices (docs/adr/ADR-013-device-data-sync.md): the Home registry
 * ([HomeRegistry]), the user's Profile ([ProfileRegistry]) and each Home's Pantry document, as Automerge sync messages in x0x direct messages
 * ([SyncEnvelope], ADR-006). The peers are the devices in the DeviceSync group roster ([SyncedDeviceRepository]),
 * and messages from anyone else are ignored, so group membership is the access control.
 *
 * Every device syncs with every other one, so no device needs to relay for another. Each periodic round (see
 * [syncEverything]) restarts the sync with every peer, which is how a device that was offline catches up.
 */
class DeviceDataSyncService(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val devices: SyncedDeviceRepository,
    private val pantrySources: PantrySources,
    private val interval: Duration = 30.seconds,
) : DeviceDataSync {

    private val registry = HomeRegistry(database, pantrySources)
    private val profile = ProfileRegistry(database, ProfileRepository(database))

    override val homeChanges: HomeChangeListener get() = registry

    // One sync step at a time: every step reads and writes the per-peer sync states.
    private val lock = Mutex()
    private val states = mutableMapOf<Pair<String, String>, SyncState>()

    // Pantry messages that arrived before their Home did (the registry and the Pantry sync side by side).
    private val waiting = mutableMapOf<String, MutableList<Pair<String, ByteArray>>>()

    override suspend fun run() = coroutineScope {
        registry.importLocalHomes()
        attempt { profile.publish() }
        launch { X0xEventHub(client).directMessages.collect { message -> launch { attempt { handle(message) } } } }
        launch { registry.changes.collect { attempt { syncDocument(HomeRegistryDocumentId) } } }
        launch { profile.changes.collect { attempt { syncDocument(ProfileDocumentId) } } }
        launch { pantrySources.localChanges.collect { homeId -> attempt { syncDocument(pantryDocumentId(homeId)) } } }
        // A newly paired device, or one that has gone, changes who there is to sync with.
        launch { devices.devices.collect { attempt { syncEverything() } } }
        while (true) {
            attempt { syncEverything() }
            delay(interval)
        }
    }

    /** Handles one direct message: a sync message from a paired device is applied and answered; anything else is ignored. */
    suspend fun handle(message: X0xDirectMessage) = lock.withLock {
        if (message.verified != true) return@withLock
        val sender = message.sender ?: return@withLock
        if (sender !in devices.agentIds()) return@withLock
        val bytes = message.payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() } ?: return@withLock
        val envelope = SyncEnvelope.decode(bytes) ?: return@withLock
        receive(sender, envelope.documentId, envelope.message)
    }

    /**
     * Syncs every document with every paired device, starting each peer's sync over. Automerge's sync state assumes
     * what it sent arrived, and a direct message to an offline device may vanish without an error, so a fresh start
     * each round is what lets a device that was away catch up. It costs a small handshake per document and peer.
     */
    suspend fun syncEverything() = lock.withLock {
        val peers = devices.agentIds()
        states.clear()
        connected.clear()
        documentIds().forEach { id -> peers.forEach { peer -> pump(id, peer) } }
    }

    private suspend fun syncDocument(documentId: String) = lock.withLock {
        devices.agentIds().forEach { pump(documentId, it) }
    }

    private suspend fun documentIds(): List<String> =
        listOf(HomeRegistryDocumentId, ProfileDocumentId) + database.homeQueries.selectAll().awaitAsList().map { pantryDocumentId(it.id) }

    private suspend fun receive(sender: String, documentId: String, message: ByteArray) {
        if (documentId == HomeRegistryDocumentId) {
            registry.receive(stateFor(documentId, sender), message)
            val created = registry.project()
            // Homes can now exist that a Pantry message was waiting for.
            created.forEach { homeId ->
                waiting.remove(homeId)?.forEach { (peer, queued) -> receivePantry(peer, homeId, queued) }
            }
            val existing = database.homeQueries.selectAll().awaitAsList().mapTo(mutableSetOf()) { it.id }
            waiting.keys.retainAll(existing)
        } else if (documentId == ProfileDocumentId) {
            profile.receive(stateFor(documentId, sender), message)
            profile.project()
        } else if (documentId.endsWith(PantrySuffix)) {
            val homeId = documentId.removeSuffix(PantrySuffix)
            if (!receivePantry(sender, homeId, message)) {
                waiting.getOrPut(homeId) { mutableListOf() }.let { if (it.size < MaxBufferedPerHome) it += sender to message }
                return
            }
        } else {
            return // a document this version doesn't know about
        }
        // Answer the sender, and tell the other devices what changed.
        devices.agentIds().forEach { pump(documentId, it) }
    }

    /** Applies a Pantry message; false when this device has no such Home yet. */
    private suspend fun receivePantry(sender: String, homeId: String, message: ByteArray): Boolean {
        val source = pantrySources.forHome(homeId) ?: return false
        source.receiveSync(stateFor(pantryDocumentId(homeId), sender), message)
        return true
    }

    private val connected = mutableSetOf<String>()

    /** Sends [peer] whatever it is missing of [documentId]. A failed send drops that peer's sync state, so the next round starts afresh. */
    private suspend fun pump(documentId: String, peer: String) {
        val state = stateFor(documentId, peer)
        try {
            if (connected.add(peer)) attempt { client.connectAgent(peer) }
            while (true) {
                val message = when (documentId) {
                    HomeRegistryDocumentId -> registry.generate(state)
                    ProfileDocumentId -> profile.generate(state)
                    else -> pantrySources.forHome(documentId.removeSuffix(PantrySuffix))?.generateSync(state)
                } ?: break
                client.sendDirect(peer, SyncEnvelope(documentId, message).encode())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            states.keys.removeAll { it.second == peer }
            connected.remove(peer)
        }
    }

    private fun stateFor(documentId: String, peer: String) = states.getOrPut(documentId to peer) { SyncState() }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sync is retried every round; one failure shouldn't stop it.
        }
    }
}
