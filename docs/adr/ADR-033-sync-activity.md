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
3. **Unsent-change tracking** for "Changes waiting", from the peers' **acknowledgement**, not from what was sent. Automerge's
   `SyncState.isInSync(document)` is true only once the peer has replied with the heads it has, so a message to an offline device
   stays "not in sync" (Spike 1). A Home is "waiting" when, for some document, no peer's sync state is in sync with it **and** the
   current heads are not covered by the last mailbox deposit (`MailboxWrite`). The result is a boolean (the library has no
   change-count API, so there is no number: the card says changes are waiting to be sent). The service clears its sync states at
   the start of each round (`syncEverything`), so the answer is computed at the end of a round, once the replies are in, and held until
   the next; a restart errs towards "waiting".

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
- "Changes waiting" is based on acknowledgement, so it is accurate about what a peer has confirmed. It can still show "waiting" after a
  restart until the first round ends, and a peer that has everything but has not replied yet counts as not in sync. The copy says
  "waiting to be sent".
- A restart clears the in-memory sync states, not the persisted `lastSynced`.
- Sync-state facts that are cheap here (heads) are the only ones used. Anything needing a peer's acknowledgement would need a protocol
  change and is out of scope.

## Spike findings

**Spike 1 (2026-10-15): can "changes waiting" be answered cheaply?** Yes, as a boolean.
- Every Home document already exposes `heads()` through `MailboxDocument` (`HomeMailboxDocuments.open`): roster, Pantry, shopping list,
  cookbook, merchants, meal plan, calendar, tasks. The private calendar and tasks documents are not in that list but use the same
  source classes, which have `heads()` and `receiveSync`. Nothing needs adding to `SyncableDocument` for "did the heads change".
- There is **no change-count API** in automerge-java 0.0.9 (`encodeChangesSince` returns opaque bytes), so no number.
- `SyncState.isInSync(doc)` (throwaway test, deleted): false for a new state, false after sending with no reply, true after a full
  exchange, false again after a new local change, true after the next exchange. It reflects acknowledged heads. The sources keep their
  `Document` private, so each needs a small `isInSync(state)` method.
- Sync states are cleared at the start of every round, so the verdict must be computed after a round's exchanges, not read cold.

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
