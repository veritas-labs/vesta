package uk.veritaslabs.vesta.automerge

import kotlin.time.Clock
import kotlinx.coroutines.flow.SharedFlow
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.vesta.docs.HomeDocumentStore
import uk.veritaslabs.vesta.docs.homeDocumentId

private const val CalendarFeature = "calendar"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s calendar document. */
fun calendarDocumentId(homeId: String): String = homeDocumentId(homeId, CalendarFeature)

/**
 * Id of the user's private calendar in [homeId]: kept per Home, but unlike the Home's own documents it only ever syncs between the
 * user's paired devices (ADR-029). It must not end in `:calendar`, which would make it the Home's shared calendar.
 */
fun privateCalendarDocumentId(homeId: String): String = homeDocumentId(homeId, PrivateCalendarFeature)

private const val PrivateCalendarFeature = "private-calendar"

/** One shared [CalendarDocumentSource] per Home (see [PantrySources] for why there must be exactly one), and one private one per Home. */
class CalendarSources(
    database: KitsDatabase,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val shared = HomeDocumentStore(
        database, CalendarFeature, nowMillis,
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> CalendarDocumentSource(document, EventCalendar.Home, persist, changed) },
    )
    private val personal = HomeDocumentStore(
        database, PrivateCalendarFeature, nowMillis,
        refreshNew = { it.refresh() },
        create = { _, document, persist, changed -> CalendarDocumentSource(document, EventCalendar.Private, persist, changed) },
    )

    /** The id of a Home each time its calendar is edited on this device (not when a sync brings in a change). */
    val localChanges: SharedFlow<String> = shared.localChanges

    /** The id of a Home each time the user's private calendar in it is edited on this device, to be pushed to their other devices. */
    val privateChanges: SharedFlow<String> = personal.localChanges

    /** [homeId]'s calendar source, opened on first use; null if there is no such Home. */
    suspend fun forHome(homeId: String): CalendarDocumentSource? = shared.forHome(homeId)

    /** The user's private calendar in [homeId], opened on first use; null if there is no such Home. */
    suspend fun privateForHome(homeId: String): CalendarDocumentSource? = personal.forHome(homeId)
}
