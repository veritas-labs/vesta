@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.kits.sync

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
import uk.veritaslabs.kits.spaces.SpaceRegistryDocumentId
import uk.veritaslabs.kits.spaces.ProfileDocumentId
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.spaces.SpaceGroupMessage
import uk.veritaslabs.kits.spaces.SpacesConfig
import uk.veritaslabs.kits.spaces.SpaceChangeListener
import uk.veritaslabs.kits.spaces.RosterChangeListener
import uk.veritaslabs.kits.docs.SpaceMailboxDocuments
import uk.veritaslabs.kits.docs.MailboxReader
import uk.veritaslabs.kits.docs.MailboxWriter
import uk.veritaslabs.kits.spaces.SpaceHub
import uk.veritaslabs.kits.spaces.SpaceJoining
import uk.veritaslabs.kits.spaces.ProfileChangeListener
import uk.veritaslabs.kits.spaces.ProfileRepository
import uk.veritaslabs.kits.x0x.X0xClient
import uk.veritaslabs.kits.x0x.X0xDirectMessage
import uk.veritaslabs.kits.x0x.X0xEventHub

import uk.veritaslabs.kits.spaces.ProfileRegistry
import uk.veritaslabs.kits.spaces.SpaceRegistry
import uk.veritaslabs.kits.spaces.SpaceSeeding
import uk.veritaslabs.kits.spaces.SpaceGroups
import uk.veritaslabs.kits.spaces.SpaceHubs
import uk.veritaslabs.kits.spaces.SpaceJoinService
import uk.veritaslabs.kits.spaces.SpaceRosters
/**
 * The roster's document type. The roster is membership machinery, so the engine registers it, first (the mailbox's order),
 * and the app supplies only its own types. [publishRosters] runs after a live roster sync message has been applied and
 * projected, so this device's own member and devices are written back into the roster.
 */
internal fun rosterDocumentType(rosters: SpaceRosters, publishRosters: suspend () -> Unit) = SpaceDocumentType(
    "roster", SyncModules.Members, Scope.Shared, rosters::spaceDocument, rosters.changes,
    afterReceive = { spaceId -> rosters.project(spaceId) },
    afterSync = { publishRosters() }, resyncsHome = true,
)

/** The engine's send order for a Space's non-roster documents (the roster goes last); see [DeviceDataSyncService.documentIds]. */
private val SyncOrder = listOf("pantry", "shopping-list", "cookbook", "merchants", "mealplan", "calendar", "private-calendar", "tasks", "private-tasks")
private const val MaxBufferedPerSpace = 64

/**
 * Syncs the user's data between their own paired devices (docs/adr/ADR-013-device-data-sync.md): the Space registry
 * ([SpaceRegistry]), the user's Profile ([ProfileRegistry]) and each Space's Pantry and shopping-list documents, as Automerge sync messages in x0x direct messages
 * ([SyncEnvelope], ADR-006). The peers are the devices in the DeviceSync group roster ([SyncedDeviceRepository]),
 * and messages from anyone else are ignored, so group membership is the access control.
 *
 * Every device syncs with every other one, so no device needs to relay for another. Each periodic round (see
 * [syncEverything]) restarts the sync with every peer, which is how a device that was offline catches up.
 *
 * A Space's documents also go through its mailbox (docs/adr/ADR-020-syncing-without-overlapping-online-time.md): an x0x group
 * store every member and the always-on hub replicate. Each local change is deposited there, and each round, presence event
 * and foreground fetches what other devices left, so two devices that are never online together still converge. The direct
 * messages stay as they are; the mailbox is the catch-up path, and both end in idempotent Automerge merges. A Space that has
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
    spacesConfig: SpacesConfig = SpacesConfig(),
    seeding: SpaceSeeding,
) : DeviceDataSync {

    private val registry = SpaceRegistry(database, seeding)
    private val profiles = ProfileRepository(database, deviceName = deviceName)
    private val profile = ProfileRegistry(database, profiles)
    private val rosters = SpaceRosters(database, profiles, deviceName = deviceName)

    /** The Space registry, which also forgets a Space's Sync activity (ADR-033) when the Space is deleted or left on this device. */
    private val spaceListener = object : SpaceChangeListener by registry {
        override suspend fun deleted(id: String) {
            registry.deleted(id)
            recorder.forgetHome(id)
            changesWaiting.forget(id)
        }
    }
    private val join = SpaceJoinService(client, database, rosters, spaceListener, profiles, seeding, recorder = recorder, config = spacesConfig)
    private val groups = SpaceGroups(client, database, rosters, profiles, config = spacesConfig)

    /**
     * The one list of Space document types: the roster, which the engine registers itself, then the [documentTypes] the app
     * gave; the acknowledgement, activity module and private-id lookups go through it.
     */
    private val documentRegistry = SpaceDocumentRegistry(listOf(rosterDocumentType(rosters) { publishRosters() }) + documentTypes)
    private val mailboxDocuments = SpaceMailboxDocuments(documentRegistry)
    private val mailboxWriter = MailboxWriter(client, database, mailboxDocuments) { groups.mailboxStoreId(it) }
    private val mailboxReader = MailboxReader(client, database, mailboxDocuments, recorder) { groups.mailboxStoreId(it) }

    private val acknowledgement = SyncAcknowledgement(documentRegistry)

    private val activityFacts = SyncActivityFacts()

    /** Whether each Space has changes no other device has acknowledged (ADR-033); see [ChangesWaiting] for when it is worked out. */
    private val changesWaiting = ChangesWaiting(
        database,
        sharedDocumentIds = { mailboxDocuments.documentIds(it) },
        privateDocumentIds = { documentRegistry.documentIds(it, Scope.Private) },
        targets = { spaceId ->
            val self = self()
            val paired = devices.agentIds() - setOfNotNull(self)
            val members = rosters.devices(spaceId).values.flatten()
            SyncTargets(shared = (paired + members - setOfNotNull(self)).isNotEmpty() || rosters.hubs(spaceId).isNotEmpty(), own = paired.isNotEmpty())
        },
        heads = { spaceId, documentId -> headsOf(spaceId, documentId) },
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

    override suspend fun agentDetails(spaceId: String, agentId: String): AgentDetails {
        val discovered = runCatching { client.getDiscoveredAgent(agentId) }.getOrNull()
        val now = nowMillis()
        return AgentDetails(
            name = rosters.deviceNames(spaceId)[agentId],
            machineId = discovered?.machineId,
            lastSeenMsAgo = discovered?.lastSeen?.let { (now - it * 1_000).coerceAtLeast(0) },
            lastSyncedMsAgo = (lastSynced[agentId] ?: recorder.lastSyncedAt(agentId))?.let { (now - it).coerceAtLeast(0) },
        )
    }

    override val spaceChanges: SpaceChangeListener get() = spaceListener

    override val joining: SpaceJoining get() = join

    override val hubs: SpaceHub = SpaceHubs(client, database, rosters, profiles, config = spacesConfig)

    override suspend fun memberAgents(spaceId: String, memberId: String): List<String> =
        rosters.devices(spaceId)[memberId].orEmpty().sorted()

    override val profileChanges: ProfileChangeListener = object : ProfileChangeListener {
        override suspend fun changed() {
            profile.publish()
            publishRosters() // the new name goes into each Space's roster too
        }
    }

    override val rosterChanges: RosterChangeListener = object : RosterChangeListener {
        override suspend fun roleChanged(spaceId: String, memberId: String, role: String) = rosters.setRole(spaceId, memberId, role)
        override suspend fun memberRemoved(spaceId: String, memberId: String) = join.removeMember(spaceId, memberId)
        override suspend fun memberLeft(spaceId: String, memberId: String) = join.announceLeft(spaceId, memberId)
    }

    // One sync step at a time: every step reads and writes the per-peer sync states.
    private val lock = Mutex()
    private val states = mutableMapOf<Pair<String, String>, SyncState>()

    // Pantry and roster messages that arrived before their Space did (the registry and a Space's documents sync side by
    // side): the sending peer, the document id and the sync message.
    private val waiting = mutableMapOf<String, MutableList<Triple<String, String, ByteArray>>>()

    override suspend fun run() = coroutineScope {
        registry.importLocalSpaces()
        attempt { profile.publish() }
        attempt { publishRosters() }
        launch { X0xEventHub(client).directMessages.collect { message -> launch { attempt { handle(message) } } } }
        launch { registry.changes.collect { attempt { syncDocument(SpaceRegistryDocumentId) } } }
        launch { registry.changes.collect { attempt { publishRosters() } } }
        launch { profile.changes.collect { attempt { syncDocument(ProfileDocumentId) } } }
        documentRegistry.types.forEach { type ->
            launch {
                type.localChanges.collect { spaceId ->
                    val documentId = type.documentId(spaceId)
                    // A private document goes to the user's own devices only: no mailbox, and no Space members to add.
                    if (type.scope == Scope.Shared) launch { attempt { mailboxWriter.deposit(spaceId, documentId) } } // not behind sends to absent peers
                    attempt { syncDocument(documentId) }
                    // A roster change can add a device to a Space's peers, which then needs everything the Space has.
                    if (type.resyncsHome) {
                        documentRegistry.documentIds(spaceId, Scope.Shared).filter { it != documentId }.sortedBy { syncOrderOf(it) }
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
     * Deposits what this device changed and fetches what others left, in every Space's mailbox. It runs outside [lock]: store
     * calls can be slow, and the documents and the mailbox keep their own locks. A Space whose roster, Pantry or list changed
     * through a deposit is published and synced again, as one that arrived by direct message is.
     */
    private suspend fun syncMailboxes() {
        var applied = 0
        database.homeQueries.selectAll().awaitAsList().forEach { space ->
            attempt { if (mailboxWriter.deposit(space.id) > 0) recorder.mailbox(space.id, incoming = false, peer = null) }
            attempt { mailboxWriter.compact(space.id) }
            attempt {
                val fetched = mailboxReader.fetch(space.id)
                applied += fetched
                if (fetched > 0) recorder.mailbox(space.id, incoming = true, peer = null)
            }
        }
        if (applied > 0) attempt { publishRosters() }
    }

    /** The agents that act for each member of [spaceId], by member id, as its roster says. */
    internal suspend fun rosterDevices(spaceId: String): Map<String, Set<String>> = rosters.devices(spaceId)

    /**
     * Admits a person to [spaceId]'s roster as [role], with the agent that acts for them (the invite flow's job,
     * ADR-014), and makes them a peer of that Space. Also how a joining device learns who invited it, since a device
     * only syncs a Space with the agents its roster lists.
     */
    internal suspend fun admit(spaceId: String, memberId: String, name: String, role: String, agentId: String) =
        lock.withLock { rosters.admit(spaceId, memberId, name, role, agentId) }

    /** Marks [memberId] removed from [spaceId] and stops syncing it with their devices. */
    internal suspend fun removeMember(spaceId: String, memberId: String) = lock.withLock { rosters.remove(spaceId, memberId) }

    /**
     * Handles one direct message: a sync message is applied and answered if its sender is a peer for that document
     * ([peersFor]); anything else is ignored.
     */
    suspend fun handle(message: X0xDirectMessage) {
        if (message.verified == true) message.sender?.let { peerDownUntil.remove(it) } // it is evidently there
        if (join.handle(message)) return // a Space-join message, not a sync message
        if (groups.handle(message)) return // a Space-group message (ADR-021)
        attempt { recordUnknownMessage(message) } // noted in Sync activity (ADR-034), then handled as before: dropped
        syncFrom(message)
    }

    /**
     * Notes a verified peer's message of a family this version knows (device-sync, Space-group) but with a `kind` it doesn't, which a newer
     * Vesta may send. Anything else, such as an agent command on the same stream, is not a message of those families and is not noted.
     * It goes on the Space the message names when it names one, otherwise on every Space the sender is a peer of.
     */
    private suspend fun recordUnknownMessage(message: X0xDirectMessage) {
        if (message.verified != true) return
        val sender = message.sender ?: return
        val payload = message.payload
        val (kind, spaceId) = DeviceSyncMessage.unrecognisedKind(payload)?.let { it to null }
            ?: SpaceGroupMessage.unrecognisedKind(payload)?.let { it to SpaceGroupMessage.stringField(payload, "homeId") }
            ?: return
        val spaces = spaceId?.let { listOfNotNull(database.homeQueries.selectById(it).awaitAsOneOrNull()?.id) }
            ?: database.homeQueries.selectAll().awaitAsList().map { it.id }
        spaces.filter { sender in spacePeers(it) }.forEach { recorder.unknownMessage(it, sender, kind) }
    }

    /** Notes a document id this version doesn't know, from a peer of the Space it names (`<space id>:<document type>`), as needing a newer Vesta. */
    private suspend fun recordUnknownDocument(sender: String, documentId: String) {
        if (documentId == SpaceRegistryDocumentId || documentId == ProfileDocumentId) return
        if (documentRegistry.parse(documentId) != null) return
        val spaceId = documentId.substringBeforeLast(':', "").takeIf { it.isNotEmpty() } ?: return
        if (database.homeQueries.selectById(spaceId).awaitAsOneOrNull() == null) return
        if (sender !in spacePeers(spaceId)) return
        recorder.unsupportedVersion(spaceId, SyncModules.Mailbox, sender, documentId.substringAfterLast(':'))
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
            activitySpaceId(envelope.documentId)?.let { it to acknowledgedDocuments(it) }
        }
        acknowledged?.let { (spaceId, documents) -> attempt { changesWaiting.update(spaceId, documents) } }
        activityFacts.notifyChanged()
    }

    /** The ids of [spaceId]'s documents that some peer's sync state reports in sync. Call it holding [lock]; it reads memory only. */
    private suspend fun acknowledgedDocuments(spaceId: String): Set<String> {
        val ids = mailboxDocuments.documentIds(spaceId) + documentRegistry.documentIds(spaceId, Scope.Private)
        return ids.filterTo(mutableSetOf()) { id ->
            states.any { (key, state) -> key.first == id && acknowledgement.isInSync(spaceId, id, state) }
        }
    }

    /** Works out [ChangesWaiting] for every Space. Call it holding no lock: the sync states are read under it, the rest is not. */
    private suspend fun refreshChangesWaiting() {
        val spaces = database.homeQueries.selectAll().awaitAsList().map { it.id }
        val acknowledged = lock.withLock { spaces.associateWith { acknowledgedDocuments(it) } }
        acknowledged.forEach { (spaceId, documents) -> attempt { changesWaiting.update(spaceId, documents) } }
    }

    private var selfAgent: String? = null

    private suspend fun self(): String? = selfAgent ?: runCatching { client.agent().agentId }.getOrNull()?.also { selfAgent = it }

    /**
     * Who [documentId] is synced with. The user's own paired devices share everything; a Space's own documents (its
     * Pantry, shopping list and roster) are also shared with every device its roster lists for a member who hasn't been removed
     * (ADR-014), so the members of a Space sync it between them. The Space registry and Profile are only ever the user's.
     */
    private suspend fun peersFor(documentId: String): Set<String> {
        val paired = devices.agentIds()
        val spaceId = spaceIdOf(documentId) ?: return paired // a private type or a non-Space document: the user's own devices only
        return spacePeers(spaceId)
    }

    /** The devices a Space's shared documents sync with: the user's paired devices and the Space's members' devices. */
    private suspend fun spacePeers(spaceId: String): Set<String> =
        (devices.agentIds() + rosters.devices(spaceId).values.flatten()) - setOfNotNull(self())

    /** The same devices [spaceId]'s shared documents sync with, so a photo reaches whoever can see the recipe that shows it. */
    override suspend fun blobPeers(spaceId: String): Set<String> = spacePeers(spaceId)

    /** The Space a shared document belongs to; null for a private type's document and for anything the registry doesn't know. */
    private fun spaceIdOf(documentId: String): String? =
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
            activitySpaceId(documentId)?.let { it to acknowledgedDocuments(it) }
        }
        acknowledged?.let { (spaceId, documents) -> attempt { changesWaiting.update(spaceId, documents) } }
        activityFacts.notifyChanged()
    }

    private suspend fun documentIds(): List<String> =
        listOf(SpaceRegistryDocumentId, ProfileDocumentId) + database.homeQueries.selectAll().awaitAsList()
            .flatMap { space -> documentRegistry.documentIds(space.id).sortedBy { syncOrderOf(it) } }

    /**
     * The order a Space's documents are sent in, with the roster last. It is the engine's own and is deliberately not the registry's
     * (roster first, private types last) nor the mailbox's; a type not listed here goes after the listed ones, in registry order.
     */
    private fun syncOrderOf(documentId: String): Int = when (val feature = documentRegistry.parse(documentId)?.type?.feature) {
        "roster" -> Int.MAX_VALUE
        else -> SyncOrder.indexOf(feature).takeIf { it >= 0 } ?: SyncOrder.size
    }

    private suspend fun receive(sender: String, documentId: String, message: ByteArray) {
        if (documentId == SpaceRegistryDocumentId) {
            registry.receive(stateFor(documentId, sender), message)
            val created = registry.project()
            // Spaces can now exist that a Pantry message was waiting for.
            created.forEach { spaceId ->
                waiting.remove(spaceId)?.forEach { (peer, documentId, queued) -> receiveSpaceDocument(peer, documentId, queued) }
            }
            publishRosters() // a Space that has arrived may now have a member row for this user to publish

            val existing = database.homeQueries.selectAll().awaitAsList().mapTo(mutableSetOf()) { it.id }
            waiting.keys.retainAll(existing)
        } else if (documentId == ProfileDocumentId) {
            profile.receive(stateFor(documentId, sender), message)
            // A device that took on the shared Profile retires its old member id, then publishes the new one.
            profile.project()?.let { rosters.retire(it) }
            publishRosters()
        } else {
            val (spaceId, type) = documentRegistry.parse(documentId) ?: return // a document this version doesn't know about
            if (type.scope == Scope.Private) {
                // Not buffered when this device lacks the Space, and not applied through applySpaceDocument: a later round recovers it.
                recordingReceived(sender, documentId) { type.open(spaceId)?.receiveSync(stateFor(documentId, sender), message) }
            } else {
                if (!receiveSpaceDocument(sender, documentId, message)) {
                    waiting.getOrPut(spaceId) { mutableListOf() }.let { if (it.size < MaxBufferedPerSpace) it += Triple(sender, documentId, message) }
                    return
                }
                type.afterSync(spaceId)
            }
        }
        // Answer the sender, and tell the other peers what changed.
        peersFor(documentId).forEach { pump(documentId, it) }
    }

    /** Applies a message for one of a Space's shared documents; false when this device has no such Space yet. */
    private suspend fun receiveSpaceDocument(sender: String, documentId: String, message: ByteArray): Boolean =
        recordingReceived(sender, documentId) { applySpaceDocument(sender, documentId, message) }

    private suspend fun applySpaceDocument(sender: String, documentId: String, message: ByteArray): Boolean {
        val (spaceId, type) = documentRegistry.parse(documentId) ?: return false
        val document = type.open(spaceId) ?: return false // no such Space yet
        document.receiveSync(stateFor(documentId, sender), message)
        type.afterReceive(spaceId)
        return true
    }

    /** Writes this user's member and this device's agent into every Space's roster, and drops devices that are no longer paired; nothing is written about devices while x0x can't say what this one's agent is. */
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
                    SpaceRegistryDocumentId -> registry.generate(state)
                    ProfileDocumentId -> profile.generate(state)
                    else -> documentRegistry.parse(documentId)?.let { (spaceId, type) -> type.open(spaceId)?.generateSync(state) }
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

    // The Sync activity log (ADR-033) records Space documents only; the registry and Profile belong to no Space. Recording never
    // changes a sync result: the recorder swallows its own failures, and reading heads is guarded here the same way.

    private fun activitySpaceId(documentId: String): String? = documentRegistry.parse(documentId)?.spaceId

    private suspend fun noteSent(documentId: String, peer: String) {
        val spaceId = activitySpaceId(documentId) ?: return
        documentRegistry.moduleOf(documentId)?.let { recorder.sent(spaceId, it, peer) }
    }

    private suspend fun noteFailed(documentId: String, peer: String) {
        val spaceId = activitySpaceId(documentId)
        activityFacts.sendFailed(spaceId)
        if (spaceId == null) return
        documentRegistry.moduleOf(documentId)?.let { recorder.failed(spaceId, it, peer, "unreachable") }
    }

    /** The heads of a Space document, or null for anything else, a Space this device doesn't have yet, or a failed read. */
    private suspend fun headsOf(spaceId: String, documentId: String): ByteArray? = try {
        documentRegistry.open(spaceId, documentId)?.heads()
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
        val spaceId = activitySpaceId(documentId) ?: return apply()
        val before = headsOf(spaceId, documentId)
        val result = apply()
        headsOf(spaceId, documentId)?.takeUnless { it.contentEquals(before) }
            ?.let { documentRegistry.moduleOf(documentId)?.let { module -> recorder.received(spaceId, module, sender) } }
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
