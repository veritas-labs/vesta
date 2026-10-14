# ADR-031: Tasks and chores — one task document per Home

**Status:** Implemented (2026-10-14); UI never run in the real app or on Android, only under Compose UI tests and the JVM sync tests. Reminders are in ADR-032, not built.

## Context

REQUIREMENTS FR-10..FR-15 ask for tasks with an assignee and due date, recurring chores, completion by the assignee or
anyone, an outstanding/overdue view and a history of who did what. The Meal Planner (ADR-028) and Calendar (ADR-029) already
solved most of the sub-problems: a week of day cards, people tagged on an item, recurrence worked out rather than stored, and a
private per-person document.

A task differs from a planned meal or an event in one way that shapes everything: **it has state**. A meal just exists. A task
is open or done, and a recurring chore is done *this time*, not once for ever.

There is one model, not three features. A **chore** is a task with a repeat rule. A **reminder** (ADR-032) is a task with a
notify time. Separate screens and documents for each would duplicate data and sync code.

## Decision

### Documents

- **Home tasks:** a seventh document per Home, `documentId = "<homeId>:tasks"` (ADR-006's naming), synced and mailboxed
  exactly like the Meal Planner's and Calendar's (ADR-013, ADR-020). Every member reads and edits it.
- **Private tasks:** `documentId = "<homeId>:private-tasks"`, one per person per Home, synced only between the user's own
  paired devices, exactly as ADR-029's private calendar (not a Home document to `homeIdOf`, never mailboxed; the id must not
  end in `:tasks`). Assignees are always empty there.

### Document shape

```
ROOT
├─ "<task id (UUID)>": Map
│    title, description: Str
│    due: Str          -- yyyy-MM-dd, or absent for "anytime"
│    time: Str         -- minutes after local midnight, decimal, or absent; a due time only, no notifying (ADR-032)
│    repeat: Str       -- None | Daily | Weekly | Monthly | Yearly
│    every: Str        -- interval, decimal, default 1 ("every 2 weeks"); ignored when repeat is None
│    assignees: Str    -- Home member refs ("m:<member id>") joined by newlines; empty = anyone
│    created: Timestamp, createdBy: Str
└─ "done|<task id>|<yyyy-MM-dd or anytime>": Map
     at: Str              -- UTC milliseconds, decimal, when it was ticked
     by: Str              -- the member who ticked it ("m:<member id>")
```

- **One root map per task** (the registry shape, ADR-013): two devices adding tasks never collide, and each field is its own
  key so edits to different fields both win; the same field is last-writer-wins.
- **Completion is a separate flat key per occurrence**, not a field on the task. A one-off task is done when
  `done|<id>|<due>` exists (for an "anytime" task the date is the literal `anytime`). A repeating task is done for an
  occurrence when that occurrence's date has a key. Two devices ticking the same occurrence write the same key, so it merges to
  one; ticking different occurrences adds two keys. Un-ticking deletes the key; a concurrent tick and untick can resolve either
  way, accepted as elsewhere.
- **The history (FR-15) is the `done|` keys**: who, when, which occurrence. Nothing extra is stored. Deleting a task leaves its
  `done|` keys, which are then ignored (as ADR-028 leaves a removed recipe's eaters); a periodic clean-up is a later concern.
- An entry with no readable title is skipped, not an error. Unknown repeats read as None; an `every` below 1 reads as 1.

### Occurrences

Reuse ADR-029's engine, do not write a second one. `EventRepeat.recursOn` moves to a shared `recurrence` file and gains the
interval: Daily and Weekly count whole days or weeks from the first date divisible by `every`; Monthly and Yearly count whole
months or years. The Calendar's events pass `every = 1` and behave as before. Occurrences are never stored:
`tasksOn(tasks, date)` works out which tasks fall on a date, and each result carries its done state from the `done|` keys.

- **"After I last did it" ("N days after last done") is not in v1.** It makes the next due date depend on completion, which
  needs a different rule (a stored "next due"), and the fixed schedule covers most chores. Deferred, with rotation.
- A repeating task with no due date is not allowed: the form asks for a first date when a repeat is chosen.
- Editing or deleting a repeating task changes all its occurrences (as ADR-029).
- **Overdue** means an occurrence on a past date with no `done|` key. To stay finite, a repeating task is overdue for at most
  its **latest** missed occurrence, not every one back to its first date; older missed ones are not listed (they are still
  absent from the history, which is honest). A one-off task stays overdue until done.

### Assignees and who may tick

- Assignees are Home members, chosen on the form like the calendar's participants (dialog of members, removable chips). **None
  chosen means anyone** — the default, and a later joiner is not left out. Several assignees all share the task: it is one task
  they can each see, not one each. A ref for a departed member is ignored.
- **Anyone in the Home can tick any task**, recording who did it (FR-12 allows assignee or admin; the household is trusted, and
  "I did your bins" is common). The `by` shows in the history, and an assigned task done by someone else says so.
- Rotation (FR-11's second half) is not in v1; see "Not yet".

### Screens

Reached from a **Tasks tile** on the Home dashboard; a header with a segmented switch between the two views, and a plus.

- **Week view**, in the Meal Planner's layout (ADR-028): seven day cards in a list, at most 720 dp wide, on the Calendar's first
  day of the week, with the same previous/next/"This week" header. Each card has the day's coloured bar, its name and date
  ("Today"), and a line such as "3 tasks, 1 done" or "Nothing to do". Under it, each task due that day is a row: a round tick
  circle (the tick toggles at once), the title, the time if any, the assignees' names or "Anyone", and a repeat icon. Done
  tasks are struck through and sink to the bottom. Tapping a row opens a bottom sheet: **Edit**, **Delete**, and for a repeating
  task a note that these change every occurrence.
- **List view**, in the shopping list's style: sections **Overdue**, **Today**, **Upcoming** (next seven days), **Anytime**, and
  **Done recently** (last seven days, newest first, "by <name>, <when>"). A filter chip, **Mine** / **Everyone**, narrows to tasks
  assigned to me or to anyone. An empty list uses `EmptyState`.
- **Form:** a full screen drawn over the tasks screen (as `CalendarEventEditScreen`): title, description, due date (optional;
  clearing it makes the task "anytime"), time (optional), repeat (menu) with "every N" (shown once a repeat is chosen), assignees,
  and a list choice (Home or "My private tasks"). Save is enabled once there is a title (and a date if it repeats). Changing the
  list moves the task, two writes in two documents, not atomic (as ADR-029).
- The Home dashboard tile shows a count: "2 overdue" in the error colour, else "3 today", else nothing (FR-14's dashboard).

### Not on the Calendar

Tasks do not appear on the Calendar grid in v1. Merging two documents' occurrences into one view couples them, and the Calendar's
NON-GOAL is not to be a scheduling platform. A later ADR can offer an opt-in "show tasks" toggle that reads the tasks document.

## As built (2026-10-14)

Where the build settled something this ADR left open:

- **The plus.** The bar's plus adds a task due *today* in the Week view and one for *whenever* in the List view; each day card also has
  its own plus that adds a task for that day. Choosing a repeat on a task with no date starts it today.
- **Assignees are shown as names** ("Sam, Alex"), not initials. The Mine / Everyone chips only appear once the user's own member is known.
- **Done records store `at` as a decimal string**, like the calendar's times, not an Automerge timestamp.
- **Upcoming** is the seven days after today; **Done recently** is the last seven days by when it was ticked, and a tick there un-ticks it.
- **The tile** shows "N overdue" (error colour) or "N due today" (open ones only) in place of its usual line.
- **Code:** `tasks/Task.kt` (model, `tasksOn`, `overdue`, `upcoming`, `anytime`, `recentlyDone`), `calendar/Recurrence.kt` (the shared engine),
  `TaskDraft`, `TaskSource` (in `tasks/`, not the UI package), `TaskDocument`/`TaskDocumentSource`/`TaskSources` (Automerge),
  `TasksViewModel`, `TasksScreen`, `TaskEditScreen`, `TasksFlow`. The form reuses the calendar form's menu, picker and row helpers.
- **Sync:** `:tasks` joins the mailbox and the paired-device and Home-member sync; `:private-tasks` is paired devices only.
  `AgentCommandService.taskSources` hands UI and sync the same instance.
- **Not run in the real app**, on Android (it compiles), or against a live x0x daemon.

## Acceptance criteria

1. The dashboard opens Tasks, with the week and list views.
2. I can add a task with a title only (anytime), or with a due date, time, assignees and a repeat.
3. A task due on a day shows on that day's card; a daily/weekly/monthly/yearly task with "every N" shows on exactly the right days.
4. I can tick a task done and untick it; a repeating chore ticked for Tuesday is still due on the next occurrence.
5. A task assigned to some members shows their names; one with none shows "Anyone"; any member can tick it, and the history says who.
6. Overdue tasks appear first in the list view, and the dashboard tile counts them.
7. A task on the private list is never seen by another member and reaches only my paired devices (as `DeviceDataSyncServiceTest` proves for the calendar).
8. Tasks and their completions survive restart and reach the Home's other devices; two devices ticking the same occurrence merge to one.

Tests to write: `TaskRecurrenceTest` (occurrences, intervals, month-end, overdue), `TaskDocumentSourceTest`,
`TasksAcceptanceTest` (Given/When/Then, docs/testing/acceptance-tests.md), `TaskEditScreenTest`, `TasksScreenTest`, and the
extended `DeviceDataSyncServiceTest` cases for both documents. Previews follow the light/dark x phone/tablet matrix.

## Not yet

Reminders and notifications (ADR-032: a scheduler per platform, each device scheduling its own, a reminder for someone else's task
firing on their device after it syncs); rotation and "after last done" chores; an end date, exceptions, "this one only"; subtasks;
a points or rewards layer (ROADMAP-V1's gamification candidate); tasks that reference a shopping list item (ROADMAP Phase 2); tasks
on the Calendar; an agent command to add a task (ADR-009's route); clean-up of orphaned `done|` keys. **FR-11's rotation is a Must
in REQUIREMENTS and is deferred by this ADR**; REQUIREMENTS should be amended or a rotation ADR follow soon after.
