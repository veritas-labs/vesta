# Implementation plan: syncing without overlapping online time (ADR-020)

Written 2026-10-06 so a fresh session can build [ADR-020](../adr/ADR-020-syncing-without-overlapping-online-time.md)
without re-deriving context. Read the ADR first, especially **Decision**, **Spikes 1–7** (what was proven against real
daemons) and **Open points**. This plan says how to build it in this codebase, in what order, and how to verify it.

## Goal

A household of two people, each with only an Android phone, plus a vanilla `x0xd` (no Vesta code, no sidecar) on an
always-on machine. Phone A changes the Pantry, closes the app. Hours later phone B opens the app and has the change,
though A and B were never online together. The hub holds a group-scoped `mailbox` store that every Home member writes.

## State of the repo (verified 2026-10-06)

- **Done:** presence- and foreground-triggered sync rounds (ADR-020 slice 1); embedded x0x bumped to **0.46.3** for the
  x86_64 Android library (0.45.0 cannot open group stores); `UnseededEditTest` guard so a device holding an unseeded
  placeholder document queues edits until the creator's seed arrives.
- **There is no x0x group per Home today.** The only group Vesta creates is the per-person `DeviceSync` group
  (`DeviceSyncService.kt`: `createGroup` at lines ~99 and ~251). Homes sync over direct messages between the devices on
  each Home's roster (`DeviceDataSyncService.peersFor`, ADR-013/014). ADR-005's "one x0x group per Home" was never
  built. **The mailbox needs one, so slice 2 is the largest piece and is not in the ADR.**
- **`X0xClient` has no group-store call.** It has `createStore/putValue/getValue/listStoreKeys/deleteValue/joinStore`
  for ordinary stores, and the group calls (`createGroup`, `createGroupInvite`, `joinGroup`, `getGroupMembers`,
  `setGroupMemberRole`, `removeGroupMember`, `leaveGroup`).
  `AbstractX0xClient` builds store paths as `"/stores/$storeId/$key"` **without percent-encoding**, but a group store id
  is `x0x/group/<gid>/kv/<hash>` and must be encoded (`/` → `%2F`).
- **Fake mesh:** `shared/src/jvmTest/.../devicesync/FakeX0xNetwork.kt` (nodes, `offline`, `comeOnline`, direct messages,
  groups with roles and invites). It has **no stores**; slice 1 adds them.
- **Database:** async SQLDelight; migrations `1.sqm`–`12.sqm`, so the next is `13.sqm`. Every `.sq` table needs a
  migration.
- **Conventions (project memory, must follow):** every test has `// Arrange` / `// Act` / `// Assert`; prefer
  `x?.let { }` over `if (x != null)`; every `@Preview` is light/dark × phone/tablet grouped by device; `kotlin.test` +
  `runBlocking`; in-memory DB via `openInMemoryDatabase()`; commit only when asked, end commits with the attribution
  line from the session reminder.

## Facts the plan relies on (verified against real daemons, ADR-020 Spikes 1–7)

- Group-store entries survive rekeys, are readable by members who joined later, and are served by any replica while the
  writer is offline. **Do not** use `secure/encrypt` output as a payload (it dies at rekey).
- A daemon does not open a group store when it joins; opening is one idempotent local call, once per group
  (`POST /groups/{id}/stores {"name":"mailbox"}`), and is then restored after restarts. The store id is the same on every
  member. There is no remote trigger.
- Value limit **64 KiB**; no total cap seen at 8 MiB; any member can write and delete; a removed member is cut off by a
  rekey. A non-creator **admin** can mint invites and admit joiners with the creator offline.
- Automerge 0.0.9 offers `Document.encodeChangesSince(heads)` / `applyEncodedChanges(bytes)` / `getHeads()` (idempotent,
  usable as entry payloads). It has no commit-timestamp control, so documents cannot be seeded deterministically.
- The embedded daemon lives only while the app process does (no foreground service; stopped on mobile data unless
  allowed), so a phone touches the mailbox only while the app is open.

## Decisions to confirm before slice 2 (ask the owner; defaults given)

1. **Home group model.** Default: **one x0x group per Home**, created lazily by the Home's creator device the first
   time the mailbox is needed, its id stored on the Home. Every device of every member joins it. (Alternative:
   per-household group spanning Homes — rejected, Homes are the unit of membership, ADR-014.)
2. **Who is x0x admin.** Default: the creator device is the authority; devices of Vesta `Admin`/`Owner` members are
   promoted to x0x `admin` (so they can mint invites); the hub is promoted by the creator when it appears.
3. **Where the hub is recorded.** Default: per Home, `hubAgentId` in the Home's roster document, so every member sees it.
4. **Write policy.** Default: deposit to the mailbox whenever the Home group has an open store, hub present or not (a
   member's other devices also replicate it), so there is one code path.
5. **GC window.** Default: 30 days behind a snapshot.

Write the answers into a new `ADR-021: Home x0x group` (slice 2a) so they are not lost.

## Slices

Each slice is independently committable and leaves the suite green. Order matters where noted.

### Slice 0 — housekeeping (any time, small) — **done 2026-10-06 except the emulator `named_groups.json` check and the iOS build**
- Rebuild the arm64 and armv7 FFI libraries against 0.46.3 (`scripts/build-android-ffi.sh`, normal `preBuild`), and check
  the iOS build (`ffi/x0x-ffi` staticlib) compiles with 0.46.3.
- Check the emulator's `files/x0x/named_groups.json` for a leftover test-group entry (reset to `{}` if it holds only test
  groups; see ADR-020 Spike 7).
- ADR-020 old slice 2: document in the user-facing help what overlap is required today.
- **Done when:** `./gradlew :androidApp:installDebug` works with all ABIs and the app starts; help text merged.

### Slice 1 — group stores in `X0xClient` and the fake mesh — **done 2026-10-06**
- `X0xClient`: add `openGroupStore(groupId: String, name: String): String` (returns the store `id`), and reuse the
  existing `listStoreKeys/getValue/putValue/deleteValue` with ids and keys **percent-encoded** (fix in
  `AbstractX0xClient`; add a unit test with a `/`-containing id).
- `FakeX0xClient` / `FakeX0xNetwork`: model group stores. Per group, a store replicated on every member that has opened it;
  `offline` nodes neither send nor receive; a node coming online catches up from any online replica; the writer being
  offline does not stop others reading. `openGroupStore` is idempotent and only for members.
- Tests (`jvmTest`): writer offline, reader catches up from the hub replica; late joiner reads history; removed member
  can no longer read; value over 64 KiB is rejected.
- **Done when:** a fake three-node mesh reproduces ADR-020 Spikes 1/4/6 in tests.

### Slice 2 — a Home x0x group (the large one) — **done 2026-10-06 on the fake mesh, see [ADR-021](../adr/ADR-021-home-x0x-group.md)**
2a. **ADR-021** with the decisions above, plus lifecycle rules (below). Fold the answers back into ADR-020.
2b. **Model:** `HomeGroup` table (`homeId` PK, `groupId`, `storeId` nullable) + `13.sqm`; `HomeGroupRepository`. The group id
    also goes into the Home's roster document so other members' devices learn it.
2c. **Create:** `HomeGroupService.ensure(homeId)` on the creator device: `createGroup`, record it, `openGroupStore`.
2d. **Join:** when a member (or a new device of a member) is admitted to a Home, an admin device mints
    `createGroupInvite` and sends it to that device over the existing Vesta join messages (extend the `HomeJoinService`
    message set, ADR-018/019); the joiner `joinGroup`s, waits for `active` in `getGroupMembers`, then `openGroupStore`.
    Retries follow ADR-017.
2e. **Roles:** promote devices of Vesta admins to x0x `admin` (`setGroupMemberRole`).
2f. **Leave/remove:** removing a member or a device removes each of their agents with `removeGroupMember` (rekeys, retry on
    `X0xApiException` as `DeviceSyncService` already does); leaving a Home leaves the group.
2g. **Existing Homes:** a Home with no group gets one the next time its creator's device runs; other members' devices
    join through 2d when they next see the group id. No data migration, the groups carry no data of their own.
- Tests on the fake mesh: create → admit second member → both open the store; remove a member → cut off; new device of a
  member joins; creator offline and an admin admits a joiner.
- **Done when:** two fake devices of two members are in one group with the store open, and removal rekeys.

### Slice 3 — mailbox entries (format, writer, reader) — needs slices 1 and 2c — **done 2026-10-06 on the fake mesh (`mailbox/`); not yet called by anything, slice 4 wires it**
- **Format** (`mailbox/MailboxEntry.kt`, commonMain or jvmAndroidMain next to Automerge): `version byte (=1)`, `documentId`
  (the existing `"$homeId:$feature"` ids), `payload` = `Document.encodeChangesSince(heads)`. Entry key
  `<writerAgentId>/<zero-padded sequence>`. Split a payload over 48 KB (value limit 64 KiB) into consecutive entries with
  a part counter; the reader reassembles before applying.
- **Writer state** (`MailboxWriterState` table: `homeId`, `documentId`, `lastDepositedHeads`, `nextSequence`) in `13.sqm`
  (or `14.sqm` if 2b took 13). `MailboxWriter.deposit(homeId, documentId)`: compute changes since the last deposited heads;
  nothing new → no entry; write; only then advance the state (a failed put retries the same range, which is idempotent).
- **Reader state** (`MailboxReaderState`: `homeId`, `writerAgentId`, `lastSequence`). `MailboxReader.fetch(homeId)`: list keys
  (a high-water mark per writer avoids refetching), get entries after it in order, reassemble parts,
  `applyEncodedChanges` through the document's own source (`receiveSync`-style entry point that takes a lock and
  republishes/persists, as `PantryDocumentSource.receiveSync` does), advance the mark only after a successful apply.
- **Verified (`EncodedChangesTest`):** `applyEncodedChanges` queues changes whose dependencies have not arrived and never throws for it, so ordering across writers doesn't matter. Saved heads are rebuilt with a `ChangeHash` subclass (the constructor is protected). **Built differently from the text above:** entries carry a random per-deposit number, the reader reads each writer's entries *without gaps* (a replica can deliver entry 5 before 4), and a failed deposit reuses its numbers. Entries a writer has deleted need slice 5's snapshots.
- (Original note:) what `applyEncodedChanges` does with changes whose dependencies have not
  arrived (queue or throw). If it throws, the reader must hold back later entries from that writer until earlier ones
  apply, and fall back to the next DM sync.
- Tests: round trip of a Pantry document through the fake store; idempotent re-application; an oversized change split and
  reassembled; a gap or reordered entries; writer restart continues its sequence.
- **Done when:** two fake devices converge through the store alone with no direct messages.

### Slice 4 — wire it into `DeviceDataSyncService` — needs slice 3 — **done 2026-10-06 on the fake mesh; deposits on local change, fetch each round, on presence and on foreground (`syncNow`)**
- Deposit on local change (`localChanges` flows already exist for Pantry/shopping/roster) and on `syncNow()`.
- Fetch on app start/foreground, on a timer while open, and on presence events; keep DM sync exactly as is (the mailbox is
  the catch-up path, ADR-020 "Phones").
- Respect the network policy (`X0xNetworkPolicy`) and an absent daemon (no group yet → no-op, never an error).
- Make sure DM and mailbox application cannot double-apply or fight (both end in idempotent Automerge merges). Built: the mailbox runs *outside* the service lock (store calls can be slow; the sources and the mailbox keep their own locks). Known cost: changes applied from others are included in this device's next deposit (a deposit is everything since its last heads), so each change is stored about once per writer; it is bounded by garbage collection (slice 5).
- Tests (`DeviceDataSyncServiceTest` style, fake mesh): A edits, goes offline, B comes online later → B has it; both
  directions; DM path still works when both are online; a device with no group is unaffected.
- **Done when:** the "never online together" scenario passes on the fake mesh, and the existing suite is green.

### Slice 5 — garbage collection — needs slice 3 — **done 2026-10-06 on the fake mesh (`MailboxWriter.compact`, snapshot jump in `MailboxReader`)**
- **As built:** the first snapshot is written at once (nothing to wait for), then one per 15 days if there are new entries; a writer's entries are deleted when a snapshot that covers them is 30 days old; only the newest snapshot is kept (older generations are deleted as soon as a newer one is complete). The snapshot is every document of the Home (one group of parts each, `snapshot/<writer>/<generation>/<chunk>`), so it covers the writer's entries across documents. Manifest last. A reader waits at a gap unless the writer's newest complete snapshot covers it, then applies the snapshot and moves on. A writer with no record of its numbers continues after the highest of its entries *and* its snapshots' coverage.
- (Original plan:) A writer deletes its own entries older than the window (default 30 days) **after** it has deposited a snapshot chunk set
  that covers them: `snapshot/<writerAgentId>/<generation>/<part>` plus a small manifest entry naming the generation and the
  heads it covers. Chunks are ≤48 KB.
- Reader rule: a device that has no mark for a writer, or whose mark is below the oldest remaining entry, loads the latest
  snapshot generation first, then entries after it. Old generations are deleted once a newer one is complete.
- Tests: log size stays bounded over a long fake history; a late joiner built from snapshot + tail equals one built from
  every entry; deletion by one writer reaches the replicas.
- **Done when:** a simulated six-month history leaves a store of bounded size and a fresh device still converges.

### Slice 6 — the hub in the app — needs slice 2 — **done 2026-10-06 on the fake mesh, except the invite-link item (deliberately not built, see below)**
- **Hub enrolment UI** (Home settings → "Always-on hub"): shows the copy-paste block for the hub machine, with a freshly
  minted invite for that Home:
  `x0x group join <invite>` then `x0x group store create <group id> mailbox`, plus the hub requirement (x0xd 0.46.3 or
  newer). Previews per the matrix convention; AAA tests for the view model.
- **Promotion:** when `getGroupMembers` shows a new `active` agent that was named as the hub (the user pastes or scans its
  agent id, or confirms it from a list of unknown members), the creator device runs `setGroupMemberRole(admin)`.
- **Roster:** record `hubAgentId` on the Home (decision 3). Show hub status (present in the group; last seen if x0x exposes
  it) on the same screen. Removing the hub removes it from the group (rekey).
- **Invite links:** ADR-018 lists up to 5 admin agents; put the hub first. **Not built, on purpose:** a join request is a Vesta direct message, and the hub runs no Vesta, so it can't answer one; naming it would only spend one of the five slots. What a hub does for joining is at the x0x group level (it is an admin, so it can admit a device to the group while the phones are away, tested in `HomeHubsTest`), not at the Vesta approval step, which stays with an admin's device (ADR-020 open points).
- **As built:** `HomeHub` (commonMain) / `HomeHubs` (`datasync`), `hub` key in the roster document, `HubViewModel` (polls while the screen is on show), `HubScreen` (previews in the light/dark x phone/tablet matrix), and an admin-only "Always-on hub" entry in the dashboard menu. The admin runs the printed commands on the hub machine, then picks the agent that appeared in the group from a list (pasting or scanning an agent id was left out: choosing from the group's unknown members cannot name the wrong machine by a typo). `HomeGroups` keeps the recorded hub an x0x admin and never touches it otherwise. Removing it removes it from the group (rekey, retried while x0x fetches a key package) and then from the roster; a removal that fails leaves it recorded. "Last seen" is not shown (x0x gives none).
- **Done when:** a hub can be added and removed from the UI against the fake mesh, and the UI matches the preview matrix.

### Slice 7 — real-daemon opt-in test — needs slices 3–4
- Like `DeviceSyncDaemonIntegrationTest`: skipped unless `VESTA_X0X_HUB_DIRS` lists three daemon data directories (two
  "phones" and one hub). Scenario: create a group, admit both, hub joins, promote it, all open the store; phone A
  writes and stops, phone B starts later and reads from the hub alone; then rekey by removing a member and repeat.
- Remember the spike gotchas: a named daemon's identity lives in `~/.x0x-<name>` and survives deleting its data
  directory; never `pkill -f` with a pattern in your own command line; wait for a daemon to exit before restarting it.
- **Done when:** the test passes locally against three real `x0xd` instances.

### Slice 8 — on-device verification and docs — needs slices 4–6
- Android emulator or a real phone against a host `x0xd` hub: create a Home on the phone, join the hub with the printed
  block, write on the phone, kill the app, read the same Home from a second profile/device later. Check the daemon stops
  on mobile data as before and the mailbox simply waits.
- iOS: confirm it compiles with 0.46.3; on-device test only if an iOS build is available.
- Docs: a "Run an always-on hub" guide (requirements, the copy-paste block, how to remove it, what the hub can read);
  update ADR-020 status to Accepted/Implemented, mark the implementation slices, and update CORE-EXPERIENCE/REQUIREMENTS
  where they mention sync limits.

## Dependency order

`0` and `1` can start at once. `2` needs `1`. `3` needs `1` and the group id from `2c`. `4` needs `3`. `5` needs `3`.
`6` needs `2`. `7` needs `3` and `4`. `8` needs `4`, `6` and `7`. A usable first milestone is `0 → 1 → 2 → 3 → 4`
(mailbox working between members who are already in a Home group, tested on the fake mesh).

## Not in this plan (recorded in ADR-020, build only if needed)

- Sealed entries with an app Home key and per-writer ordinary stores (for a hub that must not read the data).
- A mailbox for the `DeviceSync` group (user registry and Profile documents); those stay on DM sync.
- Letting a hub approve joins (the ADR says it should not: approval is the check on a leaked link).
- A hosted relay run by Veritas Labs.

## Risks

- **Slice 2 is new design.** The Home group touches joining, pairing, removal and roles, all of which are tested today
  only on the fake mesh and never in the real UI (project memory: invite links, ADR-018 slices 1–4). Budget for an ADR
  and for real-daemon tests of each lifecycle step.
- **Admin sprawl.** Promoting Vesta admins' devices to x0x admin lets any of them remove members and mint invites; make
  sure Vesta's own roles still gate the UI and that the creator-removal rule (ADR-012: replace the group) is handled.
- **Join latency.** A join commits only when an admin device is online; a hub that is an admin shortens this, but only
  after it is promoted.
- **Android lifecycle.** The daemon stops with the app, so deposits happen while the app is open; a change made and the
  app killed immediately can miss the deposit until the next open (the DM path has the same limit).
- **x0x version drift.** Phones embed 0.46.3, the hub runs whatever the user installed; state the minimum in the guide and
  check the hub's version if x0x exposes it.
