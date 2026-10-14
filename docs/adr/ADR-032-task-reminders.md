# ADR-032: Task reminders — local notifications, planned by each device

**Status:** Proposed (2026-10-14); not implemented. Builds on ADR-031 (tasks and chores).

## Context

A task with a due time is only useful if it is noticed. ADR-031 gave tasks a date, a time, a repeat and assignees but no way to
tell anyone. Calendar events (ADR-029) have none either; Proton's reminder section was left out on purpose.

**This ADR reverses a product decision.** NON-GOALS ("No notifications (v1)"), REQUIREMENTS (the v1 scope note, the NFR line and the
open question about surfacing assignments offline), and CORE-EXPERIENCE (the out-of-scope table) all lock notifications out of v1. The
owner has asked for reminders, so those lines must be amended when this is accepted (the ROADMAP-V1 Phase 1 line "basic notifications
(assignment, due date)" already expects them). The reversal is narrow: **local notifications only**, with no push service, no server and no
account, so it keeps the "local-first, no account" non-goal intact.

Vesta has no server, so nothing can "send" a reminder. Each device must work out for itself what it should be reminded of and ask its
operating system to show it at the right time, with the app possibly not running. That is the whole design problem.

## Decision

### What a reminder is

One new optional task field (ADR-031's map):

```
remind: Str     -- minutes before the task's due moment, decimal: "0" at the time, "15", "60", "1440"; absent = no reminder
```

- **One reminder per task** in v1. The form's menu is: None, At the time, 15 minutes before, 1 hour before, 1 day before.
- The **due moment** is the task's due date plus its time. A task with a date but no time uses the device's **default reminder time**
  (a device setting, 09:00 until changed), so "1 day before" a date-only task fires at 09:00 the day before.
- A task with **no due date** ("anytime") has no reminder; the form hides the menu. A repeating task is reminded for **every occurrence**.
- Times are wall-clock, device-local, as ADR-031 stores them (no zone). A 09:00 chore reminds at 09:00 wherever the device is.
- **A reminder is not a separate kind of item.** A person who just wants "remind me at 17:00" makes a task with a time, "At the time",
  and, if they want it private, on the private list (ADR-031). The Tasks form gets a **"Remind me" shortcut** in the add menu that opens the
  form pre-set that way.

### Who is reminded

Each device reminds **its own person**, decided from the documents it already holds:

- A task **assigned to me** reminds me (and each other assignee on their own device).
- A task with **no assignees** ("anyone") reminds every member, until someone ticks it: the tick syncs and the receiving device
  cancels its reminder. A device setting, **Remind me about tasks for anyone** (on by default), turns that off for a person who finds it noisy.
- A task assigned only to other people does not remind me.
- A **private** task always reminds me and nobody else; the private document never leaves my paired devices (ADR-031), and it reminds on every
  one of them.
- "Me" is the profile's member id in that Home (ADR-016). A Home I have left or been removed from schedules nothing.

### The plan: a pure function

```
ReminderPlanner.plan(tasks, done, me, settings, now, until, maxCount): List<PlannedReminder>
PlannedReminder(id, fireAt, title, body, homeId, taskId, occurrence, isPrivate)
```

- Common code, no platform types, and testable with a fixed clock. For each task it works out the occurrences (ADR-031's engine), drops those
  already ticked (`done|<task>|<date>`), computes `fireAt = due moment - remind`, and keeps those in `(now, until]`, soonest first, up to `maxCount`.
- `id` is deterministic: a hash of `homeId|taskId|occurrence`. Scheduling the same reminder twice is therefore the same reminder, and one that
  has changed is replaced, not duplicated.
- `body` says when it is due ("Due 17:00", "Due today") and, for a task assigned to others too, who ("with Sam"). The Home's name is added when
  I belong to more than one Home.
- **Overdue tasks are not nagged.** A reminder fires once, at its time. The Overdue section and dashboard count (ADR-031) are how a missed one is
  found. A reminder whose time passed while the device was off is **not** replayed.

### The scheduler: one small interface, replace-all

```
interface ReminderScheduler {
    val available: Boolean          // false where there is no implementation or the user has refused permission
    suspend fun replaceAll(reminders: List<PlannedReminder>)   // cancel everything Vesta scheduled, schedule exactly these
    fun requestPermission()
}
```

Replace-all, not add/remove, so the device can never drift from the documents: a missed update, a crash, a restore all heal at the next
replan. A `ReminderCoordinator` in common code replans when anything that can change the plan changes:

- a tasks document (Home or private) changes, from a local edit or a sync (the same flow the UI observes);
- the app starts or comes to the foreground; the settings change; the profile or Homes change;
- the device's time zone or clock changes, and (Android) the device boots.

It does nothing when `available` is false. It is the only caller of `replaceAll`.

The interface is deliberately **not about tasks**: it takes `PlannedReminder`s. Calendar event reminders can later add a second planner feeding the
same scheduler, without a new platform layer.

### Per platform

- **Android** (`AlarmManagerReminderScheduler`). One alarm per reminder with `AlarmManager.setAndAllowWhileIdle` (inexact: it can fire a few
  minutes late, but needs no special permission, so there is no "alarms & reminders" settings trip). A `BroadcastReceiver` posts the notification
  on the channel **Task reminders**, reading the title and body from the alarm's own extras so it does not need the database or the app running.
  A second receiver reschedules on `BOOT_COMPLETED`, `TIMEZONE_CHANGED` and `TIME_SET`. Needs `POST_NOTIFICATIONS` (asked at runtime on API 33+
  the first time a reminder is saved, with a sentence of why first) and `RECEIVE_BOOT_COMPLETED` in the manifest. minSdk is 26, so channels always
  exist. **Horizon 60 days, at most 200 alarms.** Tapping a notification opens Vesta on that Home's Tasks. A private task's notification is
  `VISIBILITY_PRIVATE` with a generic public version ("Task reminder"), so a locked screen does not show its title. Exact alarms are not used; if
  to-the-minute proves wanted, `USE_EXACT_ALARM` is a later, deliberate step.
- **Desktop** (`TrayReminderScheduler`). An in-process timer holds the plan and shows `java.awt.SystemTray` notices (`TrayIcon.displayMessage`).
  Reminders therefore fire **only while Vesta is running**; there is no background service. **Horizon 2 days**, replanned hourly. Where there
  is no system tray (`SystemTray.isSupported()` false), `available` is false. Missed reminders while closed are not replayed.
- **iOS**: `available = false` for now (a stub). `UNUserNotificationCenter` with calendar triggers is the right answer (iOS keeps only 64
  pending local notifications, so a 14-day horizon, replan on foreground), but the iOS target is not compiled on this machine (ADR-029 has the same
  gap), and an unverified notification layer is worse than an honest "not yet". The settings screen says "Reminders aren't available on this
  device yet".

### Settings (per device, with the Calendar's settings, ADR-025)

A **Reminders** section in the Tasks settings, stored in the device's local settings (never synced; they are about this device):

- **Reminders** on or off (the master switch; off schedules nothing and cancels everything), with the permission state beside it and a button to
  grant or open system settings when refused.
- **Default reminder time** for tasks with a date but no time.
- **Remind me about tasks for anyone**.
- A line saying what this device can do ("Reminders fire even when Vesta is closed" on Android, "…only while Vesta is open" on desktop).

### What the limits are

- **A stale reminder can fire.** If another member ticks a task and this device has not synced since, the alarm still fires. Sync needs the
  embedded daemon running, and the replan cancels it as soon as the tick arrives. Accepted; the notification says when it was due so a
  stale one is recognisable.
- **Android reminders run out** after 60 days without the app being opened (a replan tops them up). A periodic top-up job is a later
  addition, listed below.
- **Inexact timing** on Android: minutes, not seconds.
- **Notification text is on the lock screen** for Home tasks (the Home's own members would see it anyway); only private tasks are hidden.

## Acceptance criteria

1. A task with a time and "At the time" shows a notification at that time, on the device of its assignee, with the app closed (Android) or open (desktop).
2. A repeating task reminds for each occurrence; ticking an occurrence stops that occurrence's reminder and no other.
3. A task assigned to others does not remind me; one assigned to nobody reminds me unless I turned that setting off; a private task reminds only me, on each of my devices.
4. Ticking, editing, moving, deleting a task, or changing its time or reminder, replaces its reminder on the next replan, including when the change arrived by sync.
5. A date-only task reminds at the default time, and changing that setting moves them.
6. Turning reminders off, or refusing permission, schedules nothing and the form says why a reminder will not fire; no crash, no lost data.
7. A private task's notification does not show its title on a locked Android screen.
8. After a reboot or a time-zone change, Android's pending reminders are rebuilt.
9. iOS says reminders are unavailable and the rest of Tasks still works.

Tests to write: `ReminderPlannerTest` (offsets, date-only default time, repeats, done occurrences, assignee rules, private, horizon and cap,
determinism of ids, no replay of the past), `ReminderCoordinatorTest` (with a fake scheduler: replans on each trigger, none when unavailable,
replace-all idempotent), `TasksAcceptanceTest` additions (Given/When/Then, docs/testing/acceptance-tests.md), `TaskEditScreenTest` (the menu,
hidden for anytime, permission prompt wording), `TaskSettingsScreenTest`, and an Android host test for the receiver building the notification.
Delivery of a real notification cannot be proven by a unit test; **by-hand checks** are owed on a real Android device (Doze, reboot, locked
screen) and on desktop.

## Not yet

Several reminders per task; snooze and a "Done" button on the notification (needs the document open in the background); a "<name> assigned
you a task" notification (needs detecting a new assignment arriving by sync); a daily digest (ROADMAP Phase 2); repeating the nag for overdue
tasks; exact alarms; an Android periodic top-up job so reminders never run out; calendar event reminders (the scheduler is ready for them);
reminders on iOS; a quiet-hours setting; reminders for a device that is never opened and never syncs (impossible without a server, by design).
