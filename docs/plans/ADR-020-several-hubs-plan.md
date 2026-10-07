# Implementation plan: several hubs, and a hub that is also a member's device (ADR-020 amendment)

Written 2026-10-06 so a fresh session can build the "Amendment (2026-10-06)" in
[ADR-020](../adr/ADR-020-syncing-without-overlapping-online-time.md). Read the amendment first, then
[the original plan](ADR-020-implementation-plan.md) for the conventions and the fake mesh. Nothing here is built.

## Goal

1. A Home can have **several hubs** (always-on x0x agents that replicate the `mailbox` store and are x0x admins), enrolled by
   different admins and on different machines.
2. A hub **may also be a member's device**: a desktop that runs Vesta can be chosen as a hub, and a headless hub whose
   machine later gets Vesta paired never loses either role by accident.

## State of the code (read 2026-10-06)

- **Roster document** (`automerge/HomeRosterDocument.kt`): one string key `hub` at the root; `readRosterHub`, `setRosterHub`
  (replaces), `clearRosterHub`. `HomeRosters` wraps them as `hub`, `setHub`, `clearHub`.
- **`HomeHub`** (commonMain) and **`HomeHubs`** (`datasync`): `enrolment`, `state` (one `HubState`), `candidates`,
  `useAsHub`, `removeHub(homeId)`. `useAsHub` returns `ActsForAMember` for any agent in a member's `devices`. `candidates`
  excludes devices, the recorded hub and this device. `removeHub` removes the agent from the group (rekey) then clears the roster.
- **`HomeGroups.adjustRoles(..., hub)`**: promotes admin members' devices plus the one hub to x0x admin and demotes plain
  members' devices; `removeDeparted` removes the devices of removed members from the group, with no knowledge of hubs.
- **UI:** `HubScreen`, `HubViewModel`, `HubUiState`/`HubItem`, an admin-only dashboard menu entry. Previews in the matrix.
- Tests: `HomeHubsTest`, `HomeGroupsTest`, `HubDaemonIntegrationTest` (opt-in, three real x0xd).
- Desktop Vesta uses the machine's one `x0xd` (`StandaloneX0xClient`), so a desktop hub and a desktop Vesta are one agent.

## Decisions to confirm before slice 2 (defaults given)

1. **Who may be flagged as a hub.** Flagging promotes the agent to x0x admin, which can remove members and mint invites. If an
   admin could flag any member's device, a plain member's device would be escalated. **Default:** a device may be flagged if it
   belongs to an admin member (including the flagging admin), or is not on the roster at all (headless). A plain member's
   device is refused with a new `HubResult.NotAnAdminDevice`.
2. **A departed member's hub.** When a member is removed or leaves and one of their devices is a hub. **Default:** security
   first: the device is removed from the group (rekey) and the flag is cleared, as for any departed device; the admin can
   enrol it again as a headless hub.
3. **Roster compatibility.** **Default:** a new map key `hubs` (agent id to `true`), read as the union with the legacy `hub`
   string; writes go to `hubs` only, and `clearRosterHub`/removal also delete the legacy key. Old devices ignore `hubs`, so
   they keep promoting the legacy hub only (harmless); a hub added after the change is simply not promoted by them, which only
   matters if an old device is the sole admin online. Alternative: dual-write the oldest hub into `hub` for a release.
4. **Own device as hub: how it is offered.** **Default:** a separate "Use this device as a hub" row on `HubScreen` on a
   device that is a desktop (a flag the platform sets); on Android it is hidden, since the embedded daemon stops with the app.
   Admins can still flag another of their own devices from the candidates list.

## Slices

### Slice 0: spike on real daemons (before any code) — **done 2026-10-06, all three confirmed, see ADR-020 Spike 8; the design does not change**

Extend the spike style of ADR-020 (Spikes 1-7), x0xd 0.46.x:

- **0a.** A member daemon that is also in the group: stop and restart it (no Vesta running). Is the `mailbox` store still open
  and replicating, with no `store create` call? (The run guide claims this for a headless hub; confirm for a daemon that joined
  through Vesta's invite.)
- **0b.** Two hubs plus two members. Remove one hub (rekey). Do entries written before and after stay readable on the other hub
  and by a member who was offline across it? (Spike 4 covers one rekey with one extra member.)
- **0c.** A device that is a member, then promoted to admin, then demoted: does `setGroupMemberRole` accept it, and does the
  promoted agent keep its store?
- **Output:** a "Spike 8" section in the ADR (written). If 0a had failed, a member's own always-on desktop needs the same `store create` call
  from Vesta (an "open the mailbox" action at startup, which `HomeGroups.openStore` already does while Vesta runs).

### Slice 1: roster document holds several hubs — no UI, no behaviour change yet — **done 2026-10-06**

- `HomeRosterDocument.kt`: `readRosterHubs(): Set<String>` (union of `hubs` keys and legacy `hub`), `addRosterHub`,
  `removeRosterHub` (also clears the legacy key if it equals the agent). Keep `readRosterHub`/`setRosterHub` only if something
  else needs them; otherwise remove them.
- `HomeRosters`: `hubs(homeId)`, `addHub`, `removeHub(homeId, agentId)`; emit `changes` like the existing hub methods.
- **Tests (AAA comments, `x?.let`):** add and read several; remove one leaves the rest; a legacy `hub` is read and removed;
  two replicas add different hubs concurrently and merge to both; an old document (only `hub`) migrates on first write.
- **Done when:** the document tests pass and `HomeHubs`/`HomeGroups` still compile against the old single-hub calls (adapted
  with `hubs().firstOrNull()` as a temporary shim, removed in slice 2).
- **As built:** one key per hub, `hub:<agentId> = true` (the roster's flat-key idiom, so concurrent adds merge to both), read as a
  union with the legacy `hub` scalar; `readRosterHubs`, `addRosterHub`, `removeRosterHub` in `HomeRosterDocument.kt`; `HomeRosters.hubs`,
  `addHub`, `removeHub`. `HomeRosters.hub`/`setHub`/`clearHub` remain as marked single-hub shims (`setHub` makes the agent the only
  hub) for `HomeHubs` and `HomeGroups`; slices 2 and 3 remove them, and `useAsHub` must then call `addHub`. Seven new document tests.
  Still open: how an older device reads a roster with `hub:` keys (it should ignore them).

### Slice 2: the hub API and `HomeHubs` — needs slice 1, decisions 1 and 2 — **done 2026-10-06 (decisions 1-4 confirmed at their defaults)**

- `HubState` becomes a list: `HubsState(hubs: List<HubState>)`, each with `agentId`, `inGroup`, and `isDevice` (is it also a
  roster device, and whose). `HomeHub.state(homeId): List<HubState>`.
- `useAsHub(homeId, agentId)`: add the hub; refuse a non-admin member's device (`NotAnAdminDevice`); keep `NotAnAdmin` and
  `NotInGroup`; **no longer `ActsForAMember` for admin-member devices**. Promote to x0x admin as now.
- `candidates`: group members that are neither a hub nor, unless decision 4 allows, a device; plus the caller's own other
  devices as a separate list (`ownDeviceCandidates`).
- `removeHub(homeId, agentId)`: remove the flag; remove the agent from the group (rekey, retried as now) **only if it is not
  an active device of any member**; a device that is also a hub keeps its group membership and its x0x admin role is
  reconciled by `adjustRoles` (slice 3). A failed group removal leaves the flag recorded, as today.
- **Tests (`HomeHubsTest`, fake mesh):** two hubs both recorded and promoted; remove one, the other still replicates and the
  rekey leaves the member able to read; choose an own desktop device as hub; refuse a plain member's device; remove a hub that
  is also a device and the device stays in the group; candidates list shape.

- **As built:** `HomeHub.hubs` (was `state`) returns `List<HubState>` with `isDevice`; `ownDeviceCandidates` added; `removeHub` takes an agent id;
  `HubResult.ActsForAMember` replaced by `NotAnAdminDevice` (a device of a removed member or of a member who is not an admin). `useAsHub` calls
  `addHub`; `removeHub` leaves the group only for an agent that is nobody's device. The `HomeRosters.setHub`/`clearHub` shims are gone.
  `HubViewModel` still shows the first hub only (slice 5). Five new `HomeHubsTest` cases (two hubs, remove one, own device as hub, hub-device
  not a candidate, another admin's device accepted and a plain member's refused). **Pulled forward from slice 3:** `HomeGroups.adjustRoles`
  now takes the set of hubs, so every hub is kept an x0x admin.

### Slice 3: `HomeGroups` roles and departures — needs slice 1 — **done 2026-10-06**

- `adjustRoles(..., hubs: Set<String>)`: admins = admin members' devices + every hub. A hub that is also a plain member's
  device is not demoted (`plain` already subtracts `admins`; add a test).
- `removeDeparted`: a departed member's devices are removed as now (decision 2) and any of them flagged as a hub also has the
  flag cleared in the roster, so the next round does not re-promote a removed agent.
- Un-flagging a hub that is also an admin member's device leaves it admin; un-flagging one that is not demotes it only if it
  is not otherwise an admin (the existing `plain`/`admins` diff already does this once the flag is gone).
- **Tests (`HomeGroupsTest`):** several hubs promoted, hub-and-device not demoted, a departed member's hub-device removed and
  unflagged, creator rule unchanged (a removed creator that is a hub still replaces the group).

- **As built:** `reconcile` (on an x0x admin device) clears the hub flag of any hub that is a departed member's device before it adjusts roles,
  so a departed hub is not re-promoted, and `removeDeparted` then takes the device out of the group as before; the creator rule still
  replaces the group. A shared `departedDevices` helper. Five new `HomeGroupsTest` cases (several hubs admin, a hub that is a plain member's
  device not demoted, an admin's device still admin after it stops being a hub, a departed member's hub device removed and unflagged, a removed
  creator that is also a hub). The slice 2 tests cover removing a hub that is a device (it stays in the group).

### Slice 4: pairing and unpairing guard — needs slices 1 and 3 — **done 2026-10-06: traced, no code change needed, three tests**

- Find where a device is added to a member's roster entry and where one is dropped (`HomeRosters` device publishing,
  `DeviceDataSyncService.publishRosters`, `SyncedDeviceRepository.remove`). **Read first; I did not trace unpair into the
  Home group in this plan**, and `removeDeparted` only covers whole departed members.
- Pairing an agent that is already a hub: allowed; both roles recorded, nothing removed.
- Unpairing a device that is also a hub: the device leaves the member's roster entry; the group membership stays because the
  flag stays. Unpairing a device that is not a hub follows today's path.
- **Tests:** pair after hub, hub after pair (both orders), unpair keeps the hub, remove-hub keeps the device.

- **As traced:** pairing is `HomeRosters.publish`, where a device claims only its own agent (`device:<agentId>`); the hub flag is the separate
  key `hub:<agentId>`, so the two roles never overwrite each other. Unpairing is the same `publish` releasing devices not in `pairedAgents`
  (deleting the claim only). Pairing a hub's machine therefore keeps both roles, and unpairing a hub-device leaves it a hub, in the group and an
  x0x admin. No guard was added: a plain member can pair a machine that is already an admin hub, which breaks decision 1's "only an admin's
  device" rule, but they already control that daemon, so nothing is gained (not confirmed with the owner). Three `HomeHubsTest` cases.
- **Found, not fixed (pre-existing, not specific to hubs):** a device released from a member's roster entry **stays in the Home's x0x group**.
  `removeDeparted` handles only whole removed members, and nothing removes a single unpaired device, so a lost or retired phone that is unpaired
  keeps its group key and can keep reading the mailbox. Confirmed with a throwaway test on the fake mesh (still a member after three reconcile
  rounds). A fix would remove group members that are neither on the roster nor hubs, after a grace window for a device that is joining; it needs
  its own decision, tests and a real-daemon check. **Fixed 2026-10-07** by [the unpaired-device plan](unpaired-device-gap-plan.md), which marks the device
  explicitly instead of guessing from the roster.

### Slice 5: UI — needs slice 2 — **done 2026-10-06; previews written but not rendered, UI not run in the real app**

- `HubUiState`/`HubItem` become a list; `HubScreen` shows each hub with its status and "Remove", a note on a hub that is also
  your device ("This is your computer; removing it as a hub keeps it in the Home"), and the candidates list; the enrolment
  block is unchanged and can be shown again ("Add another hub").
- A "Use this device as a hub" row on desktop (decision 4), only when this device is an admin's device and not already a hub.
- `HubViewModel`: poll as now; map `HubResult.NotAnAdminDevice` to a message.
- **Previews:** light/dark x phone/tablet grouped by device, with states: no hub, one hub, two hubs, a hub that is also this
  device. AAA tests for the view model.

- **As built:** `HubUiState` has `hubs`, `adding`, `ownDevices`; `HubViewModel` gets `onRemove(agentId)`, `onAddAnother()` and an `offerOwnDevice`
  flag, and makes the enrolment invite only when it is shown (no hub yet, or after "Add another hub"). `VestaApp(ownDeviceCanBeHub)` is true in
  `desktopApp`, false on Android and iOS (decision 4, as a parameter rather than a platform check). `HubScreen` lists a card per hub with its own Remove
  and confirmation (different text and a note for a hub that is also a device), an "Add another hub" button, and a "Your devices" section. Five new
  strings. Five new view-model tests, three new screen tests; four new previews (two hubs on phone, adding another on tablet, light and dark).
  **Not done:** the previews have not been looked at and the screen has not been run; Android and iOS compile not checked.

### Slice 6: real-daemon test — needs slices 2-4, opt-in like `HubDaemonIntegrationTest` — **done 2026-10-06 (`SeveralHubsDaemonIntegrationTest`, passed once against four real x0xd 0.46.0, ~7 min)**

- Two plain x0xd hubs plus two phones: a phone reads what the other left through either hub alone; remove one hub and the
  rest still works.
- A desktop-style member agent flagged as hub: stop and start its daemon with no Vesta, and a phone still reads through it
  (slice 0a, as a regression).
- ~5-10 min, three or four daemons; reuse the existing harness and temp dirs.

- **As built:** the daemon and phone helpers moved out of `HubDaemonIntegrationTest` into `DaemonHarness.kt` (`Daemon`, `Phone`, `until`, `enrolHub`,
  `leaveOneItemAndComeBack`), with `Daemon.restart()` (waits 20 s for the sockets) and `Phone.closeApp()` (stops the sync, leaves the daemon up).
  `SeveralHubsDaemonIntegrationTest` (`VESTA_X0X_HUBS_NAMES=a,b,h1,h2`): two hubs enrolled and chosen; B reads A's change from the hubs alone; hub 1
  removed (rekey), then B reads a second change through hub 2 alone; then A's own agent is made a hub (`ownDeviceCandidates`, `isDevice`), A's Vesta is
  closed with its daemon up, B writes, and after A's daemon is restarted with nothing reopening the store it still holds B's entries.
- **Found while re-running the original test (`HubDaemonIntegrationTest`, which re-enrols the removed hub):** plain x0x refuses a removed daemon's
  new `x0x group join` with `join_already_pending` (HTTP 409), or accepts it without the admin ever seeing the daemon, until the daemon has dropped the
  group (its `GET /groups/{id}/members` answers 404); in a hand test that took about two minutes. A leave then join worked once the group was gone.
  The original test failed on this twice and now waits for the 404 before re-enrolling, and passes. This is x0x behaviour, not Vesta's, but a user
  re-enrolling a removed hub straight away can hit it: slice 7's hub guide must say so.

### Slice 7: docs and verification — needs slices 2-6 — **docs and ADR done 2026-10-07; the by-hand run on two phones is not done**

- [Run an always-on hub](../product/RUN-A-HUB.md): several hubs, "your own computer can be a hub", that every hub reads
  everything, and the removal behaviour. [Syncing and joining](../product/SYNC-AND-JOINING.md) where it says "the hub".
- ADR-020: replace the amendment's "To check" list with the slice 0 and 6 results, update status.
- **By hand, on two phones:** two hubs, stop one, sync still catches up; the desktop with Vesta as hub.
- **As built (docs):** `RUN-A-HUB.md` gains "More than one hub", "Use your own computer as a hub", the wait before restarting x0x and before
  setting a removed computer up again, and a troubleshooting line; `SYNC-AND-JOINING.md` mentions several hubs. ADR-020's amendment is marked
  implemented, with the slice 0 and 6 results and what is still open.
- **Still to do, by hand:** two hubs on two phones (stop one, sync still catches up) and the desktop with Vesta as a hub, via the real screen.

## Dependency order

`0` first. `1` can start in parallel with `0`. `2` and `3` need `1`. `4` needs `1` and `3`. `5` needs `2`. `6` needs `2`-`4`.
`7` last. A usable first milestone is `0 → 1 → 2 → 3` (several hubs and a device-hub on the fake mesh, no UI).

## Not in this plan

- Sealed entries for a hub the household does not trust (still ADR-020 step 3).
- More than one Vesta user per machine (they share one x0x agent; unexamined).
- A hub approving joins.

## Risks

- **Old devices.** An older device ignores the `hubs` key and demotes nothing it should not, but it also never promotes the
  new hubs and can undo a promotion only for agents it believes are plain members' devices. Check with a mixed-version test
  once decision 3 is settled.
- **Admin sprawl.** Several hubs mean several x0x admins holding the group key. The refusal of a plain member's device (decision
  1) is the guard; keep it covered by tests.
- **Rekey cost.** Removing any hub rekeys the group, which interrupts members for a moment. Unchanged in kind, more frequent.
- **Unpair path (traced in slice 4).** Unpairing does not touch the Home group at all; see the open gap noted under slice 4.
- **x0x behaviour assumptions.** Slice 0 exists because "a restart keeps the store open" and "multi-hub rekey is safe" are
  inferred from Spikes 4 and 5, not tested.
