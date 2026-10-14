package uk.veritaslabs.vesta.ui.flows

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.CalendarEvent
import uk.veritaslabs.vesta.calendar.today
import uk.veritaslabs.vesta.ui.screens.CalendarEventEditScreen
import uk.veritaslabs.vesta.ui.screens.CalendarScreen
import uk.veritaslabs.vesta.ui.screens.CalendarViewModel

/** What the event form is open on: [event] to edit it, or null to add one on [date]. */
private data class EventTarget(val event: CalendarEvent?, val date: CalendarDate)

/**
 * The calendar (ADR-029) and the event form over it. The plus button opens the form for the selected day and tapping an event opens
 * it for that event; saving or cancelling returns to the calendar on the day it was on, as the form is drawn over the calendar rather
 * than replacing it. [homeName] names the Home calendar in the form.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun CalendarFlow(
    viewModel: CalendarViewModel,
    homeName: String,
    modifier: Modifier = Modifier,
    today: CalendarDate = remember { CalendarDate.today() },
    onBack: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    var editing by remember { mutableStateOf<EventTarget?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
        CalendarScreen(
            today = today,
            events = state.events,
            onBack = onBack,
            onSettingsClick = onSettingsClick,
            onAddEvent = { date -> editing = EventTarget(null, date) },
            onEventClick = { event -> editing = EventTarget(event, today) },
        )
        editing?.let { target ->
            CalendarEventEditScreen(
                event = target.event,
                date = target.date,
                homeName = homeName,
                members = state.members,
                newId = { Uuid.random().toString() },
                onSave = { saved ->
                    viewModel.save(saved, movedFrom = target.event?.calendar)
                    editing = null
                },
                onDelete = { event ->
                    viewModel.delete(event)
                    editing = null
                },
                onCancel = { editing = null },
            )
        }
    }
}
