package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.home.MemberListItem
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.EventCalendar

private const val StopTimeoutMillis = 5_000L

/** Where [CalendarViewModel] gets one calendar's events from, and saves them to; implemented over a calendar Automerge document (ADR-029). */
interface CalendarSource {
    /** Which calendar this is. */
    val calendar: EventCalendar

    val events: Flow<List<CalendarEvent>>

    /** Adds [event], or replaces the one with its id. */
    suspend fun save(event: CalendarEvent)

    /** Removes the event with [id]. No-op if there is none. */
    suspend fun delete(id: String)
}

/** [CalendarFlow]'s state; [isLoading] is true until both calendars have emitted once. */
data class CalendarUiState(
    /** The events of the Home's calendar and of the user's private one, each tagged with the calendar it is on. */
    val events: List<CalendarEvent> = emptyList(),
    /** The Home's members, for choosing participants. */
    val members: List<MemberListItem> = emptyList(),
    val isLoading: Boolean = true,
)

/** Backs the calendar: the Home's [home] calendar and the user's [private] one shown together, with edits sent to the one an event is on. */
class CalendarViewModel(
    private val home: CalendarSource,
    private val private: CalendarSource,
    members: Flow<List<MemberListItem>> = flowOf(emptyList()),
) : ViewModel() {

    val uiState: StateFlow<CalendarUiState> = combine(home.events, private.events, members) { shared, own, homeMembers ->
        CalendarUiState(events = shared + own, members = homeMembers, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), CalendarUiState())

    private fun source(calendar: EventCalendar) = if (calendar == EventCalendar.Home) home else private

    /**
     * Saves [event] on its calendar. When it was [movedFrom] another calendar it is taken off that one first. A private event has no
     * participants: they are the Home's members, and the Home never sees it.
     */
    fun save(event: CalendarEvent, movedFrom: EventCalendar? = null) {
        val toSave = if (event.calendar == EventCalendar.Private) event.copy(participants = emptySet()) else event
        viewModelScope.launch {
            movedFrom?.takeIf { it != event.calendar }?.let { source(it).delete(event.id) }
            source(event.calendar).save(toSave)
        }
    }

    /** Saves [events], read from a file, on [calendar]. One with the id of an event already there replaces it. */
    fun import(calendar: EventCalendar, events: List<CalendarEvent>) {
        viewModelScope.launch {
            events.forEach { source(calendar).save(it.copy(calendar = calendar, participants = emptySet())) }
        }
    }

    fun delete(event: CalendarEvent) {
        viewModelScope.launch { source(event.calendar).delete(event.id) }
    }
}
