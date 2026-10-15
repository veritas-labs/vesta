package uk.veritaslabs.vesta.automerge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.automerge.Document
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar
import uk.veritaslabs.vesta.docs.AutomergeDocumentCore
import uk.veritaslabs.vesta.docs.HomeDocument
import uk.veritaslabs.vesta.ui.screens.CalendarSource

/**
 * A [CalendarSource] backed by a calendar Automerge [document] (ADR-029): a Home's ([EventCalendar.Home]) or the user's
 * own ([EventCalendar.Private]). Same threading contract as [MealPlanDocumentSource]: route every read or write of
 * [document] through this class.
 */
class CalendarDocumentSource private constructor(
    private val core: AutomergeDocumentCore,
    override val calendar: EventCalendar,
    override val events: Flow<List<CalendarEvent>>,
) : CalendarSource, HomeDocument by core {

    constructor(
        document: Document,
        calendar: EventCalendar,
        persist: suspend (ByteArray) -> Unit = {},
        onLocalChange: () -> Unit = {},
    ) : this(MutableStateFlow(document.readCalendarEvents(calendar)), document, calendar, persist, onLocalChange)

    private constructor(
        state: MutableStateFlow<List<CalendarEvent>>,
        document: Document,
        calendar: EventCalendar,
        persist: suspend (ByteArray) -> Unit,
        onLocalChange: () -> Unit,
    ) : this(
        AutomergeDocumentCore(document, { state.value = document.readCalendarEvents(calendar) }, persist, onLocalChange),
        calendar,
        state,
    )

    override suspend fun save(event: CalendarEvent) = edit { putCalendarEvent(this, event) }

    override suspend fun delete(id: String) = edit { deleteCalendarEvent(this, id) }

    /** Publishes and persists only when [change] reports it changed something, as the calendar's edits always did. */
    private suspend fun edit(change: Document.() -> Boolean) {
        core.editIfChanged(change)
    }

    /** Republishes and persists after the document changed outside [save]/[delete]. */
    suspend fun refresh() = core.refresh()
}
