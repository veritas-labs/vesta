package uk.veritaslabs.vesta.automerge

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.db.VestaDatabase

private const val CalendarFeature = "calendar"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s calendar document. */
fun calendarDocumentId(homeId: String): String = "$homeId:$CalendarFeature"

/**
 * Id of the user's private calendar in [homeId]: kept per Home, but unlike the Home's own documents it only ever syncs between the
 * user's paired devices (ADR-029). It must not end in `:calendar`, which would make it the Home's shared calendar.
 */
fun privateCalendarDocumentId(homeId: String): String = "$homeId:$PrivateCalendarFeature"

private const val PrivateCalendarFeature = "private-calendar"

/** One shared [CalendarDocumentSource] per Home (see [PantrySources] for why there must be exactly one), and one private one per Home. */
class CalendarSources(
    private val database: VestaDatabase,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val lock = Mutex()
    private val sources = mutableMapOf<String, CalendarDocumentSource>()
    private val privateSources = mutableMapOf<String, CalendarDocumentSource>()
    private val _localChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Home each time its calendar is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = _localChanges.asSharedFlow()

    private val _privateChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Home each time the user's private calendar in it is edited on this device, to be pushed to their other devices. */
    val privateChanges: SharedFlow<String> = _privateChanges.asSharedFlow()

    /** [homeId]'s calendar source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): CalendarDocumentSource? = lock.withLock {
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) {
            sources.remove(homeId)
            return@withLock null
        }
        sources[homeId] ?: openHome(homeId).also { sources[homeId] = it }
    }

    /** The user's private calendar in [homeId], opened on first use; null if there is no such Home. */
    suspend fun privateForHome(homeId: String): CalendarDocumentSource? = lock.withLock {
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) {
            privateSources.remove(homeId)
            return@withLock null
        }
        privateSources[homeId] ?: openPrivate(homeId).also { privateSources[homeId] = it }
    }

    private suspend fun openHome(homeId: String): CalendarDocumentSource {
        val documentId = calendarDocumentId(homeId)
        val queries = database.automergeDocumentQueries
        val stored = queries.selectById(documentId).awaitAsOneOrNull()
        val source = CalendarDocumentSource(
            Document.load(stored?.bytes ?: Document().save()),
            EventCalendar.Home,
            persist = { bytes -> queries.upsert(documentId, homeId, CalendarFeature, bytes, nowMillis()) },
            onLocalChange = { _localChanges.tryEmit(homeId) },
        )
        if (stored == null) source.refresh()
        return source
    }

    private suspend fun openPrivate(homeId: String): CalendarDocumentSource {
        val documentId = privateCalendarDocumentId(homeId)
        val queries = database.automergeDocumentQueries
        val stored = queries.selectById(documentId).awaitAsOneOrNull()
        val source = CalendarDocumentSource(
            Document.load(stored?.bytes ?: Document().save()),
            EventCalendar.Private,
            persist = { bytes -> queries.upsert(documentId, homeId, PrivateCalendarFeature, bytes, nowMillis()) },
            onLocalChange = { _privateChanges.tryEmit(homeId) },
        )
        if (stored == null) source.refresh()
        return source
    }
}
