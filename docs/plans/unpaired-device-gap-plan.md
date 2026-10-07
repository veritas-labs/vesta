# Plan: an unpaired device must leave the Home's x0x group

Written 2026-10-07. Nothing here is built. Context: [ADR-014](../adr/ADR-014-home-membership.md) (the roster, §4 devices),
[ADR-021](../adr/ADR-021-home-x0x-group.md) (the Home's group), [ADR-020](../adr/ADR-020-syncing-without-overlapping-online-time.md) (the mailbox
lives in that group's store) and [the several-hubs plan](ADR-020-several-hubs-plan.md), slice 4, where the gap was found.

## The gap

Unpairing a device (Settings → Synced devices → remove) takes it out of the **DeviceSync** group and the pairing list. A Home's roster then stops
listing it (`HomeRosters.publish` releases the `device:` claim), so other devices stop sending it direct messages. But **nothing removes it
from each Home's x0x group**: `HomeGroups.removeDeparted` only handles devices of whole removed members. A lost or retired phone that was
unpaired therefore keeps its group key and keeps reading, and writing, every Home's mailbox. Confirmed on the fake mesh on 2026-10-07: after the
release and three reconcile rounds the device was still a group member.

Goal: when the user unpairs a device, it is removed from every Home group it is in (which rekeys, so it cannot read what follows), and it stays out
unless the user pairs it again. What the device already holds stays on it; nothing can change that.

## State of the code (read 2026-10-07)

- **Unpair:** `DeviceSyncService.removeDevice(agentId)` (commonMain) removes the agent from the DeviceSync group (replacing the group if it is the
  creator) and from `devices`. It is called only from `App.kt` (`onRemoveDevice`). Other devices learn of it by `reconcile`
  (`known - others` → `devices.remove`), which is **not** an explicit user action.
- **Pair:** a device is added to `devices` in `pairWith`, `acceptInvite`, `acceptHello`, `acceptRotate` and `reconcile`/`replaceGroupWithout`.
- **Roster:** `device:<agentId> = <memberId>` claims (`claimRosterDevice`, self-asserted: a device claims its own agent each round in
  `HomeRosters.publish`). `publish` also releases any claim of the user's other devices that is not in `pairedAgents`. That release is **derived**, not
  explicit: a device that has not synced its pairing list yet releases its siblings, who re-claim themselves next round. So a release alone cannot be
  taken as "the user unpaired this".
- **Group upkeep:** `HomeGroups.reconcile` (an x0x admin device only) runs `adjustRoles` and `removeDeparted`; the latter removes devices of removed
  members, retries each round when x0x refuses, and replaces the group when the creator is among them. Hubs are flagged by `hub:<agentId>` and are never
  demoted or removed by it (several-hubs slice 3).
- **Order in a round:** `syncEverything` runs `reconcile` *before* the direct-message pump, so an admin device can reconcile on a stale roster on a cold start.
- **A device joins a group only after it is on the roster:** `answerRequest` refuses an invite unless the asker is in the inviter's roster devices.
  So "in the group but not on the roster" also describes a device whose claim has not reached a *different* admin device yet.

## Why not "remove whoever is in the group but not on the roster"

Tempting, and wrong: (1) a device that just joined can be in the group before its claim reaches a second admin device; (2) an admin device that wakes
with a stale roster would remove legitimate devices before its first sync; (3) `publish`'s derived release would then evict a device that merely had a
brief pairing-list mismatch. Each is a wrongful removal of a working device, which is worse than the gap. The fix must hang off an **explicit** user
action instead.

## Design

An explicit **tombstone** in the roster, written when the user unpairs, honoured by admin devices.

1. **Roster key** `gone:<agentId> = <memberId>` (flat, like `device:`), written by the device where the user pressed remove. The claim `device:<agentId>`
   is left in place: an admin honours the tombstone only when the agent's claim names the **same member** as the tombstone, so one member's device
   cannot tombstone another member's. (Members are trusted not to forge claims, as today; see ADR-014. This is not a defence against a malicious member.)
2. **Reads:** `readRoster()` leaves a tombstoned agent out of its member's `devices`. This matters because a released device that has merged the
   release (it can, through the mailbox, until it is removed from the group) writes its claim again, and that later write survives the merge: it would
   reappear as a peer and be invited again. (Slice 0a found that a put of the *same* value concurrent with the release does not survive: the delete wins.
   Only a put made after seeing the release does.)
3. **Removal:** in `HomeGroups.reconcile`, next to `removeDeparted`, an x0x admin device removes every active group member that is tombstoned, is not a
   hub, and is not itself; a creator among them replaces the group, exactly as for a departed member. A failed removal is retried next round.
4. **Explicit re-pair clears it:** `gone:<agentId>` is deleted only when a device is newly added to the pairing list for that agent (not on every
   round), through a callback from `DeviceSyncService`. A device that already lists the agent does not clear it, so a stale sibling cannot undo an unpair.
5. **The unpaired device:** removed from the group, its `HomeGroups` forgets the group and asks for an invite; admins refuse, as it is no longer in
   `activeDevices`. It cannot re-enter through direct messages either, since its senders are no longer peers.
6. **Hubs:** a tombstoned agent that is a hub is left in the group: the hub flag, not the claim, is why it is there (several-hubs slice 4). Removing it
   as a hub stays a separate action.

Devices unpaired **before** this change have no tombstone and stay in their groups. Slice 7 gives admins a way to clean those up by hand.

## Decisions to confirm before slice 3 (defaults given)

1. **Tombstone, not "stray" detection.** Default: the tombstone design above (explicit, race-free for joins and stale devices).
2. **Re-pair clears the tombstone only when the agent is newly added** to the pairing list. Default yes. The alternative (clear whenever a device lists
   the agent) is simpler but lets a stale sibling undo an unpair.
3. **Unpair also acts on a plain-member device:** the unpairing device need not be an x0x admin; it writes the tombstone and an admin device removes.
   Default yes. If no admin device of a Home is ever online, the device stays in until one is: say so in the docs.
4. **Legacy cleanup:** default yes, build slice 7 (an admin-visible list of group members that are neither claimed nor hubs, each with Remove). It covers
   devices unpaired before this change and anything else unexpected, and it removes nobody without a person choosing.

## Slices

### Slice 0: confirm two assumptions before building — small

- **0a. Done 2026-10-07**, in `HomeRosterDocumentTest`: a device that claims itself again after merging its release is back on every replica (so a
  release alone does not keep it out, which is what justifies the tombstone filter on read); a same-value put concurrent with the release does **not**
  survive (the delete wins, which my first guess, that a concurrent set beats a delete, had wrong); a device that still holds its claim writes nothing.
- **0b. Done 2026-10-07**, written up as ADR-020 Spike 9. An admin removes a member whose daemon is off at once, and on return that daemon has no group and can
  neither read (old or new entries) nor write. But it can be listed as an active member again with any unused valid invite (7-day expiry, no use limit),
  though it still could not read within three minutes. So: removal of a tombstoned agent must run **every round** (the design already does), and the
  invites Vesta mints should be short-lived (new slice 2b below).

### Slice 1: roster tombstones — needs 0a — **done 2026-10-07**

- `HomeRosterDocument.kt`: `markRosterGone(doc, agentId, memberId)`, `clearRosterGone(doc, agentId)`, `readRosterGone(): Map<agentId, memberId>`;
  `readRoster` filters a tombstoned agent whose claim names the tombstone's member out of `devices`.
- `HomeRosters`: `markGone(agentId)` and `clearGone(agentId)` over every Home that lists the agent (using the current member id), emitting `changes`.
- Header comment of the document updated (the key list).
- **Tests (AAA):** write and read; a tombstone for another member's device is ignored; the concurrent re-claim case (0a) still leaves the device out;
  clearing; `devices()` and `activeDevices` no longer list it; two replicas tombstoning at once merge.

- **As built:** `gone:<agentId> = <memberId>` in `HomeRosterDocument.kt` (`markRosterGone`, which does nothing unless the roster has a claim for that agent
  naming that member; `clearRosterGone`; `readRosterGone`), and `readRoster` leaves a device out of `devices` when a tombstone names the same member as its claim.
  `HomeRosters.markGone(agentId)` and `clearGone(agentId)` act on every Home and emit a change for each one that changed. Seven document tests (including the
  case where the device claims itself again) and four `HomeRostersGoneTest` tests. Nothing calls them yet: slice 2 acts on the tombstone and slice 3 writes it.

### Slice 2: removal in `HomeGroups` — needs slice 1 — **done 2026-10-07 on the fake mesh**

- `reconcile` (admin branch): compute the tombstoned agents (claim matches, not hubs, not self) and remove them like `removeDeparted` (retry each round;
  creator → `replaceGroup`); clear nothing here.
- **Tests (fake mesh, `HomeGroupsTest`):** an unpaired device is removed and loses the store; unpaired creator replaces the group and the others follow;
  an unpaired device that is a hub stays; a device never tombstoned is never removed, even when absent from this admin's roster (a joining device, a
  stale roster); the tombstoned device cannot get a new invite; re-pairing clears it and it can join again; a failed removal is retried.

- **As built:** `readRosterGoneDevices()` (tombstones whose claim still names the marking member) and `HomeRosters.goneDevices(homeId)`; in
  `HomeGroups.reconcile` the admin branch passes `departed + (goneDevices - hubs)` to `removeDeparted`, so removal is every round, retried, and a creator among
  them replaces the group. `answerRequest` needed no change: the tombstoned device is already out of `activeDevices`. Seven new `HomeGroupsTest` cases (removal and
  lost store; a never-marked device is not removed even when absent from the roster; an unpaired hub stays; one that rejoins with an invite is removed again; not
  invited back when it asks; re-pairing lets it join; an unpaired creator replaces the group). Disabling the removal makes four of them fail; the other three
  are guards.

### Slice 2b: short-lived invites — needs a look at `X0xClient.createGroupInvite`, then small

- Spike 9 found that a removed device can re-register with any unused invite, and Vesta's invites live a week. Pass a short expiry (`--expiry` on the CLI;
  check the REST body) for the invites Vesta mints: a device invite (`answerRequest`, answered at once), and the hub enrolment invite (shown on screen
  while the admin sets up the machine; an hour is plenty). Keep the 7 days only if a flow really needs it. Tests: the invite carries the expiry; a hub
  enrolled after the expiry needs fresh commands (the screen already makes them on demand).

### Slice 3: wire it to the user's action — needs slices 1 and 2 — **done 2026-10-07 on the fake mesh**

- `DeviceDataSync` (commonMain) gains `suspend fun deviceUnpaired(agentId)` and `devicePaired(agentId)`; `DeviceDataSyncService` implements them with
  `rosters.markGone` / `rosters.clearGone` and asks for a sync round.
- `DeviceSyncService` gets two optional callbacks (default no-op): called after `removeDevice` succeeds, and after a device is **newly** added by
  `pairWith`, `acceptInvite`, `acceptHello`, `acceptRotate` (not by `reconcile`'s derived adds).
- `App.kt` passes `deviceDataSync` through when it builds `DeviceSyncService`.
- **Tests:** `DeviceSyncServiceTest` for the callbacks (called on explicit removal and new pairing; not on a derived removal or an already-known device);
  an end-to-end test on the fake mesh from `removeDevice` to the device leaving the Home's group.
- **As built:** `DeviceSyncService` takes `onDeviceUnpaired` and `onDevicePaired` (default no-op; a listener that throws never fails the pairing). The first is
  called after `removeDevice` succeeds, including when this device has no group. The second after `pairWith`, `acceptInvite` and a `Hello` from a device not already
  in the list, and not for `reconcile`'s or `replaceGroupWithout`'s derived adds, nor `acceptRotate`. `DeviceDataSync` gained `deviceUnpaired` and `devicePaired`
  (`DeviceDataSyncService` calls `rosters.markGone` / `clearGone`), and `App.kt` passes them. Tests: seven in `DeviceSyncServiceTest` for the callbacks, two in
  `DeviceDataSyncServiceTest`, one of them end to end (two devices paired for real, a shared Home and group, `removeDevice`, the device leaves the group); making
  `deviceUnpaired` a no-op fails both. A device removed from the DeviceSync group cannot clear its own mark with a greeting: `acceptHello` ignores a sender that is
  not an active member of that group.

### Slice 4: real-daemon test — needs slices 1-3, opt-in like the others

- Two phones, a hub, and a third device of one phone's user: unpair it; it is removed from the group (real rekey), cannot read an entry written after,
  and a new invite request from it is refused. Reuse `DaemonHarness.kt` (new env var for four names).

### Slice 5: by hand on the emulators — needs slice 4

- Pair two emulators as one user's devices, create a Home on one, unpair the other from Settings, check it can no longer read the mailbox. (Pairing UI
  exists; it has not been driven on emulators yet, so this may find its own problems.)

### Slice 6: docs — needs slices 1-3

- `SYNC-AND-JOINING.md` (or a short new page): what unpairing does, what it cannot (data already on the device), and that an admin device must be online to
  finish it. ADR-014 §4 and ADR-021: the tombstone and the rule. Update the several-hubs plan's note under slice 4.

### Slice 7: legacy and strays, by hand — optional, needs slice 2

- Members screen (admins only): "Unknown devices": active group members that are neither a roster device nor a hub, each with Remove (confirmation, names
  the agent). Uses `getGroupMembers` and the roster; removal reuses the group removal path. Previews in the matrix; AAA tests for the view model.

## Dependency order

`0` is done. `1` needs `0a` (done); `2` needs `1`; `2b` is independent of `1`-`3` and can go any time after 0b; `3` needs `1` and `2`; `4` needs `1`-`3`; `5` needs `4`; `6` needs `1`-`3`;
`7` needs `2`. A usable first milestone is `0 → 1 → 2` (the rule works on the fake mesh), then `3` makes it happen from the app.

## Not in this plan

- A device that holds the user's identity and is **online and hostile**: the roster is trust-on-members, a device re-asserts its own claim, and
  per-device keys are a different project. This plan stops it from reading new data and from re-entering by direct message; it does not make the roster
  tamper-proof.
- Wiping data already on a lost device (not possible from here).
- Removing a device from the DeviceSync group itself: already done by `removeDevice`.
- Changing the derived release in `HomeRosters.publish`; it stays, and no longer matters for safety once removal follows only the tombstone.

## Risks

- **Wrongful removal** is the failure to avoid. The design removes only on an explicit tombstone with a matching claim, never on absence from the roster.
  Slice 2's tests include the cases that would have gone wrong with the simple rule.
- **Tombstone left behind:** if a re-pair path is missed in slice 3, a re-paired device keeps being removed. List all five add paths and test each.
- **Admin availability:** removal waits for an x0x admin device of the Home. A Home whose admins are all offline keeps the device in meanwhile.
- **Creator unpaired:** the group is replaced, which makes every other device leave and ask for an invite (the cost already accepted for a removed creator).
- **x0x timing:** removing a member whose daemon is off can need retries; and a removed daemon is refused a re-join for a couple of minutes (found in
  the several-hubs slice 6). Fine for removal, to mention in the docs for re-pairing.
- **Two sources of truth for "paired":** the DeviceSync list and the roster claims already disagree transiently. This plan avoids depending on that by
  keying on the explicit action, not the lists.
