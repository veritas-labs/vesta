# Sync Activity (per Home)

> Visual/interaction and data design for a screen that shows what has been syncing for one Home: who it is syncing with,
> when it last worked, what came in and went out, and why something is not arriving. Behavioral background:
> [SYNC-AND-JOINING.md](../product/SYNC-AND-JOINING.md), [ADR-005](../adr/ADR-005-home-automerge-document-over-x0x-group.md),
> [ADR-013](../adr/ADR-013-device-data-sync.md), [ADR-017](../adr/ADR-017-retrying-messages-to-offline-devices.md),
> [ADR-020](../adr/ADR-020-syncing-without-overlapping-online-time.md).
>
> **Status:** Built (2026-10-15); the decision record is [ADR-033](../adr/ADR-033-sync-activity.md), whose "As built" section lists where the build differs from this draft. Open questions decided 2026-10-15 (§12). The data section (§8) shapes the
> design, so read it before the layout.

## 1. Purpose

Vesta has no server, so "did my change get there?" has no obvious answer. Today a user can see only a per-device
"last synced" line on a member's detail screen and the hub list. When a partner says "I don't see the milk I added",
nobody can tell whether the other phone is off, the hub is down, or the app is simply waiting for the next round.

This screen answers three questions for **one Home**:

1. **Is this Home up to date right now?** (a one-line verdict)
2. **Who have I synced with, and when?** (per device and hub)
3. **What actually moved?** (a short, plain-language log of changes in and out, and of things that failed)

It is a reassurance and diagnosis screen, not a control panel. The only action is "Sync now".

### Non-goals

- Not a debug console. No document ids, sync-message bytes, Automerge heads, x0x agent ids by default, or stack traces.
  Those stay in Network diagnostics (`NetworkDiagnosticsScreen`).
- Not an audit trail of *who changed what in the Home* (that is content history, a separate feature). This shows
  *transport* events: a change from Sam arrived, not "Sam ticked milk".
- Not cross-Home. Each Home has its own screen, matching the per-Home model (ADR-005). A device-level overview of all
  Homes is out of scope (§12).
- Not live packet-level progress. Rounds are every 30 s plus presence triggers; the screen reflects that cadence.

## 2. Where it lives

| Entry point                                    | Notes                                                                                                |
|------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Home dashboard app bar overflow → **Sync activity** | Next to *Manage hubs*. Visible to every member (not admin-only: members are the ones asking).   |
| Hub screen → "See activity"                     | Link from each hub row, pre-filtered to that hub.                                                    |

Navigation: full-screen push, back arrow to the dashboard. It is a stack screen, not a bottom-nav tab.

## 3. Layout, mobile (~360-420dp)

```
┌───────────────────────────────────┐
│ ←  Sync activity            [ ⟳ ] │  ← app bar: back, title, Sync now
│    The Johnsons                   │  ← Home name, subtitle
├───────────────────────────────────┤
│ ┌───────────────────────────────┐ │
│ │ ● Up to date                  │ │  ← Status card (verdict + one line of why)
│ │ Last sync 2 min ago with Sam's│ │
│ │ Pixel. 3 of 4 devices online. │ │
│ └───────────────────────────────┘ │
│                                   │
│ Devices                           │  ← section header
│ ● Sam's Pixel 9        2 min ago  │
│ ● Priya's iPad     online, 10 s   │
│ ○ Tom's phone       3 days ago    │  ← muted: offline
│ ▣ Kitchen hub          1 min ago  │  ← hub row, hub icon
│                                   │
│ Recent activity        [All ▾]    │  ← filter: All / Received / Sent / Problems
│ Today                             │
│ ↓ Tasks updated by Sam's Pixel    │
│   14:02                           │
│ ↑ Shopping list sent to 2 devices │
│   13:58                           │
│ ⚠ Couldn't reach Tom's phone      │
│   13:40  · will keep trying       │
│ Yesterday                         │
│ ↓ Joined: Priya's iPad            │
│   …                               │
└───────────────────────────────────┘
```

## 4. Layout, tablet / desktop

Two columns, following the dashboard's pattern: left (~35%) is the status card and Devices list, right (~65%) is the
Recent activity log, which gets the room. Content is capped at `ContentMaxWidth` (720dp) per column group on desktop;
beyond 1024dp the log keeps a readable line length and the margins are ambient. Tapping a device on the left filters the
log on the right to that device (and a second tap clears it).

## 5. Component inventory

| Component               | Anatomy                                                                                                                                                                                               |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **App bar**             | Back (`common_back`), title "Sync activity", Home name as subtitle, trailing **Sync now** icon button (calls `syncNow()`; disabled with a spinner while a round runs).                                  |
| **Status card**         | Coloured dot + verdict (see §6), then one explanatory sentence. Shape and colour tokens match the Hub screen's connected/disconnected treatment (`ConnectedGreen` etc.), never colour alone.               |
| **Device row**          | Presence dot, device name (what the device calls itself), owner in muted text if not you ("Sam"), relative "last synced", hub icon if the device is a hub. 48dp min height, `role = Button`, opens §7.   |
| **Hub row**             | As a device row, plus the hub's own state from `HubState` (in the group or not). Hubs are listed after devices.                                                                                       |
| **Activity item**       | Direction icon (↓ received, ↑ sent, ⚠ problem, ◆ membership), one plain sentence, time, optional second line (e.g. "will keep trying"). Grouped under day headers.                                    |
| **Filter menu**         | Text button with dropdown: All, Received, Sent, Problems. Defaults to All. Remembered for the visit only.                                                                                              |
| **Empty log**           | Illustration + "Nothing has synced yet." with the §9 reason underneath when there is one.                                                                                                              |

All strings go in `strings.xml`; no hard-coded text.

## 6. The verdict (status card)

One state at a time, first match wins (Syncing, while a round runs, wins over the rest). This is the most important logic on the screen, so it is derived from data the app
actually has, not guessed.

| Verdict               | When                                                                                                         | Copy (draft)                                                      |
|-----------------------|--------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| **Problem**           | The x0x daemon is unreachable, or a pending membership message has been failing for more than 1 hour.        | "Sync isn't running. {reason}"                                    |
| **Only you**          | The Home has no other devices (roster lists only this one, no hub).                                          | "This Home is only on this device. Invite someone to sync."       |
| **Up to date**        | Last round finished with no failures and at least one peer or hub synced within the last 2 rounds (about 1 min). | "Up to date. Last sync {ago} with {device}."                  |
| **Changes waiting**   | This device has local changes it has not been able to send to anyone (and no hub took them).                | "{n} changes are waiting to be sent."                             |
| **Waiting for others**| No peer reachable now, but this device has nothing unsent.                                                   | "Nobody else is online right now. Changes will sync when they are." |
| **Syncing…**          | A round is in progress. Replaces the card only while running (spinner), then falls back to the above.      | "Syncing…"                                                        |

As built, **Changes waiting is checked before Waiting for others**, because "Waiting for others" means nothing is unsent; the count in its copy became a plain sentence (the library has no change-count API). "Changes waiting" needs the unsent-change tracking in §8.3, which is in scope. Keep the tone calm ("Your household, your rules"), never alarming.

## 7. Device detail (bottom sheet on mobile, inline panel on tablet)

Opens from a device or hub row. Shows:

- Name, owner, whether it is a hub or one of your own paired devices.
- **x0x last seen** and **Vesta last synced** (the same two figures `MemberDetailScreen` shows via `lastSeenMsAgo` and
  `lastSyncedMsAgo`; reuse `agoText`).
- **Documents** synced with it, by plain name: Pantry, Shopping list, Cookbook, Meal planner, Calendar, Tasks, Merchants,
  Members. A private calendar or private tasks appear only on *your own* devices' sheets (they never go to other members).
- A **Retrying** note if an outbox message is pending to it (ADR-017), with its age.
- Advanced (collapsed): the x0x agent id, with copy button.

No destructive actions here. Removing a device or hub stays on Members and Manage hubs.

## 8. Data: what exists and what must be built

This is the constraint that shapes the design. Sync today is built for correctness, not observability.

### 8.1 Already available

- Per-peer **last synced** time: `DeviceDataSyncService.lastSynced` (in-memory `ConcurrentHashMap`, updated on each
  successful send, exposed as `AgentDetails.lastSyncedMsAgo`). Lost on restart.
- Per-peer **backoff**: `peerDownUntil`, set when a send fails (so "unreachable" is known, with an expiry).
- **Presence** events from x0x (online/offline per agent).
- The **roster** (`rosters.devices(homeId)`): members and their devices; `HomeHubs.hubs()` for hubs.
- **Outbox**: `PendingMessage` rows (ADR-017), one per recipient device, Home and kind.
- **Mailbox cursors**: `MailboxWrite`, `MailboxSequence`, `MailboxRead` (last entry read per writer).

### 8.2 Missing: an event log

There is **no record of individual sync events**, and `pump` sends in a loop without noting what changed. The Recent
activity list needs a new, small, local, unsynced table, e.g. `SyncEvent`:

| Column      | Notes                                                                                              |
|-------------|----------------------------------------------------------------------------------------------------|
| `id`        | autoincrement                                                                                      |
| `homeId`    | the Home (private-document events attach to their Home)                                            |
| `at`        | epoch millis                                                                                       |
| `kind`      | `received`, `sent`, `failed`, `membership`, `mailbox_in`, `mailbox_out`                            |
| `module`    | pantry, shopping, cookbook, mealplan, calendar, tasks, merchants, members (from `documentId`)      |
| `peerAgent` | counterparty (device agent id); name resolved at display time from the roster                      |
| `detail`    | short enum/code, not free text (e.g. `unreachable`, `retrying`), localised in the UI              |

**Do not log every Automerge sync message.** A round does many small exchanges per document and peer. Record at most one
event per (module, peer, direction) per 5 minutes, collapsed with a count, and only when the message changed the document
(on receive: heads moved; on send: a non-empty message went out). Retention: 7 days, pruned at the
start of each round. This keeps the table small and the log readable.

### 8.2a Persisting `lastSynced`

`lastSynced` becomes a small local table (agent id, millis), written at most once a minute per peer, so the screen is
correct straight after a restart instead of reading "never". `AgentDetails.lastSyncedMsAgo` reads from it too.

### 8.3 "Changes waiting"

To say "3 changes waiting" the app needs the count of local changes not yet acknowledged by anyone. Automerge's sync state
does not give that cheaply. Cheapest honest proxy: compare the document's current heads to the heads last deposited in
the mailbox (`MailboxWrite`) and to those last sent to any peer; if both lag, "changes waiting" is true, with no number.
Show a number only if it can be computed cheaply; otherwise the card says "Changes are waiting to be sent" with no count.

### 8.4 Exposure

A `SyncActivityRepository` in `commonMain` (interface, like `HomeHub`) implemented in `jvmAndroidMain` by
`DeviceDataSyncService`, returning:

```
SyncActivity(
  verdict, lastRoundAt, roundInProgress,
  peers: List<PeerActivity>,      // devices + hubs
  events: Flow<List<SyncEvent>>   // newest first, paged
)
```

A `SyncActivityViewModel` collects it. The screen composable takes plain state and callbacks (like the other screens), so
previews and `compose-ui-test` need no service.

## 9. States

| State                | Treatment                                                                                                                                         |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| **Loading**          | Blank page (matches the app's L7 treatment), no spinner flash and no empty state before data arrives.                                            |
| **Empty log**        | "Nothing has synced yet." Beneath it the reason when known: "You're the only member", or "Waiting for another device to be online".               |
| **Only this device** | Status card verdict `Only you`, Devices section replaced by an "Invite someone" row to the existing invite flow.                                 |
| **All offline**      | `Waiting for others`; every device row muted; log still shows history.                                                                            |
| **Daemon down**      | `Problem` card; "Sync now" disabled; link to Network diagnostics.                                                                                  |
| **Sync now pressed** | Button shows a spinner until the round ends (or 10 s). Result appears as a new log item, not a toast.                                             |
| **Long log**        | Lazy list, paged 50 at a time, "Show older" at the end; pruned data is simply absent (no error).                                                    |

## 10. Accessibility and themes

- The verdict is announced as a heading + text, never colour alone (icon shape differs per verdict).
- Direction icons carry content descriptions ("Received", "Sent", "Problem"); each log item reads as one sentence with its time.
- Relative times are also available as absolute time on long-press/hover (and in the accessibility label).
- Device rows and the filter are 48dp targets with `role = Button`.
- At 200% font scale the log items wrap; the status card grows; no truncation of the verdict.
- Themes: follows the app theme tokens (light/dark, and the High Contrast / Kid-Friendly variants other design docs
  define). High Contrast replaces the small presence dots with text ("Online" / "Offline"). Motion (spinner) respects
  reduce-motion.

## 11. Privacy and safety

- The log is **local only** and never synced (it is not in any Automerge document or mailbox).
- Only transport facts are stored: no item names, quantities, or content. "Shopping list updated", never "Milk added".
- It is visible to anyone who holds this device and has this Home open. It reveals device names and when they were
  online, so it is shown only for Homes the current profile belongs to.
- Removing a Home or leaving it deletes its `SyncEvent` rows.
- Retention (7 days) is stated at the bottom of the log: "Showing the last 7 days."

## 12. Decisions and open questions

Decided 2026-10-15:

- **"Changes waiting" is built** in the first version (§6, §8.3).
- **Every member sees the full screen**, log included.
- **Retention is 7 days.**
- **`lastSynced` is persisted** (§8.2a).
- **No dashboard status chip.** The dashboard stays calm; entry is the overflow menu and the Hub screen only.

Still open:

1. **Count or no count** for "Changes waiting": depends on whether a number is cheap to compute (§8.3).
2. **Hub rows.** Hub mailbox activity is folded into device events, labelled "via hub"; say if you want separate lines.
3. **A device-wide view across Homes** is out of scope; a later Settings section could reuse the same repository.
4. **Copy.** All strings above are drafts and need a copy pass for tone.

## 13. Acceptance tests (Given / When / Then)

To be added to `docs/testing/acceptance-tests.md` with the unit tests when this is built.

- *Given* a Home with two other devices, one online and synced a minute ago; *When* the user opens Sync activity; *Then*
  the status card says "Up to date", the online device shows its time and the offline one is muted.
- *Given* no other device is online; *When* the screen opens; *Then* it says "Waiting for others" and the log still lists
  earlier events.
- *Given* the user is the only member; *Then* the card says "Only you" and shows an Invite row, not an empty Devices list.
- *Given* a device sent a Tasks update; *Then* the log shows "Tasks updated by {device}" with the time, and no task content.
- *Given* a send to a device failed and backs off; *Then* a Problems entry "Couldn't reach {device}, will keep trying"
  appears, and filtering by Problems shows only it.
- *When* the user taps Sync now; *Then* the button shows progress and the round runs (`syncNow()` called once).
- *Given* the app restarts; *Then* each device row still shows its last synced time.
- *Given* this device has local changes no peer or hub has received; *Then* the card says "Changes waiting" until one takes them.
- *Given* events older than 7 days; *Then* they are not shown and are removed on the next round.
- *Given* the user leaves the Home; *Then* its events are deleted.
- *Given* the x0x daemon is down; *Then* the card says "Sync isn't running" and Sync now is disabled.
- Previews: light/dark x phone/tablet, grouped by device, for Up to date, Waiting, Only you, Problem and Empty.
