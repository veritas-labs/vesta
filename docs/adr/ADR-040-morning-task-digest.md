# ADR-040: Morning digest of today's tasks with no time

**Status:** Built on Android and desktop (2026-10-19), uncommitted; not run on a device or in the desktop app. Related to ADR-031 (tasks) and ADR-032 (timed reminders).

## Context

ADR-032 reminds a person of a task at its time. A task due today **with no time** has no moment to remind at, so it falls through: nothing
tells the person it is due. This is the first, smallest notification: one note a day naming those tasks.

## Decision

- **What it says.** Each morning, the open occurrences due **today** with **no time set**, in every Home the user belongs to, for the user:
  assigned to them, assigned to anyone, or on their private list. Tasks with a time are ADR-032's; tasks with no date at all are not "due today".
  Nothing due means no notification.
- **When.** The first time the device is awake after **07:00 device time**, once per day. It must not wake the device.
- **How (Android).** A local `AlarmManager.set(RTC, next 07:00)`: the non-waking alarm type, which the system holds until the device is next awake
  (it is not delivered in Doze until the user picks the device up). No push service, no server, no WorkManager. `ACTION_USER_PRESENT` ("the user
  unlocked") would match the wording even more closely but cannot be declared in the manifest from Android 8, and a runtime receiver dies with the process.
- **Staying armed.** The alarm is re-armed when the app starts, when the alarm fires, and on `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED`.
  After a boot the alarm is gone and the morning may be past, so a boot after 07:00 shows the digest if today's has not been shown.
- **Once a day.** The day shown is kept in the app's private preferences and written *before* the database is read, so a crash cannot loop.
  A day with nothing to say still counts.
- **Reading the tasks.** `morningDigestItems` reads the persisted Automerge documents straight from the database: no screen, no x0x daemon.
- **Privacy.** If any listed task is private, the notification is `VISIBILITY_PRIVATE` and the lock screen shows only the count.
- **Control.** Its own channel, "Tasks for today"; the person turns it off or changes its sound in the system's notification settings. There is no
  in-app switch yet.
- **How (desktop).** No alarm and no "first wake" signal, so a timer: `MorningDigestRunner` checks every minute while Vesta is running, and the first
  check after 07:00 shows a system tray notice (`java.awt.TrayIcon`). A machine that sleeps through 07:00 shows it within a minute of waking, and an app
  started after 07:00 shows it at once. The day is kept in `~/.vesta/morning-digest`. Nothing runs while Vesta is closed, the same limit as ADR-032's desktop
  reminders. Clicking the notice raises the window on that Home's Tasks. Where `SystemTray` is unsupported (GNOME on Wayland reports it so; confirmed on the
  author's Fedora machine) it runs `notify-send`, whose click action raises the window; with neither, a line goes to stderr. English text only, as on the Android side.
- **Tap.** The notification carries the first task's Home id; `MainActivity` (now `singleTop`) hands it to `TasksDeepLink`, which selects the Home and opens its Tasks. With tasks from several Homes it opens the first's.
- **Permission.** `POST_NOTIFICATIONS` is requested at startup on Android 13+. Without it the notification is dropped silently.

## Not in this ADR

iOS (needs the unverified notification layer ADR-032 describes). An in-app on/off switch and a
configurable hour. Overdue tasks in the digest. A permission prompt with a reason first.
Timed reminders (ADR-032, still parked); the planner there should reuse nothing from here but the channel idea.

## Known limits

- A non-waking alarm is delivered when the device is next awake *for any reason*, which may be a background wake rather than the user. Close to
  "when they pick it up", not exactly it.
- A force-stop or an aggressive battery manager removes the alarm; opening the app re-arms it.
- The receiver opens its own database connection to the file the app also uses. Untested together on a device.
