# ADR-033: Sync activity — a local transport log and a per-Home status screen

**Status:** Proposed (2026-10-15); not implemented. Design: [sync-activity.md](../design/sync-activity.md). Builds on ADR-013
(device data sync), ADR-017 (retrying messages), ADR-020 (mailbox and hubs).

## Context

Vesta has no server, so a member whose change has not arrived has no way to tell whether the other phone is off, the hub is
down, or the app is waiting for its next round. Today the only visible sign is a per-device "last synced" line on the member
detail screen (`AgentDetails.lastSyncedMsAgo`) and the hub list.

Sync is built for correctness, not observability. `DeviceDataSyncService` keeps `lastSynced` and `peerDownUntil` in memory
only (lost on restart), records no individual events, and does not know how much of a document a peer has not yet received.
The outbox (`PendingMessage`, ADR-017) and the mailbox cursors (`MailboxWrite`, `MailboxSequence`, `MailboxRead`) exist but are not
exposed.

## Decision

### A Sync activity screen per Home

A full-screen page, opened from the Home dashboard's overflow menu and from the Hub screen, visible to **every member**. It shows
a one-line verdict, the Home's devices and hubs with their last-synced times, and a log of recent transport events. Its only
action is **Sync now** (`DeviceDataSync.syncNow()`). Layout, states, copy and accessibility are in the design doc. There is no
dashboard status chip.

### Verdict

Derived, first match wins: Problem (daemon unreachable, or a pending membership message failing for over an hour), Only you,
Up to date, Waiting for others, Changes waiting, and Syncing while a round runs. "Changes waiting" is in the first version.

### Three new pieces of local data

All three are local to the device and **never synced** (not in an Automerge document or a mailbox).

1. **`SyncEvent` table** (migration 19, mirrored in `SyncEvent.sq` as the other `.sq` files do): `id`, `homeId`, `at`, `kind`
   (`received`, `sent`, `failed`, `membership`, `mailbox_in`, `mailbox_out`), `module` (pantry, shopping, cookbook, mealplan,
   calendar, tasks, merchants, members), `peerAgent`, `detail` (a code such as `unreachable` or `retrying`, localised in the UI,
   never free text). **No content is stored**: no item names, quantities or titles.
   - **What is recorded.** An event is written only when a message changed something: on receive, the document's heads moved; on
     send, a non-empty message went out; on failure, a send to a peer failed (the existing `peerDownUntil` path in `pump`).
     Membership events come from `HomeJoinService` (joined, left, removed, acknowledged). Mailbox deposits and fetches are
     recorded as `mailbox_out` and `mailbox_in` and shown folded into the device events, labelled "via hub".
   - **Collapsing.** At most one row per (Home, module, peer, kind) per 5 minutes; further events in the window raise a count on
     that row. A round does many small exchanges per document and peer, and logging each would drown the log and the database.
   - **Retention.** 7 days, pruned at the start of each round. Leaving or deleting a Home deletes its rows.
   - **Private documents** (private calendar, private tasks) are logged under their Home, and appear only for the user's own
     devices, because only those ever receive them.
2. **`lastSynced` persisted** in a `PeerSync` table (agent id, millis), written at most once a minute per peer. `AgentDetails.lastSyncedMsAgo`
   reads from it too, so every row shows its time straight after a restart instead of "never".
3. **Unsent-change tracking** for "Changes waiting". For each document of the Home, compare the current heads with the heads last
   deposited in the mailbox (`MailboxWrite`) and with the heads last sent to any peer. If neither covers the current heads, changes
   are waiting. This is a boolean from data already kept; a count is shown only if it proves cheap to compute, otherwise the card says
   changes are waiting without a number. Needs a `lastSentHeads` record per (document, peer), kept in memory and rebuilt on the
   next round (a restart errs towards "waiting", which is the safe side).

### Exposure

- `commonMain`: a `SyncActivityRepository` interface returning `SyncActivity(verdict, lastRoundAt, roundInProgress, peers, events)`
  with events a paged `Flow`, newest first; and a `SyncActivityViewModel`. The screen composable takes plain state and callbacks, like
  the other screens, so previews and `compose-ui-test` need no service.
- `jvmAndroidMain`: `DeviceDataSyncService` implements the repository and writes events through a small `SyncEventRecorder` it owns, so
  the service's own code only calls `recorder.received(...)`, `sent(...)`, `failed(...)`, not the SQL.
- Previews follow the project matrix: light/dark x phone/tablet, grouped by device.

### Privacy

Only transport facts are kept. The log is visible to anyone using this device with the Home open, and shows device names and when
they were online, which the Members screen already shows. Nothing about the log leaves the device.

## Consequences

- Members can tell "nobody else is online" from "it is broken", and see that a change left this device.
- Every sync path gains a recorder call. This touches `pump`, `receive`, the mailbox reader and writer, and the join service, but
  changes none of their behaviour. A recorder failure must never fail a sync: the recorder runs inside `attempt`/`runCatching`
  and drops the event on error.
- The write volume stays small because of the collapsing window and 7-day pruning, but it is new disk traffic on every round. The
  recorder batches writes per round rather than per message.
- "Changes waiting" can be briefly wrong in both directions: a message sent but not received counts as sent (Automerge's sync state
  assumes arrival, as `syncEverything` notes), and a restart shows "waiting" until the first round. The copy says "waiting to be
  sent", not "not received".
- A restart clears the in-memory heads record, not the persisted `lastSynced`.
- Sync-state facts that are cheap here (heads) are the only ones used. Anything needing a peer's acknowledgement would need a protocol
  change and is out of scope.

## Acceptance criteria

1. With two other devices, one online and synced a minute ago, the screen says "Up to date" and lists both, the offline one muted.
2. With nobody online, it says "Waiting for others" and earlier events remain in the log.
3. The only member of a Home sees "Only you" and an Invite row.
4. A received Tasks update appears as "Tasks updated by {device}" with its time and no task content; repeated updates inside five
   minutes raise a count, not new rows.
5. A failed send to a device appears under Problems as "Couldn't reach {device}, will keep trying", and the Problems filter shows only it.
6. A local change no peer or hub has taken shows "Changes waiting" until one does.
7. After a restart each device row still shows its last synced time.
8. Rows older than 7 days are gone after the next round; leaving the Home removes its rows.
9. Sync now runs one round and shows progress; with the daemon down the card says sync isn't running and the button is disabled.
10. A failure inside the recorder does not stop a sync round.

Tests to write: `SyncEventRecorderTest` (collapsing, retention, no content stored, failure isolation), `PeerSyncRepositoryTest`
(persist, once-a-minute write limit), `ChangesWaitingTest` (heads compared with the mailbox and with peers, restart behaviour),
`SyncVerdictTest` (each verdict and the precedence), `DeviceDataSyncServiceTest` additions on the fake mesh (events appear for
received, sent and failed; membership events), `SyncActivityViewModelTest`, `SyncActivityScreenTest`, and Given/When/Then entries
in `docs/testing/acceptance-tests.md`. Whether a real daemon produces the same events is by-hand work, not yet done, as with other
sync features.

## Not yet

A device-wide view across Homes (a Settings section could reuse the repository); a dashboard status chip (decided against); exporting
or sharing the log; notifying the user when sync has been failing for a long time (would build on ADR-032's scheduler); separate
lines for hub mailbox activity instead of folding it into device events; acknowledgement-based "delivered" status, which needs a
protocol change.
