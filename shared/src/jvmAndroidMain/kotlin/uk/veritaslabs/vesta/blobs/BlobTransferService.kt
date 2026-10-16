package uk.veritaslabs.vesta.blobs

import app.cash.sqldelight.async.coroutines.awaitAsList
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.automerge.CookbookSources
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage
import uk.veritaslabs.vesta.x0x.X0xEventHub
import uk.veritaslabs.vesta.x0x.X0xFileEvent
import uk.veritaslabs.vesta.x0x.X0xNetworkPolicy

/**
 * Moves the blobs the documents refer to between a Home's devices (docs/adr/ADR-023-blob-storage-and-transfer.md), by native x0x file transfer, and
 * only while a device that holds one and a device that lacks it are online together. Nothing is stored for later and the hub carries no bytes:
 * what a device lacks is its want list ([BlobRepository.wants]), and everything here is a way of working through it that can be killed at any point.
 *
 * * **Scan:** each Home's cookbook is read for blob references; a hash this device holds is touched, one it lacks becomes a want. Run at start, each
 *   [interval], and whenever a cookbook changes.
 * * **Push:** a reference that is new to a cookbook, and whose blob this device holds, is offered to the Home's devices that are online (the author's
 *   photo reaching those present).
 * * **Pull:** for each due want, a `blob.want` message to the Home's online devices; backoff [askBackoff] per want, restarted when any device comes
 *   online. Any holder answers, not just the author.
 * * **Serve:** a `blob.want` from a device of the Home, for a hash the Home's cookbook refers to and this device holds, is answered with a file
 *   transfer of the stored file. Repeats within [serveWindow] and more than [maxServing] at once are ignored (the asker asks again).
 * * **Accept gate:** a file offer is accepted only from a device of the wanting Home, named `<hash>.webp`, for a hash on the want list, at exactly the
 *   size the document gives, and once per hash; anything else is rejected at once. A finished file is hashed again and moved into the store
 *   ([BlobRepository.importFile]); the daemon's own sha256 is not relied on.
 *
 * * **Janitor:** when given the daemon's `transfers/` folder, each round sweeps it of orphaned `.part` files and finished photos nobody moved ([TransfersJanitor]).
 * * **Garbage collection:** a blob no cookbook has referred to for [gracePeriod] and that is not wanted is deleted ([collectGarbage]).
 *
 * [peersFor] gives the devices a Home syncs with (its roster's devices and the user's own, not this one).
 */
class BlobTransferService(
    private val client: X0xClient,
    private val repository: BlobRepository,
    private val cookbookSources: CookbookSources,
    private val database: KitsDatabase,
    private val peersFor: suspend (homeId: String) -> Set<String>,
    private val onArrived: (hash: String) -> Unit = {},
    private val interval: Duration = 30.seconds,
    private val askBackoff: Backoff = Backoff(30.seconds, 10.minutes),
    private val serveWindow: Duration = 30.seconds,
    private val maxServing: Int = 4,
    private val acceptTimeout: Duration = 5.minutes,
    private val gracePeriod: Duration = 7.days,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val isOnline: (suspend (agentId: String) -> Boolean)? = null,
    transfersDir: java.io.File? = null,
    transfersMinAge: Duration = 15.minutes,
) {
    private val janitor = transfersDir?.let { TransfersJanitor(it, client, repository, transfersMinAge, nowMillis, onArrived) }

    /** Capped exponential delay between asks for one blob: [initial], doubling per attempt up to [max]. */
    data class Backoff(val initial: Duration, val max: Duration) {
        /** How long to wait after the [attempts]th ask (1 or more) before the next. */
        fun delayAfter(attempts: Long): Duration =
            min(initial.inWholeMilliseconds * (1L shl (attempts - 1).coerceIn(0, 20).toInt()), max.inWholeMilliseconds).milliseconds
    }

    private val online = ConcurrentHashMap.newKeySet<String>()
    private val lock = Mutex()

    /** Hashes each Home's cookbook referred to at the last scan; a reference that is new since then is pushed. Null until the first scan. */
    private val knownReferences = mutableMapOf<String, Set<String>>()
    private val watching = mutableSetOf<String>()

    /** Offers this device accepted and is waiting on: transfer id by hash, with when. */
    private class Accepting(val transferId: String, val at: Long)
    private val accepting = mutableMapOf<String, Accepting>()

    /** When each (device, hash) was last served, for de-duplication and the concurrency cap. */
    private val recentServes = mutableMapOf<Pair<String, String>, Long>()

    private val _waitingForWifi = MutableStateFlow(false)

    /**
     * True while blobs are wanted and x0x is stopped because mobile data is not allowed (Settings): nothing can move until Wi-Fi. A heuristic
     * from the policy and whether the daemon answers, not a measurement of the connection.
     */
    val waitingForWifi: StateFlow<Boolean> = _waitingForWifi

    suspend fun run() = coroutineScope {
        attempt { repository.reconcile(nowMillis()) }
        val hub = X0xEventHub(client)
        launch { hub.fileEvents.collect { event -> launch { attempt { onFileEvent(event) } } } }
        launch { hub.directMessages.collect { message -> launch { attempt { onDirect(message) } } } }
        launch {
            hub.presence.collect { event ->
                if (event.isOnline) {
                    online += event.agentId
                    attempt { repository.resetBackoff() }
                    launch { attempt { askRound() } }
                } else {
                    online -= event.agentId
                }
            }
        }
        launch { cookbookSources.localChanges.collect { homeId -> attempt { scan(homeId) } } }
        while (true) {
            attempt { watchHomes() }
            attempt { round() }
            delay(interval)
        }
    }

    /** One pass of everything that is time-driven. Public so a foreground event can ask for it. */
    suspend fun round() {
        val homes = database.homeQueries.selectAll().awaitAsList().map { it.id }
        homes.forEach { attempt { scan(it) } }
        attempt { checkAccepted() }
        attempt { askRound() }
        attempt { collectGarbage() }
        attempt { janitor?.sweep(lock.withLock { accepting.values.map { it.transferId }.toSet() }) }
        attempt { _waitingForWifi.value = repository.wants().isNotEmpty() && !X0xNetworkPolicy.useMobileData.value && client.health() == null }
    }

    /** Starts watching each Home's cookbook that is not watched yet: a change that arrives from another device (a sync) is scanned at once, not at the next round. */
    private suspend fun CoroutineScope.watchHomes() {
        database.homeQueries.selectAll().awaitAsList().map { it.id }.forEach { homeId ->
            if (lock.withLock { watching.add(homeId) }) {
                launch { cookbookSources.forHome(homeId)?.recipes?.collect { attempt { scan(homeId) } } }
            }
        }
    }

    // --- Garbage collection ---

    /**
     * Deletes the blobs no cookbook has referred to for [gracePeriod] and that nothing wants. The period is what lets a photo removed from a recipe by
     * mistake, or a Home's cookbook that has not synced yet, come back without the bytes being gone; a reference that reappears restarts the clock
     * (the scan touches it). Does nothing if any Home's cookbook could not be read, so a failure never looks like "no references".
     */
    suspend fun collectGarbage() {
        val stale = repository.unreferencedSince(nowMillis() - gracePeriod.inWholeMilliseconds)
        if (stale.isEmpty()) return
        val homes = database.homeQueries.selectAll().awaitAsList().map { it.id }
        val referenced = mutableSetOf<String>()
        for (homeId in homes) {
            val source = cookbookSources.forHome(homeId) ?: return
            source.blobReferences().mapTo(referenced) { it.hash }
        }
        val wanted = repository.wants().mapTo(mutableSetOf()) { it.hash }
        stale.filter { it !in referenced && it !in wanted }.forEach { repository.delete(it) }
    }

    // --- Scan and push ---

    /** Reads [homeId]'s references: wants the missing, touches the held, and pushes the held that are new to the cookbook. */
    suspend fun scan(homeId: String) {
        val references = cookbookSources.forHome(homeId)?.blobReferences().orEmpty()
            .filter { it.size in 1..PhotoLimits.MaxInputBytes }
        val now = nowMillis()
        val toPush = mutableListOf<BlobReference>()
        val first: Boolean
        lock.withLock {
            first = homeId !in knownReferences
            val known = knownReferences[homeId].orEmpty()
            references.filter { it.hash !in known && repository.has(it.hash) && !first }.forEach { toPush += it }
            knownReferences[homeId] = references.mapTo(mutableSetOf()) { it.hash }
        }
        references.forEach { reference ->
            if (repository.has(reference.hash)) repository.touch(reference.hash, now) else repository.want(reference.hash, homeId, reference.size, now)
        }
        if (toPush.isNotEmpty()) push(homeId, toPush)
    }

    private suspend fun push(homeId: String, references: List<BlobReference>) {
        val targets = peersFor(homeId).filter { isOnline(it) }
        references.forEach { reference ->
            val file = repository.file(reference.hash)?.takeIf { it.isFile } ?: return@forEach
            targets.forEach { peer -> attempt { client.sendFile(peer, "${reference.hash}.webp", file.length(), reference.hash, file.path) } }
        }
    }

    // --- Pull ---

    private suspend fun askRound() {
        val now = nowMillis()
        val perHome = repository.wants().filter { want -> accepting[want.hash] == null }
            .filter { want -> want.attempts == 0L || now - want.lastAskedAt >= askBackoff.delayAfter(want.attempts).inWholeMilliseconds }
            .groupBy { it.homeId }
        perHome.forEach { (homeId, wants) ->
            val targets = peersFor(homeId).filter { isOnline(it) }
            if (targets.isEmpty()) return@forEach // nobody to ask: not an attempt
            wants.forEach { want ->
                val message = BlobMessage.encode(BlobMessage.Want(homeId, want.hash))
                targets.forEach { peer -> attempt { client.sendDirect(peer, message) } }
                repository.recordAsk(want.hash, now)
            }
        }
    }

    // --- Serve ---

    private suspend fun onDirect(message: X0xDirectMessage) {
        val want = BlobMessage.decode(message.payload) as? BlobMessage.Want ?: return
        if (message.verified != true) return
        val sender = message.sender ?: return
        if (!BlobStore.isHash(want.hash) || sender !in peersFor(want.homeId)) return
        val referenced = cookbookSources.forHome(want.homeId)?.blobReferences().orEmpty().any { it.hash == want.hash }
        if (!referenced) return
        val file = repository.file(want.hash)?.takeIf { it.isFile } ?: return
        val now = nowMillis()
        val admitted = lock.withLock {
            recentServes.entries.removeAll { now - it.value > serveWindow.inWholeMilliseconds }
            if ((sender to want.hash) in recentServes || recentServes.size >= maxServing) {
                false
            } else {
                recentServes[sender to want.hash] = now
                true
            }
        }
        if (admitted) client.sendFile(sender, "${want.hash}.webp", file.length(), want.hash, file.path)
    }

    // --- Receive ---

    private suspend fun onFileEvent(event: X0xFileEvent) {
        when (event) {
            is X0xFileEvent.Offer -> gate(event)
            is X0xFileEvent.Complete -> finish(event)
            is X0xFileEvent.Failed -> lock.withLock { accepting.entries.removeAll { it.value.transferId == event.transferId } }
        }
    }

    private suspend fun gate(offer: X0xFileEvent.Offer) {
        val hash = offer.filename.removeSuffix(".webp").takeIf { offer.filename.endsWith(".webp") && BlobStore.isHash(it) }
        val want = hash?.let { h -> repository.wants().firstOrNull { it.hash == h } }
        val allowed = want != null && offer.size == want.size && offer.sender in peersFor(want.homeId)
        val take = allowed && lock.withLock {
            val current = accepting[hash]
            if (current != null && nowMillis() - current.at < acceptTimeout.inWholeMilliseconds) {
                false
            } else {
                accepting[hash!!] = Accepting(offer.transferId, nowMillis())
                true
            }
        }
        if (take) {
            try {
                client.acceptFile(offer.transferId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lock.withLock { accepting.remove(hash) }
            }
        } else {
            attempt { client.rejectFile(offer.transferId) }
        }
    }

    private suspend fun finish(complete: X0xFileEvent.Complete) {
        val hash = lock.withLock { accepting.entries.firstOrNull { it.value.transferId == complete.transferId }?.key } ?: return
        val want = repository.wants().firstOrNull { it.hash == hash }
        if (want == null) {
            // Satisfied some other way meanwhile; don't leave the daemon's copy lying about.
            java.io.File(complete.path).delete()
        } else {
            val result = repository.importFile(complete.path, hash, want.size, PhotoLimits.MimeType, nowMillis())
            if (result is BlobImport.Imported) onArrived(hash)
        }
        lock.withLock { accepting.remove(hash) }
    }

    /** Forgets accepted offers that failed or never finished, so the blob can be asked for again. */
    private suspend fun checkAccepted() {
        val now = nowMillis()
        val pending = lock.withLock { accepting.entries.map { it.key to it.value } }
        pending.forEach { (hash, accepted) ->
            val state = runCatching { client.getFileTransfer(accepted.transferId)?.state }.getOrNull()
            val dead = state == "Failed" || state == "Rejected" || now - accepted.at > acceptTimeout.inWholeMilliseconds
            if (dead) lock.withLock { if (accepting[hash]?.transferId == accepted.transferId) accepting.remove(hash) }
        }
    }

    private suspend fun isOnline(agentId: String): Boolean =
        isOnline?.invoke(agentId) ?: (agentId in online || recentlySeen(agentId))

    /** A device the daemon heard from in the last two minutes counts as online even if no presence event was seen (the stream may have started after it). */
    private suspend fun recentlySeen(agentId: String): Boolean {
        val lastSeen = runCatching { client.getDiscoveredAgent(agentId)?.lastSeen }.getOrNull() ?: return false
        return nowMillis() - lastSeen * 1_000 < 2 * 60 * 1_000
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Everything here is retried by the next round.
        }
    }
}
