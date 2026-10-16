@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.kits.docs.SyncEnvelope
import uk.veritaslabs.kits.docs.SyncAcknowledgement
import uk.veritaslabs.kits.docs.SyncEventRecorder
import uk.veritaslabs.kits.docs.SyncModules
import uk.veritaslabs.kits.docs.SyncTargets
import uk.veritaslabs.kits.docs.ChangesWaiting

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
import uk.veritaslabs.kits.docs.SpaceDocumentRegistry
import uk.veritaslabs.kits.docs.SpaceDocumentType
import uk.veritaslabs.kits.docs.Scope
import uk.veritaslabs.vesta.automerge.HomeRegistryDocumentId
import uk.veritaslabs.vesta.automerge.ProfileDocumentId
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.devicesync.AgentDetails
import uk.veritaslabs.vesta.devicesync.DeviceDataSync
import uk.veritaslabs.vesta.devicesync.DeviceSyncMessage
import uk.veritaslabs.vesta.homegroup.HomeGroupMessage
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.home.HomeConfig
import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.home.RosterChangeListener
import uk.veritaslabs.kits.docs.SpaceMailboxDocuments
import uk.veritaslabs.kits.docs.MailboxReader
import uk.veritaslabs.kits.docs.MailboxWriter
import uk.veritaslabs.vesta.homegroup.HomeHub
import uk.veritaslabs.vesta.homejoin.HomeJoining
import uk.veritaslabs.vesta.profile.ProfileChangeListener
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.syncactivity.SyncActivityRepository
import uk.veritaslabs.kits.x0x.X0xClient
import uk.veritaslabs.kits.x0x.X0xDirectMessage
import uk.veritaslabs.kits.x0x.X0xEventHub

/**
 * The roster's document type. The roster is membership machinery, so the engine registers it, first (the mailbox's order),
 * and the app supplies only its own types. [publishRosters] runs after a live roster sync message has been applied and
 * projected, so this device's own member and devices are written back into the roster.
 */
internal fun rosterDocumentType(rosters: HomeRosters, publishRosters: suspend () -> Unit) = SpaceDocumentType(
    "roster", SyncModules.Members, Scope.Shared, rosters::homeDocument, rosters.changes,
    afterReceive = { homeId -> rosters.project(homeId) },
    afterSync = { publishRosters() }, resyncsHome = true,
)

/** The engine's send order for a Home's non-roster documents (the roster goes last); see [DeviceDataSyncService.documentIds]. */
private val SyncOrder = listOf("pantry", "shopping-list", "cookbook", "merchants", "mealplan", "calendar", "private-calendar", "tasks", "private-tasks")
private const val MaxBufferedPerHome = 64

/**
 * Syncs the user's data between their own paired devices (docs/adr/ADR-013-device-data-sync.md): the Home registry
 * ([HomeRegistry]), the user's Profile ([ProfileRegistry]) and each Home's Pantry and shopping-list documents, as Automerge sync messages in x0x direct messages
 * ([SyncEnvelope], ADR-006). The peers are the devices in the DeviceSync group roster ([SyncedDeviceRepository]),
 * and messages from anyone else are ignored, so group membership is the access control.
 *
 * Every device syncs with every other one, so no device needs to relay for another. Each periodic round (see
 * [syncEverything]) restarts the sync with every peer, which is how a device that was offline catches up.
 *
 * A Home's documents also go through its mailbox (docs/adr/ADR-020-syncing-without-overlapping-online-time.md): an x0x group
 * store every member and the always-on hub replicate. Each local change is deposited there, and each round, presence event
 * and foreground fetches what other devices left, so two devices that are never online together still converge. The direct
 * messages stay as they are; the mailbox is the catch-up path, and both end in idempotent Automerge merges. A Home that has
 * no open store yet, or a daemon that is down, makes the mailbox do nothing.
 */
class DeviceDataSyncService(
    private val client: X0xClient,
    private val database: KitsDatabase,
    private val devices: SyncedDeviceRepository,
    private val deviceName: () -> String,
    documentTypes: List<SpaceDocumentType>,
    private val interval: Duration = 30.seconds,
    private val peerBackoff: Duration = 2.minutes,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val recorder: SyncEventRecorder = SyncEventRecorder(database, nowMillis),
    activityRefresh: Duration = 20.seconds,
    homeConfig: HomeConfig = HomeConfig(),
    seeding: HomeSeeding,
) : DeviceDataSync {

    private val registry = HomeRegistry(database, seeding)
    private val profiles = ProfileRepository(database, deviceName = deviceName)
    private val profile = ProfileRegistry(database, profiles)
    private val rosters = HomeRosters(database, profiles, deviceName = deviceName)

    /** The Home registry, which also forgets a Home's Sync activity (ADR-033) when the Home is deleted or left on this device. */
    private val homeListener = object : HomeChangeListener by registry {
        override suspend fun deleted(id: String) {
            registry.deleted(id)
            recorder.forgetHome(id)
            changesWaiting.forget(id)
        }
    }
    private val join = HomeJoinService(client, database, rosters, homeListener, profiles, seeding, recorder = recorder, config = homeConfig)
    private val groups = HomeGroups(client, database, rosters, profiles, config = homeConfig)

    /**
     * The one list of Home document types: the roster, which the engine registers itself, then the [documentTypes] the app
     * gave; the acknowledgement, activity module and private-id lookups go through it.
     */
    private val documentRegistry = SpaceDocumentRegistry(listOf(rosterDocumentType(rosters) { publishRosters() }) + documentTypes)
    private val mailboxDocuments = SpaceMailboxDocuments(documentRegistry)
    private val mailboxWriter = MailboxWriter(client, database, mailboxDocuments) { groups.mailboxStoreId(it) }
    private val mailboxReader = MailboxReader(client, database, mailboxDocuments, recorder) { groups.mailboxStoreId(it) }

    private val acknowledgement = SyncAcknowledgement(documentRegistry)

    private val activityFacts = SyncActivityFacts()

    /** Whether each Home has changes no other device has acknowledged (ADR-033); see [ChangesWaiting] for when it is worked out. */
    private val changesWaiting = ChangesWaiting(
        database,
        sharedDocumentIds = { mailboxDocuments.documentIds(it) },
        privateDocumentIds = { documentRegistry.documentIds(it, Scope.Private) },
        targets = { homeId ->
            val self = self()
            val paired = devices.agentIds() - setOfNotNull(self)
            val members = rosters.devices(homeId).values.flatten()
            SyncTargets(shared = (paired + members - setOfNotNull(self)).isNotEmpty() || rosters.hubs(homeId).isNotEmpty(), own = paired.isNotEmpty())
        },
        heads = { homeId, documentId -> headsOf(homeId, documentId) },
    )

    /**
     * Peers a direct message recently failed to reach, by agent id and the time until which they are left alone. x0xd waits about
     * 24 s before it gives up on a peer that is not there, and every document was sent in turn, so one absent device held the sync
     * (and, behind it, the mailbox) for minutes. A peer is tried once, then not again until it has been quiet for [peerBackoff], it
     * is seen coming online ([syncWith]) or it sends something ([handle]).
     */
    private val peerDownUntil = ConcurrentHashMap<String, Long>()

    /** When a sync message last passed between this device and each peer, in [nowMillis] time. */
    private val lastSynced = ConcurrentHashMap<String, Long>()

    override val syncActivity: SyncActivityRepository = SyncActivityService(
        database, recorder, rosters, profiles, devices, activityFacts, changesWaiting, lastSynced, peerDownUntil,
        selfAgent = { self() }, requestSync = { syncNow() }, nowMillis = nowMillis, refreshInterval = activityRefresh,
    )

    override suspend fun agentDetails(homeId: String, agentId: String): AgentDetails {
        val discovered = runCatching { client.getDiscoveredAgent(agentId) }.getOrNull()
        val now = nowMillis()
        return AgentDetails(
            name = rosters.deviceNames(homeId)[agentId],
            machineId = discovered?.machineId,
            lastSeenMsAgo = discovered?.lastSeen?.let { (now - it * 1_000).coerceAtLeast(0) },
            lastSyncedMsAgo = (lastSynced[agentId] ?: recorder.lastSyncedAt(agentId))?.let { (now - it).coerceAtLeast(0) },
        )
    }

    override val homeChanges: HomeChangeListener get() = homeListener

    override val joining: HomeJoining get() = join

    override val hubs: HomeHub = HomeHubs(client, database, rosters, profiles, config = homeConfig)

    override suspend fun memberAgents(homeId: String, memberId: String): List<String> =
        rosters.devices(homeId)[memberId].orEmpty().sorted()

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
        launch { profile.changes.collect { attempt { syncDocument(ProfileDocumentId) } } }
        documentRegistry.types.forEach { type ->
            launch {
                type.localChanges.collect { homeId ->
                    val documentId = type.documentId(homeId)
                    // A private document goes to the user's own devices only: no mailbox, and no Home members to add.
                    if (type.scope == Scope.Shared) launch { attempt { mailboxWriter.deposit(homeId, documentId) } } // not behind sends to absent peers
                    attempt { syncDocument(documentId) }
                    // A roster change can add a device to a Home's peers, which then needs everything the Home has.
                    if (type.resyncsHome) {
                        documentRegistry.documentIds(homeId, Scope.Shared).filter { it != documentId }.sortedBy { syncOrderOf(it) }
                            .forEach { attempt { syncDocument(it) } }
                    }
                }
            }
        }
        // A newly paired device, or one that has gone, changes who there is to sync with.
        launch { devices.devices.collect { attempt { syncEverything() } } }
        // A peer coming online is the moment a message to it can get through (ADR-020): sync with it now rather than
        // at the next round. The timer stays as the fallback, as presence events can be late or missed.
        launch {
            X0xEventHub(client).presence.collect { event ->
                activityFacts.online[event.agentId] = event.isOnline
                activityFacts.notifyChanged()
                if (event.isOnline) {
                    launch { attempt { syncMailboxes() } }
                    attempt { syncWith(event.agentId) }
                }
            }
        }
        launch { syncRequests.consumeEach { attempt { round() } } }
        while (true) {
            attempt { round() }
            delay(interval)
        }
    }

    /**
     * One full round: the direct-message sync, and with it the mailboxes. The mailboxes start at once, not after the direct-message
     * sync, which can spend a long time on peers that do not answer; they run again when it is done, for stores it just opened.
     */
    private suspend fun round() {
        activityFacts.roundStarted(daemonReachable = runCatching { client.agent() }.isSuccess)
        try {
            coroutineScope {
                launch { attempt { syncMailboxes() } }
                attempt { syncEverything() }
                attempt { syncMailboxes() }
                attempt {
                    recorder.prune(nowMillis())
                    recorder.flush()
                }
                // Now, with the sync states whole again, not while syncEverything was clearing and refilling them.
                attempt { refreshChangesWaiting() }
            }
        } finally {
            activityFacts.roundEnded(nowMillis())
        }
    }

    /**
     * Deposits what this device changed and fetches what others left, in every Home's mailbox. It runs outside [lock]: store
     * calls can be slow, and the documents and the mailbox keep their own locks. A Home whose roster, Pantry or list changed
     * through a deposit is published and synced again, as one that arrived by direct message is.
     */
    private suspend fun syncMailboxes() {
        var applied = 0
        database.homeQueries.selectAll().awaitAsList().forEach { home ->
            attempt { if (mailboxWriter.deposit(home.id) > 0) recorder.mailbox(home.id, incoming = false, peer = null) }
            attempt { mailboxWriter.compact(home.id) }
            attempt {
                val fetched = mailboxReader.fetch(home.id)
                applied += fetched
                if (fetched > 0) recorder.mailbox(home.id, incoming = true, peer = null)
            }
        }
        if (applied > 0) attempt { publishRosters() }
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
        if (message.verified == true) message.sender?.let { peerDownUntil.remove(it) } // it is evidently there
        if (join.handle(message)) return // a Home-join message, not a sync message
        if (groups.handle(message)) return // a Home-group message (ADR-021)
        attempt { recordUnknownMessage(message) } // noted in Sync activity (ADR-034), then handled as before: dropped
        syncFrom(message)
    }

    /**
     * Notes a verified peer's message of a family this version knows (device-sync, Home-group) but with a `kind` it doesn't, which a newer
     * Vesta may send. Anything else, such as an agent command on the same stream, is not a message of those families and is not noted.
     * It goes on the Home the message names when it names one, otherwise on every Home the sender is a peer of.
     */
    private suspend fun recordUnknownMessage(message: X0xDirectMessage) {
        if (message.verified != true) return
        val sender = message.sender ?: return
        val payload = message.payload
        val (kind, homeId) = DeviceSyncMessage.unrecognisedKind(payload)?.let { it to null }
            ?: HomeGroupMessage.unrecognisedKind(payload)?.let { it to HomeGroupMessage.stringField(payload, "homeId") }
            ?: return
        val homes = homeId?.let { listOfNotNull(database.homeQueries.selectById(it).awaitAsOneOrNull()?.id) }
            ?: database.homeQueries.selectAll().awaitAsList().map { it.id }
        homes.filter { sender in homePeers(it) }.forEach { recorder.unknownMessage(it, sender, kind) }
    }

    /** Notes a document id this version doesn't know, from a peer of the Home it names (`<home id>:<document type>`), as needing a newer Vesta. */
    private suspend fun recordUnknownDocument(sender: String, documentId: String) {
        if (documentId == HomeRegistryDocumentId || documentId == ProfileDocumentId) return
        if (documentRegistry.parse(documentId) != null) return
        val homeId = documentId.substringBeforeLast(':', "").takeIf { it.isNotEmpty() } ?: return
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) return
        if (sender !in homePeers(homeId)) return
        recorder.unsupportedVersion(homeId, SyncModules.Mailbox, sender, documentId.substringAfterLast(':'))
    }

    private suspend fun syncFrom(message: X0xDirectMessage) {
        // What the peers have acknowledged is read inside the lock, where the sync states are consistent; it needs no I/O.
        val acknowledged = lock.withLock {
            if (message.verified != true) return@withLock null
            val sender = message.sender ?: return@withLock null
            val bytes = message.payload?.let { runCatching { Base64.Default.decode(it) }.getOrNull() } ?: return@withLock null
            val envelope = SyncEnvelope.decode(bytes) ?: return@withLock null
            attempt { recordUnknownDocument(sender, envelope.documentId) } // noted (ADR-034); handled as before
            if (sender !in peersFor(envelope.documentId)) return@withLock null
            receive(sender, envelope.documentId, envelope.message)
            noteSynced(sender)
            activityFacts.online[sender] = true
            activityHomeId(envelope.documentId)?.let { it to acknowledgedDocuments(it) }
        }
        acknowledged?.let { (homeId, documents) -> attempt { changesWaiting.update(homeId, documents) } }
        activityFacts.notifyChanged()
    }

    /** The ids of [homeId]'s documents that some peer's sync state reports in sync. Call it holding [lock]; it reads memory only. */
    private suspend fun acknowledgedDocuments(homeId: String): Set<String> {
        val ids = mailboxDocuments.documentIds(homeId) + documentRegistry.documentIds(homeId, Scope.Private)
        return ids.filterTo(mutableSetOf()) { id ->
            states.any { (key, state) -> key.first == id && acknowledgement.isInSync(homeId, id, state) }
        }
    }

    /** Works out [ChangesWaiting] for every Home. Call it holding no lock: the sync states are read under it, the rest is not. */
    private suspend fun refreshChangesWaiting() {
        val homes = database.homeQueries.selectAll().awaitAsList().map { it.id }
        val acknowledged = lock.withLock { homes.associateWith { acknowledgedDocuments(it) } }
        acknowledged.forEach { (homeId, documents) -> attempt { changesWaiting.update(homeId, documents) } }
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
        val homeId = homeIdOf(documentId) ?: return paired // a private type or a non-Home document: the user's own devices only
        return homePeers(homeId)
    }

    /** The devices a Home's shared documents sync with: the user's paired devices and the Home's members' devices. */
    private suspend fun homePeers(homeId: String): Set<String> =
        (devices.agentIds() + rosters.devices(homeId).values.flatten()) - setOfNotNull(self())

    /** The same devices [homeId]'s shared documents sync with, so a photo reaches whoever can see the recipe that shows it. */
    override suspend fun blobPeers(homeId: String): Set<String> = homePeers(homeId)

    /** The Home a shared document belongs to; null for a private type's document and for anything the registry doesn't know. */
    private fun homeIdOf(documentId: String): String? =
        documentRegistry.parse(documentId)?.takeIf { it.type.scope == Scope.Shared }?.spaceId

    /**
     * Syncs every document with its peers ([peersFor]), starting each peer's sync over. Automerge's sync state assumes
     * what it sent arrived, and a direct message to an offline device may vanish without an error, so a fresh start
     * each round is what lets a device that was away catch up. It costs a small handshake per document and peer.
     */
    suspend fun syncEverything() = lock.withLock {
        attempt { join.resendPending() }
        attempt { publishRosters() }
        attempt { groups.reconcile() }
        states.clear()
        connected.clear()
        documentIds().forEach { id -> peersFor(id).forEach { peer -> pump(id, peer) } }
    }

    /** Starts the sync over with [peer] alone, and resends anything queued, if it is a peer of any document; otherwise does nothing. */
    suspend fun syncWith(peer: String) = lock.withLock {
        peerDownUntil.remove(peer)
        val shared = documentIds().filter { peer in peersFor(it) }
        if (shared.isEmpty()) return@withLock
        attempt { join.resendPending() }
        states.keys.removeAll { it.second == peer }
        connected.remove(peer)
        shared.forEach { pump(it, peer) }
        recorder.flush()
        activityFacts.notifyChanged()
    }

    private val syncRequests = Channel<Unit>(Channel.CONFLATED)

    override suspend fun deviceUnpaired(agentId: String) = rosters.markGone(agentId)

    override suspend fun devicePaired(agentId: String) = rosters.clearGone(agentId)

    override fun syncNow() {
        syncRequests.trySend(Unit)
    }

    private suspend fun syncDocument(documentId: String) {
        // A local change just made the document newer than anything a peer acknowledged. This does not clear the states, so it is safe to read them.
        val acknowledged = lock.withLock {
            peersFor(documentId).forEach { pump(documentId, it) }
            activityHomeId(documentId)?.let { it to acknowledgedDocuments(it) }
        }
        acknowledged?.let { (homeId, documents) -> attempt { changesWaiting.update(homeId, documents) } }
        activityFacts.notifyChanged()
    }

    private suspend fun documentIds(): List<String> =
        listOf(HomeRegistryDocumentId, ProfileDocumentId) + database.homeQueries.selectAll().awaitAsList()
            .flatMap { home -> documentRegistry.documentIds(home.id).sortedBy { syncOrderOf(it) } }

    /**
     * The order a Home's documents are sent in, with the roster last. It is the engine's own and is deliberately not the registry's
     * (roster first, private types last) nor the mailbox's; a type not listed here goes after the listed ones, in registry order.
     */
    private fun syncOrderOf(documentId: String): Int = when (val feature = documentRegistry.parse(documentId)?.type?.feature) {
        "roster" -> Int.MAX_VALUE
        else -> SyncOrder.indexOf(feature).takeIf { it >= 0 } ?: SyncOrder.size
    }

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
        } else {
            val (homeId, type) = documentRegistry.parse(documentId) ?: return // a document this version doesn't know about
            if (type.scope == Scope.Private) {
                // Not buffered when this device lacks the Home, and not applied through applyHomeDocument: a later round recovers it.
                recordingReceived(sender, documentId) { type.open(homeId)?.receiveSync(stateFor(documentId, sender), message) }
            } else {
                if (!receiveHomeDocument(sender, documentId, message)) {
                    waiting.getOrPut(homeId) { mutableListOf() }.let { if (it.size < MaxBufferedPerHome) it += Triple(sender, documentId, message) }
                    return
                }
                type.afterSync(homeId)
            }
        }
        // Answer the sender, and tell the other peers what changed.
        peersFor(documentId).forEach { pump(documentId, it) }
    }

    /** Applies a message for one of a Home's shared documents; false when this device has no such Home yet. */
    private suspend fun receiveHomeDocument(sender: String, documentId: String, message: ByteArray): Boolean =
        recordingReceived(sender, documentId) { applyHomeDocument(sender, documentId, message) }

    private suspend fun applyHomeDocument(sender: String, documentId: String, message: ByteArray): Boolean {
        val (homeId, type) = documentRegistry.parse(documentId) ?: return false
        val document = type.open(homeId) ?: return false // no such Home yet
        document.receiveSync(stateFor(documentId, sender), message)
        type.afterReceive(homeId)
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
        if ((peerDownUntil[peer] ?: 0L) > nowMillis()) return
        val state = stateFor(documentId, peer)
        try {
            if (connected.add(peer)) attempt { client.connectAgent(peer) }
            while (true) {
                val message = when (documentId) {
                    HomeRegistryDocumentId -> registry.generate(state)
                    ProfileDocumentId -> profile.generate(state)
                    else -> documentRegistry.parse(documentId)?.let { (homeId, type) -> type.open(homeId)?.generateSync(state) }
                } ?: break
                client.sendDirect(peer, SyncEnvelope(documentId, message).encode())
                noteSynced(peer)
                noteSent(documentId, peer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            states.keys.removeAll { it.second == peer }
            connected.remove(peer)
            peerDownUntil[peer] = nowMillis() + peerBackoff.inWholeMilliseconds
            activityFacts.online[peer] = false
            noteFailed(documentId, peer)
        }
    }

    /** Notes that a sync message just passed between this device and [peer]. */
    private suspend fun noteSynced(peer: String) {
        lastSynced[peer] = nowMillis()
        recorder.lastSynced(peer)
    }

    // The Sync activity log (ADR-033) records Home documents only; the registry and Profile belong to no Home. Recording never
    // changes a sync result: the recorder swallows its own failures, and reading heads is guarded here the same way.

    private fun activityHomeId(documentId: String): String? = documentRegistry.parse(documentId)?.spaceId

    private suspend fun noteSent(documentId: String, peer: String) {
        val homeId = activityHomeId(documentId) ?: return
        documentRegistry.moduleOf(documentId)?.let { recorder.sent(homeId, it, peer) }
    }

    private suspend fun noteFailed(documentId: String, peer: String) {
        val homeId = activityHomeId(documentId)
        activityFacts.sendFailed(homeId)
        if (homeId == null) return
        documentRegistry.moduleOf(documentId)?.let { recorder.failed(homeId, it, peer, "unreachable") }
    }

    /** The heads of a Home document, or null for anything else, a Home this device doesn't have yet, or a failed read. */
    private suspend fun headsOf(homeId: String, documentId: String): ByteArray? = try {
        documentRegistry.open(homeId, documentId)?.heads()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * Runs [apply], which applies a sync message from [sender] to [documentId], and records that [sender] sent changes if the
     * document's heads moved. A message that changes nothing is just a handshake and is not recorded.
     */
    private suspend fun <T> recordingReceived(sender: String, documentId: String, apply: suspend () -> T): T {
        val homeId = activityHomeId(documentId) ?: return apply()
        val before = headsOf(homeId, documentId)
        val result = apply()
        headsOf(homeId, documentId)?.takeUnless { it.contentEquals(before) }
            ?.let { documentRegistry.moduleOf(documentId)?.let { module -> recorder.received(homeId, module, sender) } }
        return result
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
