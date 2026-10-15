package uk.veritaslabs.vesta.automerge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.mailbox.changesSince
import uk.veritaslabs.vesta.mailbox.encodeHeads
import uk.veritaslabs.vesta.ui.screens.CalendarSource

/**
 * A [CalendarSource] backed by a calendar Automerge [document] (ADR-029): a Home's ([EventCalendar.Home]) or the user's
 * own ([EventCalendar.Private]). Same threading contract as [MealPlanDocumentSource]: route every read or write of
 * [document] through this class.
 */
class CalendarDocumentSource(
    private val document: Document,
    override val calendar: EventCalendar,
    private val persist: suspend (ByteArray) -> Unit = {},
    private val onLocalChange: () -> Unit = {},
) : CalendarSource {

    private val lock = Mutex()
    private val state = MutableStateFlow(document.readCalendarEvents(calendar))

    override val events: Flow<List<CalendarEvent>> = state

    override suspend fun save(event: CalendarEvent) = edit { putCalendarEvent(document, event) }

    override suspend fun delete(id: String) = edit { deleteCalendarEvent(document, id) }

    private suspend fun edit(change: () -> Boolean) {
        val changed = lock.withLock {
            change().also { if (it) publishAndPersist() }
        }
        if (changed) onLocalChange()
    }

    /** The next Automerge sync message for the peer whose progress is [state], or null when it is up to date. */
    suspend fun generateSync(state: SyncState): ByteArray? = lock.withLock {
        document.generateSyncMessage(state).orElse(null)
    }

    /** Applies a sync message from a peer, then republishes and persists the events if they changed. */
    suspend fun receiveSync(state: SyncState, message: ByteArray) = applyIncoming { receiveSyncMessage(state, message) }

    /** The document's current heads, saved as [encodeHeads] writes them (for the mailbox, ADR-020). */
    suspend fun heads(): ByteArray = lock.withLock { encodeHeads(document.heads) }

    /** True once the peer whose progress is [state] has replied with its heads and has everything this document has. */
    suspend fun isInSync(state: SyncState): Boolean = lock.withLock { state.isInSync(document) }

    /** The changes made since the heads [saved] by [heads] (everything, for none), for another device's [applyChanges]. */
    suspend fun changesSince(saved: ByteArray): ByteArray = lock.withLock { document.changesSince(saved) }

    /** Applies changes from [changesSince], as [receiveSync] does a sync message. Applying one again changes nothing. */
    suspend fun applyChanges(changes: ByteArray) = applyIncoming { applyEncodedChanges(changes) }

    private suspend fun applyIncoming(apply: Document.() -> Unit) {
        lock.withLock {
            val before = document.heads
            document.apply()
            if (!document.heads.contentEquals(before)) publishAndPersist()
        }
    }

    /** Republishes and persists after [document] changed outside [save]/[delete]. */
    suspend fun refresh() = lock.withLock { publishAndPersist() }

    private suspend fun publishAndPersist() {
        state.value = document.readCalendarEvents(calendar)
        persist(document.save())
    }
}
