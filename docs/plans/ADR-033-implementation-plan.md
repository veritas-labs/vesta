# Implementation plan: Sync activity (ADR-033)

Written 2026-10-15 so a fresh session can build [ADR-033](../adr/ADR-033-sync-activity.md) without re-deriving context. Read the
ADR and the [design doc](../design/sync-activity.md) first; this plan says **how to build it in this codebase**, in what order, and how
to verify it. **Status: WP0-WP8 built (2026-10-15).** As built: see the ADR's "As built" section for the differences (verdict order, model additions, migration `19.sqm` taking the schema to 20), the UI and recording gaps, and what was not done (no by-hand run against a real daemon, the desktop app or Android; no iOS). Acceptance criteria are in `docs/testing/acceptance-tests.md`, backed by `SyncActivityAcceptanceTest` and the service tests it names.

## Goal

A member opens **Sync activity** for a Home and sees a verdict, the Home's devices and hubs with last-synced times, and a 7-day
log of content-free transport events, with **Sync now**. Nothing leaves the device.

## State of the repo (read 2026-10-15; verify before relying on it)

- **Sync engine:** `jvmAndroidMain/.../datasync/DeviceDataSyncService.kt` (519 lines) implements `DeviceDataSync`. Relevant seams:
  - `pump(documentId, peer)`: the only place a sync message is *sent* (`client.sendDirect`, then `lastSynced[peer] = nowMillis()`); its
    `catch` is the only place a send *fails* (sets `peerDownUntil[peer]`).
  - `syncFrom(message)` -> `receive(sender, documentId, bytes)`: the only place a sync message is *received*; `lastSynced[sender]` is set
    after it. `receive` routes by `documentId` suffix (`homeIdOf`, `PrivateCalendarSuffix`, `PrivateTasksSuffix`, registry, profile).
  - `round()` = `syncMailboxes()` + `syncEverything()` + `syncMailboxes()`; `syncMailboxes()` loops Homes calling `mailboxWriter.deposit`,
    `.compact`, `mailboxReader.fetch` (returns the applied count).
  - `agentDetails(homeId, agentId)` builds `AgentDetails` (`devicesync/DeviceDataSync.kt:13`) including `lastSyncedMsAgo` from `lastSynced`.
  - `syncNow()` conflates into `syncRequests`; `X0xEventHub(client).presence` gives online/offline.
- **Outbox:** `PendingMessage.sq` (migration `10.sqm`), resent by `join.resendPending()`; ADR-017.
- **Mailbox cursors:** `Mailbox.sq`: `MailboxWrite(homeId, documentId, heads)`, `MailboxSequence`, `MailboxRead`.
- **Hubs:** `HomeHubs.hubs(homeId)` returns `HubState(agentId, inGroup, isDevice)`; roster devices via `rosters.devices(homeId)`.
- **Database:** async SQLDelight. Latest migration is **`18.sqm`** (it migrates version 18 to 19), so the new one is **`19.sqm`**. Every
  `.sq` table is mirrored in a migration with `CREATE TABLE IF NOT EXISTS`. Tests build from the `.sq` schema, so both must agree.
- **UI:** screens take plain state + callbacks (`MemberDetailScreen`, `HubScreen`); navigation is `destination = HomeDestination.X` in
  `commonMain/.../App.kt` (the Hub manager is at ~`:520`, `:566`). The dashboard overflow has `onHubClick`
  (`HomeDashboardScreen.kt:193`, test tag `HubMenuItem`). `MemberDetailScreen` has `agoText(...)` and `InfoLine`. Shared constants:
  `ContentMaxWidth`, `FabClearance`, `common_back`, `ConnectedGreen`.
- **Fakes:** `jvmTest/.../devicesync/FakeX0xNetwork.kt` (nodes, `offline`, `comeOnline`, DMs, groups, stores) drives
  `DeviceDataSyncServiceTest`. Real-daemon tests are opt-in (`DaemonHarness`).
- **Conventions (project memory, must follow):** every test method has `// Arrange` / `// Act` / `// Assert`; prefer `x?.let { }` to
  `if (x != null)`; every `@Preview` is light/dark x phone/tablet grouped by device; each feature has Given/When/Then acceptance
  tests in `docs/testing/acceptance-tests.md` *and* unit tests; commit only when asked, ending messages with the attribution lines.

## Decisions taken in this plan (fold back into the ADR when done)

1. **One recorder, one write path.** `SyncEventRecorder` (jvmAndroidMain) is the only code that writes `SyncEvent`/`PeerSync`. It buffers
   in memory and flushes once at the end of each `round()`, on `syncWith`, and on `received` after a short debounce (2 s), so the
   sync path never waits on SQL.
2. **Interfaces in `commonMain`, implementation in `jvmAndroidMain`**, like `HomeHub`. iOS gets a stub repository returning
   "unavailable" so the code still compiles there (no Automerge on iOS).
3. **The verdict is a pure function** `SyncVerdict.of(snapshot, now)` in `commonMain`, so the precedence table is unit-tested without a
   service.
4. **Module names come from the document id** through one helper `SyncModule.of(documentId)`, next to `homeIdOf`; it returns null for the
   registry and profile (not Home content, not logged), and the private suffixes map to `Calendar`/`Tasks` with `private = true`.
5. **Peer display names** are resolved at read time from the roster, never stored in `SyncEvent`.
6. **Events carry a `count`** (collapsing, see ADR) and a `lastAt`; the schema has both `at` (first) and `lastAt`.

## Work packages

Each WP is a commit-sized slice with its tests; the order lets everything up to WP6 be built and verified on the fake mesh without a
real daemon or any UI.

### WP0. Spike: can "changes waiting" be answered cheaply? (done 2026-10-15)

Findings are in the ADR's "Spike findings". In short: heads are already available for every Home document through `MailboxDocument`;
there is no change count, so the result is a boolean; and `SyncState.isInSync(document)` gives an acknowledgement-based answer (verified
with a throwaway two-document test, since deleted). No change to `SyncableDocument` is needed. Each document source gains a small
`isInSync(state)` method (WP4) because they keep their `Document` private.

### WP1. Schema and queries

- `SyncEvent.sq` and `PeerSync.sq` in `commonMain/sqldelight/.../db/`, mirrored in `migrations/19.sqm` (header comment in the style of
  `18.sqm`, `CREATE TABLE IF NOT EXISTS`).
  - `SyncEvent(id INTEGER PK AUTOINCREMENT, homeId, at, lastAt, kind, module, peerAgent, detail, count INTEGER NOT NULL DEFAULT 1)` with an
    index on `(homeId, lastAt)`; `ON DELETE CASCADE` from `Home(id)` so leaving or deleting a Home removes its rows.
  - `PeerSync(agentId TEXT PK, lastSyncedAt INTEGER NOT NULL)`.
  - Queries: `selectPage(homeId, beforeLastAt, limit)`, `selectFiltered(...)` by kind group, `findOpen(homeId, module, peer, kind, since)`
    for collapsing, `bump(id, lastAt, count)`, `insert`, `pruneOlderThan(cutoff)`, `selectPeerSync`, `upsertPeerSync`.
- **Tests:** `SyncEventQueriesTest` (insert/page order, collapse lookup respects the 5-minute window, prune, cascade on Home delete); a
  migration test following the existing migration tests: a version-18 database upgrades to 19 with both tables present.
- **Exit:** schema + migration pass; `./gradlew :shared:jvmTest` green.

### WP2. `SyncEventRecorder`

- Class in `jvmAndroidMain/.../datasync/`. API: `received(homeId, module, peer)`, `sent(...)`, `failed(homeId, module?, peer, detail)`,
  `membership(homeId, peer, detail)`, `mailbox(homeId, direction, peer?)`, `lastSynced(peer)`, `flush()`, `prune(now)`.
- Collapsing per ADR: one row per (Home, module, peer, kind) per 5 minutes with a count. Clock injected (the service has `nowMillis()`).
- **Failure isolation:** every public method wraps its body in `runCatching` and swallows non-cancellation errors (acceptance 10).
- `PeerSync` writes limited to once a minute per peer; `lastSyncedMsAgo` reads memory first, then the table.
- **Tests (`SyncEventRecorderTest`, in-memory database):** collapsing and the count; a new row after the window; no content columns
  exist; a throwing database does not propagate; `prune` removes rows older than 7 days and keeps the rest; `PeerSync` rate limit.
- **Exit:** unit-tested in isolation, not yet called by anything.

### WP3. Record the events (service wiring)

Add the recorder to `DeviceDataSyncService` (constructor param with a default so `main.kt`/`MainActivity.kt` and existing tests still
compile; both entry points pass the real one). No behaviour change; only calls:

- `pump`: after a non-empty `sendDirect` -> `sent`; in `catch` -> `failed(detail = "unreachable")`.
- `receive`: record `received` **only if the document's heads changed** (take heads before and after through the WP0 accessor; the
  registry and profile are skipped via `SyncModule.of` returning null).
- `syncMailboxes`: `mailbox(out)` when `deposit` wrote something, `mailbox(in)` when `fetch` returned a positive count (it already returns
  the applied count).
- `HomeJoinService` membership events: joined, left, removed, ack. Inject the recorder or a small `MembershipEvents` callback rather than
  the whole recorder.
- `round()` ends with `recorder.prune(now)` then `flush()`.
- The existing `lastSynced` map writes through the recorder so it persists.
- **Tests:** extend `DeviceDataSyncServiceTest` on the fake mesh: a Tasks edit on device A produces a `received` Tasks row on B naming A;
  an offline peer produces a `failed` row that collapses across rounds; a mailbox fetch produces `mailbox_in`; a leave produces a
  `membership` row; **a recorder that always throws leaves every existing test result unchanged.** All existing tests still pass.
- **Exit:** the above green with no change to any existing assertion.

### WP4. Changes waiting and the verdict inputs

- Add `suspend fun isInSync(state: SyncState): Boolean` to each document source (Pantry, ShoppingList, Cookbook, Merchants, MealPlan,
  Calendar, Tasks, and `HomeRosters` for the roster), as `lock.withLock { state.isInSync(document) }`.
- `ChangesWaiting.of(homeId)`: true when, for any of the Home's documents (private ones count only against the user's own devices),
  **no** peer's state in `states[(documentId, peer)]` is in sync **and** the current `heads()` differ from `MailboxWrite.heads` (a deposit
  covers it). Evaluate at the end of `round()` and after each `receive`, cache the result per Home, and read the cache. A restart has
  no states, so it errs towards "waiting" (accepted in the ADR).
- Daemon-down: `client.agent()` failing for the last round (already wrapped in `runCatching` in `self()`), plus the stalled-membership
  rule (a `PendingMessage` row older than 1 hour).
- **Tests (`ChangesWaitingTest`):** local change with nobody online -> waiting; after a peer syncs (replies) -> not; a send the peer never answered -> still waiting; after a mailbox deposit -> not;
  fresh start -> waiting; private document never waits on another member's device.
- **Exit:** the service can answer a `SyncSnapshot` (peers with presence and last-synced, hubs, `changesWaiting`, `lastRoundAt`,
  `roundInProgress`, daemon state, stalled message age).

### WP5. Repository and view model (`commonMain`)

- `SyncActivityRepository` (interface), `SyncActivity`/`PeerActivity`/`SyncEvent` models, `SyncVerdict.of(snapshot, now)`, filter enum
  (All/Received/Sent/Problems), `SyncActivityViewModel(homeId, repository)` exposing `state`, `refresh()`, `syncNow()`, `setFilter`,
  `selectDevice`, `loadOlder()`. Pages of 50.
- `DeviceDataSync` gains `val syncActivity: SyncActivityRepository` (default: an "unavailable" stub, as `blobPeers` defaults to empty);
  `DeviceDataSyncService` overrides it.
- Presence dot and name come from `agentDetails`/roster; owner shown if not the current member.
- **Tests:** `SyncVerdictTest` (every row of the design's table and the precedence between them), `SyncActivityViewModelTest` (filter,
  device filter, paging, Sync now calls `syncNow()` once and shows progress until the round ends or 10 s).
- **Exit:** logic complete and tested without any composable.

### WP6. Strings, screen and previews

- `SyncActivityScreen.kt` plus `SyncActivityComponents.kt` (status card, device row, hub row, activity item, filter menu, device
  detail sheet / inline panel). Reuse `agoText`, `InfoLine`, `ContentMaxWidth`, `ConnectedGreen`, `common_back`. Blank loading state (no
  empty-state flash), empty log with reason, two-column layout at expanded width, test tags in a `SyncActivityTestTags` object.
- All strings in `strings.xml`; plural resources for counts ("Tasks updated by Sam's Pixel (3)").
- Previews: Up to date, Waiting, Only you, Changes waiting, Problem, Empty, each light/dark x phone/tablet grouped by device. Check
  `docs/troubleshooting/compose-previews.md` before debugging a preview.
- **Tests (`SyncActivityScreenTest`, compose-ui-test):** each verdict's text; offline device muted; filter narrows the list; Problems
  entry text; Sync now disabled with the daemon down; Invite row for "Only you"; device sheet shows documents by name and hides private
  ones for other members' devices.
- **Exit:** screen tests and previews green on desktop; Android still compiles.

### WP7. Navigation

- `HomeDestination.SyncActivity(homeId)` in `App.kt`; dashboard overflow gets **Sync activity** next to Manage hubs (`onSyncActivityClick`,
  test tag), visible to every member (the hub item is admin-only, this is not); Hub screen gets the "See activity" link.
- `HomeDashboardScreenTest` additions: item present for member and admin, click invokes the callback.
- Back returns to where it was opened from (dashboard or hub).
- **Exit:** reachable in the running app.

### WP8. Docs and acceptance tests

- Given/When/Then entries for ADR criteria 1-10 in `docs/testing/acceptance-tests.md` with a `SyncActivityAcceptanceTest` (fake mesh +
  screen) backing them.
- Update `docs/product/SYNC-AND-JOINING.md` with a short "Seeing what synced" section; set ADR-033 and the design doc to "Built" with an
  "As built" note recording differences (especially WP0's outcome); update the memory index.
- **Exit:** docs match the code.

## Verification

1. `./gradlew :shared:jvmTest` (all existing tests plus the new ones), and the Android and desktop compile tasks.
2. **By hand, desktop** (use a second Vesta instance, not the user's own; see the memory notes on the live app): create a Home, pair or
   invite a second instance, edit Tasks on one side, and watch the other's log update; stop the second instance and see "Waiting for others"
   and a Problems entry; restart the first and see last-synced times survive.
3. **By hand, Android emulator:** open the screen on a Home with a hub (ADR-020 setup), toggle airplane mode, check the verdict follows.
4. Performance check: a Home with several documents over 30 rounds writes a bounded number of rows (collapsing works) and a round is not
   measurably slower (compare timings in `DeviceDataSyncServiceTest` before and after).

## Risks

- **"Changes waiting"** relies on sync states, which are cleared every round; evaluating at the wrong moment shows a false "waiting" (WP4 caches the result from the end of a round).
- **Event noise:** if `received` fires on every handshake, the log is useless. The heads-changed test in WP3 is what prevents it; check it
  on the real daemon by hand.
- **Recorder in the hot path:** keep writes buffered (decision 1); never `await` SQL inside `lock`.
- **Private documents** must not leak into other members' views: tested in WP6, and the query filters by peer being the user's own device.
- Real-daemon behaviour (presence timing, `peerDownUntil` backoff) is only covered by hand, as with other sync work.

## Out of scope

As in the ADR's "Not yet": a cross-Home overview, a dashboard chip, log export, long-failure notifications, separate hub mailbox lines,
and acknowledgement-based "delivered" status.
