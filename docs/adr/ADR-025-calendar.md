# ADR-025: Calendar

**Status:** Accepted, first slice built (2026-10-09). View only. Events, the local time zone and the private calendar followed in ADR-029.

## Context

A Home gets a Calendar, reached from a tile on the dashboard beside Pantry, Shopping and Cookbook. This slice is the
screen and its navigation; events (and where they live: a Home document synced like the others) come later.

## Decisions

- **App bar:** back arrow, and the visible month over its year, stacked and centred. Tapping them opens a dialog with a
  year stepper and the twelve months; choosing a month applies it and closes the dialog.
- **Month grid:** at the top, square (a header row plus six week rows of seven), Monday first, always six rows so the
  size doesn't jump between months. Days from neighbouring months are dimmed; tapping one moves to that month. Swiping
  sideways changes month. Previous / next buttons either side of the handle (and swiping) step a month, or a week when collapsed. The handle beneath collapses the grid to the week of the selected day and expands it again.
- **Below the grid:** the selected day and "No events".
- **Dates:** a small `CalendarDate` (`calendar/CalendarDate.kt`) with the civil-date arithmetic, rather than a new
  dependency. "Today" was first taken by the UTC clock; ADR-029 replaced that with the device's time zone (`CalendarDate.today()`, over a
  small platform hook rather than `kotlinx-datetime`). The screen still takes `today` as a parameter.
- **Settings:** the app bar's overflow menu has Settings, a screen of per-device preferences (saved in `AppSettingsRepository`,
  not synced): Week starts on (Monday, Saturday or Sunday; default Monday), Show week numbers (ISO, shown as w40; off), Show days from
  other months (on) and Open with the month collapsed (off). They reach the screens through `LocalCalendarSettings`.

## Acceptance criteria

See `CalendarAcceptanceTest`: tile opens the calendar and back returns; month over year in the app bar, opening on
today; the picker dialog changes month and year; the grid collapses to a week and expands again.

## Not yet

Reminders, week/agenda views (events, sync and the time zone: ADR-029).
