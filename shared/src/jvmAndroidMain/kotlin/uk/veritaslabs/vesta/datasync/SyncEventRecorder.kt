package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase

/** The kinds of [SyncEventRecorder] event, as stored in `SyncEvent.kind`. */
internal object SyncEventKinds {
    const val Received = "received"
    const val Sent = "sent"
    const val Failed = "failed"
    const val Membership = "membership"
    const val MailboxIn = "mailbox_in"
    const val MailboxOut = "mailbox_out"

    /** Something arrived in a newer format, or for a document this version doesn't have (ADR-034). Kept, not lost: an update applies it. */
    const val UnsupportedVersion = "unsupported_version"

    /** A message of a family this version knows, with a `kind` it doesn't (ADR-034). */
    const val UnknownMessage = "unknown_message"
}

/** The modules of the sync machinery itself; the app's own are named by its document types. A [SyncEventRecorder] event can name either, as stored in `SyncEvent.module`. Never content, only which part moved. */
internal object SyncModules {
    const val Members = "members"

    /** Mailbox events belong to the Home as a whole rather than to one module. */
    const val Mailbox = "mailbox"
}

/**
 * Keeps the Sync activity log (docs/adr/ADR-033-sync-activity.md): a content-free, local record of what moved between this
 * device and others, per Home. Events are buffered in memory and written by [flush], which the sync service calls at the end
 * of each round. The same event (Home, module, peer, kind and detail) within five minutes of the last one is not a new row but a
 * higher `count` on it, so a busy hour is a handful of rows rather than thousands.
 *
 * Recording must never fail a sync: every method swallows its own failures (cancellation aside), and a failed [flush]
 * drops what it was holding.
 */
class SyncEventRecorder(
    private val database: VestaDatabase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private class Pending(
        val homeId: String,
        val module: String,
        val peer: String?,
        val kind: String,
        val at: Long,
        var lastAt: Long,
        val detail: String?,
        var count: Long,
    )

    private val lock = Mutex()
    private val buffer = mutableListOf<Pending>()
    private val lastSyncedInMemory = mutableMapOf<String, Long>()
    private val lastSyncedWritten = mutableMapOf<String, Long>()

    /** [peer] sent us changes to [module] of [homeId]. */
    suspend fun received(homeId: String, module: String, peer: String) =
        record(homeId, module, peer, SyncEventKinds.Received, null)

    /** We sent [peer] changes to [module] of [homeId]. */
    suspend fun sent(homeId: String, module: String, peer: String) =
        record(homeId, module, peer, SyncEventKinds.Sent, null)

    /** Syncing [module] of [homeId] with [peer] failed; [detail] says why, in words that name no content. */
    suspend fun failed(homeId: String, module: String, peer: String, detail: String) =
        record(homeId, module, peer, SyncEventKinds.Failed, detail)

    /** Who belongs to [homeId] changed in a way involving [peer]; [detail] says how. */
    suspend fun membership(homeId: String, peer: String, detail: String) =
        record(homeId, SyncModules.Members, peer, SyncEventKinds.Membership, detail)

    /** Changes passed through [homeId]'s mailbox, [incoming] or outgoing; [peer] is the other device when known. */
    suspend fun mailbox(homeId: String, incoming: Boolean, peer: String?) = record(
        homeId, SyncModules.Mailbox, peer, if (incoming) SyncEventKinds.MailboxIn else SyncEventKinds.MailboxOut, null,
    )

    /**
     * [peer] sent something in a format newer than this version reads, for [module] of [homeId]; [detail] is the version or the
     * name of the document type, never content. Repeats of the same thing fold into one row.
     */
    suspend fun unsupportedVersion(homeId: String, module: String, peer: String?, detail: String) =
        record(homeId, module, peer, SyncEventKinds.UnsupportedVersion, safeDetail(detail))

    /** [peer] sent a message of a family this version knows, with a `kind` it doesn't; [detail] is that kind. */
    suspend fun unknownMessage(homeId: String, peer: String?, detail: String) =
        record(homeId, SyncModules.Members, peer, SyncEventKinds.UnknownMessage, safeDetail(detail))

    /**
     * Notes that this device synced with [peer] just now. The time is kept in memory at once, and written to the table
     * at most once a minute per peer, so a chatty peer doesn't turn every message into a write.
     */
    suspend fun lastSynced(peer: String) {
        try {
            val now = nowMillis()
            val write = lock.withLock {
                lastSyncedInMemory[peer] = now
                val written = lastSyncedWritten[peer]
                (written == null || now - written >= PeerSyncWriteIntervalMillis).also { if (it) lastSyncedWritten[peer] = now }
            }
            if (write) database.peerSyncQueries.upsert(peer, now)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Recording is best effort.
        }
    }

    /** When this device last synced with [peer]: the newest this run has seen, else what an earlier run wrote down. */
    suspend fun lastSyncedAt(peer: String): Long? = try {
        lock.withLock { lastSyncedInMemory[peer] }
            ?: database.peerSyncQueries.selectByAgent(peer).awaitAsOneOrNull()?.lastSyncedAt
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Writes the buffered events: folded into a recent row for the same thing when there is one, as a new row otherwise. */
    suspend fun flush() {
        try {
            val events = lock.withLock { buffer.toList().also { buffer.clear() } }
            events.forEach { event -> write(event) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The events that were being written are dropped; the log is a courtesy, not a record of truth.
        }
    }

    /** Writes one event; a failure (a Home deleted since, say) drops only that event, not the rest of the batch. */
    private suspend fun write(event: Pending) {
        try {
            val open = database.syncEventQueries.findOpen(
                homeId = event.homeId,
                module = event.module,
                peerAgent = event.peer,
                kind = event.kind,
                detail = event.detail,
                since = event.at - CollapseWindowMillis,
            ).awaitAsOneOrNull()
            if (open == null) {
                database.syncEventQueries.insert(
                    event.homeId, event.at, event.lastAt, event.kind, event.module, event.peer, event.detail, event.count,
                )
            } else {
                database.syncEventQueries.bump(maxOf(open.lastAt, event.lastAt), open.count + event.count, open.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // This event is dropped; the log is a courtesy, not a record of truth.
        }
    }

    /** Deletes events whose latest occurrence is more than a week before [now]. */
    suspend fun prune(now: Long) {
        try {
            database.syncEventQueries.pruneOlderThan(now - RetentionMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Pruned next time.
        }
    }

    /** Forgets every event of [homeId], buffered or written, for when the Home is deleted or left. */
    suspend fun forgetHome(homeId: String) {
        try {
            lock.withLock { buffer.removeAll { it.homeId == homeId } }
            database.syncEventQueries.deleteForHome(homeId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing to do: the rows go with the Home anyway (ON DELETE CASCADE).
        }
    }

    private suspend fun record(homeId: String, module: String, peer: String?, kind: String, detail: String?) {
        try {
            val now = nowMillis()
            lock.withLock {
                val same = buffer.lastOrNull {
                    it.homeId == homeId && it.module == module && it.peer == peer && it.kind == kind && it.detail == detail &&
                        now - it.lastAt < CollapseWindowMillis
                }
                if (same == null) {
                    buffer.add(Pending(homeId, module, peer, kind, now, now, detail, 1))
                } else {
                    same.lastAt = now
                    same.count++
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Recording is best effort.
        }
    }

    private companion object {
        val CollapseWindowMillis = 5.minutes.inWholeMilliseconds
        val PeerSyncWriteIntervalMillis = 1.minutes.inWholeMilliseconds
        val RetentionMillis = 7.days.inWholeMilliseconds
    }
}

private const val MaxDetailLength = 64

/**
 * [text] as a log detail: text a remote device chose (a message kind, a document type) cut to a short length and to plain
 * identifier characters, so it can never carry content or markup into the log.
 */
internal fun safeDetail(text: String): String =
    text.take(MaxDetailLength).map { if ((it.isLetterOrDigit() && it.code < 128) || it in "._-:") it else '_' }.joinToString("")
