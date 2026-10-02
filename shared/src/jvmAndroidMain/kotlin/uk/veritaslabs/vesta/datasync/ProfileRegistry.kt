package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.vesta.automerge.ProfileDocumentId
import uk.veritaslabs.vesta.automerge.canonical
import uk.veritaslabs.vesta.automerge.putProfile
import uk.veritaslabs.vesta.automerge.readProfiles
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.profile.CurrentProfile
import uk.veritaslabs.vesta.profile.ProfileRepository

/**
 * The user's Profile as an Automerge document that syncs between their devices, so that all of them are one member of
 * every Home (docs/adr/ADR-014-home-membership.md). The `Profile` table stays what the app reads; [publish] writes
 * this device's Profile into the document and [project] makes the device take on the one the document settles on.
 */
internal class ProfileRegistry(
    private val database: VestaDatabase,
    private val profiles: ProfileRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SyncableDocument {

    private val lock = Mutex()
    private var document: Document? = null
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after each change made on this device, so it can be pushed to the other devices. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** Writes this device's Profile into the document. A no-op when it is already there as it is. */
    suspend fun publish() {
        val profile = profiles.current()
        val changed = lock.withLock {
            val doc = open()
            putProfile(doc, profile.id, profile.displayName, profile.avatarResourceId, nowMillis(), profile.named)
                .also { if (it) persist(doc) }
        }
        if (changed) _changes.tryEmit(Unit)
    }

    override suspend fun generate(state: SyncState): ByteArray? = lock.withLock { open().generateSyncMessage(state).orElse(null) }

    override suspend fun receive(state: SyncState, message: ByteArray) = lock.withLock {
        val doc = open()
        val before = doc.heads
        doc.receiveSyncMessage(state, message)
        if (!doc.heads.contentEquals(before)) persist(doc)
    }

    /** Makes this device's Profile the one the document settles on, if it isn't already. Returns the id it replaced, if any. */
    suspend fun project(): String? {
        val settled = lock.withLock { open().readProfiles().canonical() } ?: return null
        return profiles.adopt(CurrentProfile(settled.id, settled.name, settled.avatar, settled.named))
    }

    private suspend fun open(): Document = document ?: run {
        val stored = database.userDocumentQueries.selectById(ProfileDocumentId).awaitAsOneOrNull()
        (stored?.let { Document.load(it.bytes) } ?: Document()).also { document = it }
    }

    private suspend fun persist(doc: Document) {
        database.userDocumentQueries.upsert(ProfileDocumentId, doc.save(), nowMillis())
    }
}
