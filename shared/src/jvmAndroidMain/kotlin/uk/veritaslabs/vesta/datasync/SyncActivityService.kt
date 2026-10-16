@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.vesta.datasync

import uk.veritaslabs.kits.docs.SyncEventRecorder
import uk.veritaslabs.kits.docs.SyncEventKinds
import uk.veritaslabs.kits.docs.ChangesWaiting

import app.cash.sqldelight.async.coroutines.awaitAsList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import uk.veritaslabs.vesta.db.SyncEvent
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.devicesync.SyncedDeviceRepository
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.syncactivity.PeerActivity
import uk.veritaslabs.vesta.syncactivity.SyncActivityRepository
import uk.veritaslabs.vesta.syncactivity.SyncEventFilter
import uk.veritaslabs.vesta.syncactivity.SyncEventItem
import uk.veritaslabs.vesta.syncactivity.SyncEventKind
import uk.veritaslabs.vesta.syncactivity.SyncModule
import uk.veritaslabs.vesta.syncactivity.SyncSnapshot

/**
 * What [DeviceDataSyncService] knows about its own running that the Sync activity screen shows, kept as the service goes: which peers
 * presence says are online, whether the daemon answered, whether a round is running and how the last one went. [notifyChanged]
 * wakes the snapshot flows.
 */
internal class SyncActivityFacts {
    /** Whether each agent was last seen online (presence events, or a message from it) or offline (a failed send). Absent when unknown. */
    val online = ConcurrentHashMap<String, Boolean>()

    /** Whether the daemon answered when the last round began. Assumed so until a round says otherwise. */
    @Volatile var daemonReachable = true

    @Volatile var lastRoundAt: Long? = null

    private val rounds = AtomicInteger()

    val roundInProgress: Boolean get() = rounds.get() > 0

    /**
     * The Homes with a failed send since the current round began, and those of the round before. A send that failed for the Home
     * registry or the Profile belongs to no Home, so it counts against all of them.
     */
    private val failing = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var failingAll = false
    @Volatile private var lastRoundFailed: Set<String> = emptySet()
    @Volatile private var lastRoundFailedAll = false

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits whenever something a snapshot shows may have changed. */
    val changed: Flow<Unit> get() = changes

    fun notifyChanged() {
        changes.tryEmit(Unit)
    }

    fun roundStarted(daemonReachable: Boolean) {
        rounds.incrementAndGet()
        this.daemonReachable = daemonReachable
        failing.clear()
        failingAll = false
        notifyChanged()
    }

    fun roundEnded(now: Long) {
        lastRoundFailed = failing.toSet()
        lastRoundFailedAll = failingAll
        lastRoundAt = now
        rounds.decrementAndGet()
        notifyChanged()
    }

    /** A send failed; [homeId] is the Home whose document it was, null for one that belongs to none. */
    fun sendFailed(homeId: String?) {
        homeId?.let { failing.add(it) } ?: run { failingAll = true }
    }

    fun lastRoundHadFailures(homeId: String): Boolean = lastRoundFailedAll || homeId in lastRoundFailed
}

/**
 * The [SyncActivityRepository] of [DeviceDataSyncService] (docs/adr/ADR-033-sync-activity.md): a Home's devices and hubs with
 * presence and last-synced times, the verdict's inputs, and the log the [recorder] keeps. [lastSynced] and [peerDownUntil] are the
 * service's own maps, read here and never written. A snapshot is emitted whenever [facts] says something changed, and at least every
 * [refreshInterval], so "Up to date" can age without anything happening.
 */
internal class SyncActivityService(
    private val database: KitsDatabase,
    private val recorder: SyncEventRecorder,
    private val rosters: HomeRosters,
    private val profiles: ProfileRepository,
    private val devices: SyncedDeviceRepository,
    private val facts: SyncActivityFacts,
    private val changesWaiting: ChangesWaiting,
    private val lastSynced: Map<String, Long>,
    private val peerDownUntil: Map<String, Long>,
    private val selfAgent: suspend () -> String?,
    private val requestSync: () -> Unit,
    private val nowMillis: () -> Long,
    private val refreshInterval: Duration = 20.seconds,
) : SyncActivityRepository {

    override val available: Boolean = true

    override fun snapshot(homeId: String): Flow<SyncSnapshot> = flow {
        emit(build(homeId))
        val ticks = flow { while (true) { delay(refreshInterval); emit(Unit) } }
        merge(facts.changed, ticks).conflate().collect { emit(build(homeId)) }
    }

    override suspend fun events(homeId: String, filter: SyncEventFilter, peerAgent: String?, beforeLastAt: Long?, limit: Int): List<SyncEventItem> {
        val kinds = when (filter) {
            SyncEventFilter.All -> null
            SyncEventFilter.Received -> listOf(SyncEventKinds.Received, SyncEventKinds.MailboxIn)
            SyncEventFilter.Sent -> listOf(SyncEventKinds.Sent, SyncEventKinds.MailboxOut)
            SyncEventFilter.Problems -> listOf(SyncEventKinds.Failed, SyncEventKinds.UnsupportedVersion, SyncEventKinds.UnknownMessage)
        }
        val oldest = nowMillis() - Retention.inWholeMilliseconds
        val found = mutableListOf<SyncEvent>()
        var cursor = beforeLastAt ?: Long.MAX_VALUE
        // The log has no query by device, so pages are read and filtered until there are enough rows or no more.
        while (found.size < limit) {
            val page = readPage(homeId, kinds, cursor, limit).filter { it.lastAt >= oldest }
            if (page.isEmpty()) break
            found += page.filter { peerAgent == null || it.peerAgent == peerAgent }
            cursor = page.last().lastAt
            if (page.size < limit) break
        }
        return found.take(limit).mapNotNull { it.toItem() }
    }

    override fun syncNow() = requestSync()

    /** One page of rows newest first. Several kinds are read separately and merged, which is exact because each is read to [limit]. */
    private suspend fun readPage(homeId: String, kinds: List<String>?, before: Long, limit: Int): List<SyncEvent> {
        val queries = database.syncEventQueries
        if (kinds == null) return queries.selectPage(homeId, before, limit.toLong()).awaitAsList()
        return kinds.flatMap { queries.selectPageOfKind(homeId, it, before, limit.toLong()).awaitAsList() }
            .sortedWith(compareByDescending<SyncEvent> { it.lastAt }.thenByDescending { it.id })
            .take(limit)
    }

    private fun SyncEvent.toItem(): SyncEventItem? {
        val itemKind = when (kind) {
            SyncEventKinds.Received -> SyncEventKind.Received
            SyncEventKinds.Sent -> SyncEventKind.Sent
            SyncEventKinds.Failed -> SyncEventKind.Failed
            SyncEventKinds.Membership -> SyncEventKind.Membership
            SyncEventKinds.MailboxIn -> SyncEventKind.MailboxIn
            SyncEventKinds.MailboxOut -> SyncEventKind.MailboxOut
            SyncEventKinds.UnsupportedVersion -> SyncEventKind.UnsupportedVersion
            SyncEventKinds.UnknownMessage -> SyncEventKind.UnknownMessage
            else -> return null
        }
        val itemModule = SyncModule(module)
        return SyncEventItem(id, at, lastAt, itemKind, itemModule, peerAgent, detail, count.toInt())
    }

    private suspend fun build(homeId: String): SyncSnapshot {
        val now = nowMillis()
        val self = selfAgent()
        val me = profiles.current().id
        val paired = devices.agentIds()
        val hubs = rosters.hubs(homeId)
        val names = rosters.deviceNames(homeId)
        val memberNames = rosters.members(homeId).associate { it.memberId to it.name }
        val pending = database.pendingMessageQueries.selectAll().awaitAsList().filter { it.homeId == homeId }
        val retryingSince = pending.groupBy { it.recipient }.mapValues { (_, rows) -> rows.minOf { it.createdAt } }

        suspend fun peer(agentId: String, memberId: String?): PeerActivity {
            val mine = memberId == me || agentId in paired
            return PeerActivity(
                agentId = agentId,
                name = names[agentId] ?: agentId.take(UnnamedLength),
                ownerName = memberId?.takeUnless { mine }?.let { memberNames[it] },
                isYou = agentId == self,
                isHub = agentId in hubs,
                isOwnDevice = mine,
                online = agentId == self || isOnline(agentId, now),
                lastSyncedMsAgo = lastSyncedMsAgo(agentId, now),
                retryingSinceMsAgo = retryingSince[agentId]?.let { (now - it).coerceAtLeast(0) },
            )
        }

        val peers = linkedMapOf<String, PeerActivity>()
        self?.let { peers[it] = peer(it, me) }
        rosters.devices(homeId).forEach { (memberId, agents) ->
            agents.sorted().forEach { agent -> peers.getOrPut(agent) { peer(agent, memberId) } }
        }
        paired.sorted().forEach { agent -> peers.getOrPut(agent) { peer(agent, me) } }
        hubs.sorted().forEach { agent -> peers.getOrPut(agent) { peer(agent, null) } }
        val others = peers.values.filterNot { it.isYou }
        val latest = others.mapNotNull { p -> p.lastSyncedMsAgo?.let { p to it } }.minByOrNull { it.second }

        return SyncSnapshot(
            takenAt = now,
            peers = peers.values.toList(),
            hasOtherDevices = others.isNotEmpty(),
            daemonReachable = facts.daemonReachable,
            roundInProgress = facts.roundInProgress,
            lastRoundAt = facts.lastRoundAt,
            lastRoundHadFailures = facts.lastRoundHadFailures(homeId),
            changesWaiting = try {
                changesWaiting.of(homeId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            },
            needsNewerVersion = needsNewerVersion(homeId, now),
            stalledMessageAgeMs = pending.minOfOrNull { it.createdAt }?.let { now - it }?.takeIf { it > 0 },
            lastSyncedPeerName = latest?.first?.name,
            lastSyncedMsAgo = latest?.second,
        )
    }

    /** True when the log holds a change from a newer format within the retention window (ADR-034); the log is what remembers it across restarts. */
    private suspend fun needsNewerVersion(homeId: String, now: Long): Boolean = try {
        database.syncEventQueries.selectPageOfKind(homeId, SyncEventKinds.UnsupportedVersion, Long.MAX_VALUE, 1).awaitAsList()
            .any { it.lastAt >= now - Retention.inWholeMilliseconds }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** Online by what presence and messages have said, but not while a send to it is being held off after failing. */
    private fun isOnline(agentId: String, now: Long): Boolean =
        (peerDownUntil[agentId] ?: 0L) <= now && facts.online[agentId] == true

    private suspend fun lastSyncedMsAgo(agentId: String, now: Long): Long? =
        (lastSynced[agentId] ?: recorder.lastSyncedAt(agentId))?.let { (now - it).coerceAtLeast(0) }

    private companion object {
        val Retention = 7.days
        const val UnnamedLength = 8
    }
}
