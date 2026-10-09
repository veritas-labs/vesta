package uk.veritaslabs.vesta.calendar

import androidx.compose.runtime.compositionLocalOf

/** How this device draws the calendar. Kept per device, not synced. */
data class CalendarSettings(
    val weekStart: WeekStart = WeekStart.Monday,
    val showWeekNumbers: Boolean = false,
    val showOtherMonthDays: Boolean = true,
    val startCollapsed: Boolean = false,
)

/** The current [settings] and how to change them; the app provides one that saves. */
class CalendarSettingsController(val settings: CalendarSettings, val onChange: (CalendarSettings) -> Unit)

val LocalCalendarSettings = compositionLocalOf { CalendarSettingsController(CalendarSettings()) {} }
