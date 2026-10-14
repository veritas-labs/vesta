# ADR-029: Calendar events, and a private calendar

**Status:** Implemented (2026-10-14); UI never run in the real app, only under Compose UI tests. Builds on ADR-025 (the month grid).

## Context

ADR-025 gave a Home a Calendar screen that could only show days. People need to put things on it: a dentist, bin day, a
week away. The form is modelled on Proton Calendar's (without its notifications section, and without e-mail invitations,
conferencing or external calendars, which stay out of scope per NON-GOALS "Full Calendar / Scheduling Platform").

A person also wants a calendar the rest of the Home never sees: a private one.

## Decisions

### Two calendars

- **Home calendar:** a sixth document per Home, `documentId = "<homeId>:calendar"` (ADR-006's naming), synced and mailboxed
  exactly like the Meal Planner's (ADR-013, ADR-020). Every member reads and edits it.
- **Private calendar:** one per person *per Home*, `documentId = "<homeId>:private-calendar"`, stored in the `AutomergeDocument`
  table under that Home (feature `private-calendar`). It is synced **only between the user's own paired devices**: `homeIdOf` does
  not recognise its id as a Home document, so no Home member is a peer, and it is never deposited in the Home mailbox. It shows only in
  that Home's Calendar (on the user's devices), with a padlock, and a different Home has its own. `DeviceDataSyncServiceTest` proves a
  Home member's device never receives a message for it. (First built as one calendar per person across all Homes; changed to per Home
  the same day.)
- It is not end-to-end hidden from the user's own *paired* devices, by design: pairing means "the same person".
- The id deliberately does not end in `:calendar`, which `homeIdOf` would read as the Home's shared calendar.

### Document shape

```
ROOT
└─ "<event id (UUID)>": Map
     title, location, description: Str
     start, end: Str      -- UTC milliseconds, decimal; for an all-day event allDayMillis(first day) and allDayMillis(last day)
     allDay: Bool
     repeat: Str          -- None | Daily | Weekly | Monthly | Yearly
     color: Str           -- index 0..7 into the palette
     participants: Str    -- Home member refs ("m:<member id>") joined by newlines; always empty on the private calendar
```

- **One root map per event, under its UUID**, the registry shape (ADR-013). No wrapping "events" map for two devices to create at
  once, and two devices adding events never collide. Each field is its own key, so two devices editing different fields of one
  event both win; the same field is last-writer-wins.
- An entry with no readable start and end is skipped, not an error. Colours outside the palette are clamped, unknown repeats read as None.
- **Deleting while another device edits** the same event can leave the editor's write dropped on merge; accepted, as elsewhere.

### Time

- **Timed events are two UTC instants**, shown in each device's own time zone, so a 09:00 event made in London reads 10:00 for a
  device in Paris. **All-day events have no time zone**: their two values are UTC midnight of the first and last day (the last
  day is included), so a holiday on the 14th is the 14th everywhere.
- The device zone comes from a one-function platform hook, `deviceOffsetMillis(utcMillis)` (`java.util.TimeZone` on JVM/Android,
  `NSTimeZone` on iOS), instead of adding `kotlinx-datetime`. `CalendarDate.today()` uses it, so "today" is now the device's
  date for the Calendar and the Meal Planner (ADR-025/028 used the UTC date). `localMoment` / `utcMillisOf` convert; the form
  edits wall-clock dates and minutes (`EventDraft`) and converts on save. There is no per-event time zone picker: Proton's "Show time zones" is not built.
- An event ending exactly at midnight ends the previous day; it does not spill onto the next.

### Repeating

`EventRepeat` is None, Daily, Weekly, Monthly or Yearly, counted from the event's first (local) day, with no end date and no
exceptions. There are no stored occurrences: `eventsOn(events, date)` works out, for each event, whether any run of it (its first
day, or a repeat of it, plus the days it spans) covers the date. Monthly keeps the day of the month, so an event on the 31st
skips months without one; yearly on 29 February skips non-leap years. A repeating event keeps its wall-clock time across daylight saving changes.
Editing or deleting a repeating event changes **all** its occurrences; "this one only" and "from here on" are not offered.

### The form

`CalendarEventEditScreen`, a full screen drawn over the calendar by `CalendarFlow` (so the calendar keeps its selected day and
month behind it). From top: title; start and end, each a date and a time (the time hidden when All day); All day; Repeat
(menu); calendar (menu: "<Home> calendar" or "My private calendar") and colour (menu of eight); participants (Home calendar
only: a dialog of the Home's members, chosen ones shown as removable chips) or, on the private calendar, a note that only your own
devices see it; location; description. Save is enabled once there is a title and the end is not before the start; the form
says so otherwise. Editing adds a delete button. Moving the start moves the end with it, keeping the length. Changing the
calendar moves the colour to the new calendar's default unless one was chosen (Home blue, private purple). Dates use Material's
date picker, times its 24-hour time picker.

Changing an event's calendar saves it on the new calendar and deletes it from the old (`CalendarViewModel.save(movedFrom)`),
two writes in two documents, not atomic: a crash between them could leave it on both. Its id is kept, so an event on both would show twice;
accepted. A private event's participants are dropped on the way, since they name Home members.

### Export as iCal

Opening an existing event shows an overflow menu (three dots) in the form's app bar with one item, **Export as iCal event**. It offers
the event as `<title>.ics` through the system's save dialog: a `FileExporter` (`LocalFileExporter`, provided by `VestaApp`, like the
recipe importer) implemented with `FileDialog` on desktop and the "create document" screen on Android. Where there is no exporter
(previews, iOS) or the event is new and unsaved, the menu is not shown. It exports what is on the form if that is a valid event, so edits
not yet saved are included. `CalendarEvent.toIcal()` writes RFC 5545: one VEVENT, UID `<event id>@vesta` (re-importing updates rather than
duplicates), UTC `DTSTART`/`DTEND` for timed events, date values for all-day ones (end the day after the last), an `RRULE` with no end for a
repeat, escaped and 75-octet-folded text, CRLF lines. Participants are not written (Home members have no e-mail address). A repeating
timed event is anchored to UTC, so across a daylight saving change the importing calendar may show it an hour off the wall-clock time Vesta keeps.

### The calendar screen

A plus button (bottom end) opens the form for the selected day. Under the grid, the selected day's events are a list: a bar in
the event's colour, the title, when it is ("09:00 – 09:30", "From 22:00", "Until 10:00", "All day"), and a padlock for a private
one; tapping one opens it. In the grid a day with events has up to three dots under its number, one per distinct colour.

## Acceptance criteria

See `CalendarEventsAcceptanceTest`, `DeviceDataSyncServiceTest` (Home calendar reaches the Home's members; private calendar
reaches the user's paired devices and never another member; edits sync), `CalendarDocumentSourceTest`, `CalendarEventTest`
(occurrences, repeats, time zones), `EventDraftTest`, `CalendarEventEditScreenTest` and `CalendarScreenTest`.

## Not yet

Notifications and reminders (the ADR-025 aim; Proton's section was left out on purpose), invitations and replies, an end date or
exceptions for repeats, editing one occurrence, a per-event time zone, week and agenda views, an agent command to add events
(ADR-009's route), per-calendar visibility toggles, and a shared calendar a person makes for some members only. The UI has
not been run in the real app, on Android, or against a live x0x daemon; the iOS time-zone actual is not compiled on this machine.
