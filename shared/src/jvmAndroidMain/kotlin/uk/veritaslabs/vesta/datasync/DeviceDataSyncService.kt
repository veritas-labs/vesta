package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
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
import uk.veritaslabs.vesta.automerge.ShoppingListSources
import uk.veritaslabs.vesta.automerge.shoppingListDocumentId
import uk.veritaslabs.vesta.automerge.pantryDocumentId
import uk.veritaslabs.vesta.automerge.rosterDocumentId
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.devicesync.DeviceDataSync
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.home.RosterChangeListener
import uk.veritaslabs.vesta.homejoin.HomeJoining
import uk.veritaslabs.vesta.profile.ProfileChangeListener
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage
import uk.veritaslabs.vesta.x0x.X0xEventHub

private const val PantrySuffix = ":pantry"
private const val RosterSuffix = ":roster"
private const val ShoppingListSuffix = ":shopping-list"
private const val MaxBufferedPerHome = 64

/**
 * Syncs the user's data between their own paired devices (docs/adr/ADR-013-device-data-sync.md): the Home registry
 * ([HomeRegistry]), the user's Profile ([ProfileRegistry]) and each Home's Pantry and shopping-list documents, as Automerge sync messages in x0x direct messages
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
    private val shoppingSources: ShoppingListSources = ShoppingListSources(database, pantrySources),
    private val interval: Duration = 30.seconds,
) : DeviceDataSync {

    private val registry = HomeRegistry(database, pantrySources)
    private val profiles = ProfileRepository(database)
    private val profile = ProfileRegistry(database, profiles)
    private val rosters = HomeRosters(database, profiles)
    private val join = HomeJoinService(client, database, rosters, registry, profiles)

    override val homeChanges: HomeChangeListener get() = registry

    override val joining: HomeJoining get() = join

    override val profileChanges: ProfileChangeListener = object : ProfileChangeListener {
        override suspend fun changed() {
            profile.publish()
            publishRosters() // the new name goes into each Home's roster too
        }
    }

    override val rosterChanges: RosterChangeListener = object : RosterChangeListener {
        override suspend fun roleChanged(homeId: String, memberId: String, role: String) = rosters.setRole(homeId, memberId, role)
        override suspend fun memberRemoved(homeId: String, memberId: String) = join.removeMember(homeId, memberId)
        override suspend fun memberLeft(homeId: String, memberId: String) = join.announceLeft(homeId, memberId)
    }

    // One sync step at a time: every step reads and writes the per-peer sync states.
    private val lock = Mutex()
    private val states = mutableMapOf<Pair<String, String>, SyncState>()

    // Pantry and roster messages that arrived before their Home did (the registry and a Home's documents sync side by
    // side): the sending peer, the document id and the sync message.
    private val waiting = mutableMapOf<String, MutableList<Triple<String, String, ByteArray>>>()

    override suspend fun run() = coroutineScope {
        registry.importLocalHomes()
        attempt { profile.publish() }
        attempt { publishRosters() }
        launch { X0xEventHub(client).directMessages.collect { message -> launch { attempt { handle(message) } } } }
        launch { registry.changes.collect { attempt { syncDocument(HomeRegistryDocumentId) } } }
        launch { registry.changes.collect { attempt { publishRosters() } } }
        launch {
            // A roster change can add a device to a Home's peers, which then needs everything the Home has.
            rosters.changes.collect { homeId ->
                attempt { syncDocument(rosterDocumentId(homeId)) }
                attempt { syncDocument(pantryDocumentId(homeId)) }
                attempt { syncDocument(shoppingListDocumentId(homeId)) }
            }
        }
        launch { profile.changes.collect { attempt { syncDocument(ProfileDocumentId) } } }
        launch { pantrySources.localChanges.collect { homeId -> attempt { syncDocument(pantryDocumentId(homeId)) } } }
        launch { shoppingSources.localChanges.collect { homeId -> attempt { syncDocument(shoppingListDocumentId(homeId)) } } }
        // A newly paired device, or one that has gone, changes who there is to sync with.
        launch { devices.devices.collect { attempt { syncEverything() } } }
        // A peer coming online is the moment a message to it can get through (ADR-020): sync with it now rather than
        // at the next round. The timer stays as the fallback, as presence events can be late or missed.
        launch { X0xEventHub(client).presence.collect { event -> if (event.isOnline) attempt { syncWith(event.agentId) } } }
        launch { syncRequests.consumeEach { attempt { syncEverything() } } }
        while (true) {
            attempt { syncEverything() }
            delay(interval)
        }
    }

    /** The agents that act for each member of [homeId], by member id, as its roster says. */
    internal suspend fun rosterDevices(homeId: String): Map<String, Set<String>> = rosters.devices(homeId)

    /**
     * Admits a person to [homeId]'s roster as [role], with the agent that acts for them (the invite flow's job,
     * ADR-014), and makes them a peer of that Home. Also how a joining device learns who invited it, since a device
     * only syncs a Home with the agents its roster lists.
     */
    internal suspend fun admit(homeId: String, memberId: String, name: String, role: String, agentId: String) =
        lock.withLock { rosters.admit(homeId, memberId, name, role, agentId) }

    /** Marks [memberId] removed from [homeId] and stops syncing it with their devices. */
    internal suspend fun removeMember(homeId: String, memberId: String) = lock.withLock { rosters.remove(homeId, memberId) }

    /**
     * Handles one direct message: a sync message is applied and answered if its sender is a peer for that document
     * ([peersFor]); anything else is ignored.
     */
    suspend fun handle(message: X0xDirectMessage) {
        if (join.handle(message)) return // a Home-join message, not a sync message
        syncFrom(message)
    }

    private suspend fun syncFrom(message: X0xDirectMessage) = lock.withLock {
        if (message.verified != true) return@withLock
        val sender = message.sender ?: return@withLock
        val bytes = message.payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() } ?: return@withLock
        val envelope = SyncEnvelope.decode(bytes) ?: return@withLock
        if (sender !in peersFor(envelope.documentId)) return@withLock
        receive(sender, envelope.documentId, envelope.message)
    }

    private var selfAgent: String? = null

    private suspend fun self(): String? = selfAgent ?: runCatching { client.agent().agentId }.getOrNull()?.also { selfAgent = it }

    /**
     * Who [documentId] is synced with. The user's own paired devices share everything; a Home's own documents (its
     * Pantry, shopping list and roster) are also shared with every device its roster lists for a member who hasn't been removed
     * (ADR-014), so the members of a Home sync it between them. The Home registry and Profile are only ever the user's.
     */
    private suspend fun peersFor(documentId: String): Set<String> {
        val paired = devices.agentIds()
        val homeId = homeIdOf(documentId) ?: return paired
        val members = rosters.devices(homeId).values.flatten()
        return (paired + members) - setOfNotNull(self())
    }

    private fun homeIdOf(documentId: String): String? = when {
        documentId.endsWith(PantrySuffix) -> documentId.removeSuffix(PantrySuffix)
        documentId.endsWith(RosterSuffix) -> documentId.removeSuffix(RosterSuffix)
        documentId.endsWith(ShoppingListSuffix) -> documentId.removeSuffix(ShoppingListSuffix)
        else -> null
    }

    /**
     * Syncs every document with its peers ([peersFor]), starting each peer's sync over. Automerge's sync state assumes
     * what it sent arrived, and a direct message to an offline device may vanish without an error, so a fresh start
     * each round is what lets a device that was away catch up. It costs a small handshake per document and peer.
     */
    suspend fun syncEverything() = lock.withLock {
        attempt { join.resendPending() }
        attempt { publishRosters() }
        states.clear()
        connected.clear()
        documentIds().forEach { id -> peersFor(id).forEach { peer -> pump(id, peer) } }
    }

    /** Starts the sync over with [peer] alone, and resends anything queued, if it is a peer of any document; otherwise does nothing. */
    suspend fun syncWith(peer: String) = lock.withLock {
        val shared = documentIds().filter { peer in peersFor(it) }
        if (shared.isEmpty()) return@withLock
        attempt { join.resendPending() }
        states.keys.removeAll { it.second == peer }
        connected.remove(peer)
        shared.forEach { pump(it, peer) }
    }

    private val syncRequests = Channel<Unit>(Channel.CONFLATED)

    override fun syncNow() {
        syncRequests.trySend(Unit)
    }

    private suspend fun syncDocument(documentId: String) = lock.withLock {
        peersFor(documentId).forEach { pump(documentId, it) }
    }

    private suspend fun documentIds(): List<String> =
        listOf(HomeRegistryDocumentId, ProfileDocumentId) + database.homeQueries.selectAll().awaitAsList()
            .flatMap { listOf(pantryDocumentId(it.id), shoppingListDocumentId(it.id), rosterDocumentId(it.id)) }

    private suspend fun receive(sender: String, documentId: String, message: ByteArray) {
        if (documentId == HomeRegistryDocumentId) {
            registry.receive(stateFor(documentId, sender), message)
            val created = registry.project()
            // Homes can now exist that a Pantry message was waiting for.
            created.forEach { homeId ->
                waiting.remove(homeId)?.forEach { (peer, documentId, queued) -> receiveHomeDocument(peer, documentId, queued) }
            }
            publishRosters() // a Home that has arrived may now have a member row for this user to publish

            val existing = database.homeQueries.selectAll().awaitAsList().mapTo(mutableSetOf()) { it.id }
            waiting.keys.retainAll(existing)
        } else if (documentId == ProfileDocumentId) {
            profile.receive(stateFor(documentId, sender), message)
            // A device that took on the shared Profile retires its old member id, then publishes the new one.
            profile.project()?.let { rosters.retire(it) }
            publishRosters()
        } else if (homeIdOf(documentId) != null) {
            val homeId = homeIdOf(documentId)!!
            if (!receiveHomeDocument(sender, documentId, message)) {
                waiting.getOrPut(homeId) { mutableListOf() }.let { if (it.size < MaxBufferedPerHome) it += Triple(sender, documentId, message) }
                return
            }
            if (documentId.endsWith(RosterSuffix)) publishRosters()
        } else {
            return // a document this version doesn't know about
        }
        // Answer the sender, and tell the other peers what changed.
        peersFor(documentId).forEach { pump(documentId, it) }
    }

    /** Applies a Pantry, shopping-list or roster message; false when this device has no such Home yet. */
    private suspend fun receiveHomeDocument(sender: String, documentId: String, message: ByteArray): Boolean {
        if (documentId.endsWith(RosterSuffix)) {
            val homeId = documentId.removeSuffix(RosterSuffix)
            return rosters.receive(homeId, stateFor(documentId, sender), message).also { if (it) rosters.project(homeId) }
        }
        if (documentId.endsWith(ShoppingListSuffix)) {
            val source = shoppingSources.forHome(documentId.removeSuffix(ShoppingListSuffix)) ?: return false
            source.receiveSync(stateFor(documentId, sender), message)
            return true
        }
        val source = pantrySources.forHome(documentId.removeSuffix(PantrySuffix)) ?: return false
        source.receiveSync(stateFor(documentId, sender), message)
        return true
    }

    /** Writes this user's member and this device's agent into every Home's roster, and drops devices that are no longer paired; nothing is written about devices while x0x can't say what this one's agent is. */
    private suspend fun publishRosters() {
        val agentId = runCatching { client.agent().agentId }.getOrNull()
        val paired = devices.agentIds()
        database.homeQueries.selectAll().awaitAsList().forEach { rosters.publish(it.id, agentId, paired) }
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
                    else -> if (documentId.endsWith(RosterSuffix)) {
                        rosters.generate(documentId.removeSuffix(RosterSuffix), state)
                    } else if (documentId.endsWith(ShoppingListSuffix)) {
                        shoppingSources.forHome(documentId.removeSuffix(ShoppingListSuffix))?.generateSync(state)
                    } else pantrySources.forHome(documentId.removeSuffix(PantrySuffix))?.generateSync(state)
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
