# ADR-021: One x0x group per Home

**Status:** Accepted (2026-10-06), implemented the same day on the fake mesh only. Prerequisite of the mailbox in
[[ADR-020]] (slice 2 of its [implementation plan](../plans/ADR-020-implementation-plan.md)). Revives the "one x0x group per
Home" of [[ADR-005]], which was never built; [[ADR-014]] (roster) and [[ADR-018]]/[[ADR-019]] (joining) are unchanged.

## Context

The mailbox needs an x0x **group store**, and a group store needs a group. Vesta has no group per Home: the only group is
the per-person `DeviceSync` group ([[ADR-012]]). Homes sync over direct messages between the devices their roster lists.

## Decision

1. **One x0x group per Home** (`private_secure`), not one per household. Homes are the unit of membership ([[ADR-014]]).
   The group carries no Home data itself. It exists for its stores, and for x0x to replicate and encrypt them.
2. **The roster document names it.** A flat key `group` in the Home's roster ([[ADR-014]]) holds the group id, so every
   member's device learns it by the existing roster sync. A concurrent write by two devices resolves to one value; the
   other device abandons its group.
3. **Who creates it.** The *founder*: the device of the earliest-joined admin member (ties by member id), the first time it
   reconciles. Existing Homes get a group the next time the founder's device runs. No data migration.
4. **Who joins, and how.** Pull, not push. A device that has the Home but no group sends `homegroup.invite.request` to the
   devices of the Home's admin members every sync round. An admin device that is an x0x admin of the group answers with
   `homegroup.invite` (an x0x invite link, in an end-to-end encrypted direct message). The asker joins, waits until it is
   `active`, records the group and opens the `mailbox` store. Both sides check the other is a device the roster lists for a
   member who has not been removed. Retries are the sync round ([[ADR-017]]). This one path covers a new member, a new device
   of a member, an existing Home that gets a group, and a device whose group was replaced.
5. **Roles.** Devices of Vesta admin members are promoted to x0x `admin` by any x0x admin that sees them active, and devices
   of members demoted to plain member are demoted (never the creator, never oneself). Agents that are in the group but on no
   member's roster entry are left alone: promotion and demotion only touch agents the roster names. The hub ([[ADR-020]], slice 6)
   is one the roster names in its own `hub` key, and is kept an x0x admin; it is removed only by an admin choosing to.
6. **Removal.** A member removed from the roster ([[ADR-014]]) or who left has their devices removed from the group by any
   x0x admin, which rekeys. So does a device the user **unpaired** (2026-10-07; [plan](../plans/unpaired-device-gap-plan.md)): marked `gone:` in the roster by
   the device where they pressed Remove ([[ADR-014]] §4) and removed by the same diff, each round, so one that gets back in with an unused invite goes
   again. This is a diff each round (group members that are devices of removed members, or marked), so a removal
   that failed (the key package is pending, or the admin was offline) is retried. If the device to remove is the group's
   **creator**, x0x won't let another admin remove it, so the admin **replaces** the group as [[ADR-012]] does: a new
   group, written to the roster; every other device sees the id change, leaves the old group and asks for an invite.
   A Home deleted on a device (left, or removed) makes that device leave its group.
7. **Where it is kept.** A `HomeGroup` table (`homeId`, `groupId`, `storeId` nullable), local and unsynced. `storeId` is set
   when `POST /groups/{id}/stores {"name":"mailbox"}` succeeds. A daemon that cannot open group stores (embedded x0x older
   than 0.46) leaves it null and retries each round.
8. **Garbage-collection window:** 30 days behind a snapshot ([[ADR-020]] plan, slice 5). **Write policy:** deposit to the
   mailbox whenever the Home has an open store, hub or no hub (slice 3). **The hub** is recorded in the roster later (slice 6).

## Consequences

- Every device of every member is in the group, so the group's size is the Home's device count. A removed member's devices
  keep what they replicated before the rekey and are cut off afterwards ([[ADR-020]] Spike 1).
- Any x0x admin can remove members and mint invites, as can the creator. Vesta's own roles still gate the UI and the
  roster; the group follows the roster, not the other way round.
- Joining the group needs an admin device online, as joining a Home does. A hub that is an x0x admin shortens that.
- A Home whose founder never opens the app on a version with this change has no group; nothing else depends on it.
- No message format that existed before changes. The two new messages are in their own `homegroup.` family and are ignored by
  older versions, which are then simply not in the group.
