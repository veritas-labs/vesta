# ADR-014: Home membership — people are members, devices are only how they connect

**Status:** Accepted (2026-10-02): `memberId` is the Profile id, and the unbound-claims limitation is accepted for v1. Not yet implemented. Builds on [[ADR-005]] (one Automerge document and one x0x group per
Home), [[ADR-012]] (a person's devices form the `DeviceSync` group) and [[ADR-013]] (those devices sync user data). The
sync *between* the members of a Home is still to come and is not designed here; this ADR only says who the members are.

## Context

The Members tab shows nothing yet, and the creator of a Home does not appear in it. Two facts make
the obvious implementation wrong:

- **x0x knows agents, not people.** `GET /groups/{id}/members` returns agent ids, and every device runs its own agent
  ([[ADR-012]]: no shared owner key). A person with a phone, a tablet and a laptop paired together has three agents, and
  all three must be in the Home group to receive its sync ([[ADR-005]]: group roster = who you sync with).
- **The product says a member is a person.** CORE-EXPERIENCE §5: "a Home's members are the app users (Profiles) who
  joined it", with a name, avatar and role badge, and the creator is "the sole member with Admin role". Listing the
  roster as it comes back from x0x would show the same person once per device.

The code today has a `HomeMember` table keyed by `profileId`, and a `Profile` table, but production code writes neither:
the Members tab is a placeholder, and the Home registry ([[ADR-013]]) records Homes without membership. REQUIREMENTS
parks "x0x agent-id linking". This ADR un-parks it, as the owner decided on 2026-09-09 for device pairing.

## Decision

### 1. A member is a person, identified by a `memberId` that every one of their devices shares

`memberId` is the id of the person's **Profile**. A Profile is already "who is using the app right now" and already maps
to one x0x identity per device (`x0xInstanceName`), so a person on a shared tablet is one Profile with its own agent, and
one `DeviceSync` group, exactly as [[ADR-012]] has it.

For all of a person's devices to carry the same `memberId`, the Profile (id, display name, avatar) is replicated
across the `DeviceSync` group as a per-user Automerge document `user:profile`, using the [[ADR-013]] mechanism. A device
that is paired adopts the existing Profile rather than creating a new one. This is a prerequisite and a change to
pairing: today pairing carries no identity.

Rejected as the identity:

- **The agent id** (one row per device): the exact problem to avoid.
- **x0x `user_id` / owner key**: [[ADR-012]] showed it cannot be shared between embedded daemons.
- **The first device's agent id**: dies with that device. Losing the original phone must not erase the person.
- **The `DeviceSync` group id**: private to the person, and it changes when the group is replaced ([[ADR-012]]).

### 2. The roster lives in the Home's document

The Home's Automerge document gets one top-level entry per member, keyed by `memberId` (no wrapping `members` map, for
the concurrent-creation reason in [[ADR-013]]):

```
member:<memberId> = {
  name, avatar,                 // copied from the Profile, refreshed when it changes
  role: "admin" | "member",
  joinedAt,
  devices: { <agentId>: { addedAt } }   // the agents that act for this member in this Home
  removed: bool                // tombstone, so a departure propagates
}
```

Each device writes only its own `devices[agentId]` entry; only an admin writes another member's `role` or `removed`.
Because the roster is part of the document, a member who joins later, or is offline, gets it through ordinary sync, and
it needs no extra protocol.

The x0x group stays what [[ADR-005]] made it: invite, admission and the list of agents to sync with. The Home document
is the authority for **who the people are**; the group is the authority for **which agents may take part**. They are
reconciled (§4), never merged into one source.

### 3. The UI shows members, not agents

- One row per non-removed `member:*` entry: avatar, name, role badge. The current Profile's row is pinned first and
  marked "You".
- A person with several devices is one row. Devices are not shown on other members' rows. On "You", a quiet secondary line
  ("This device and 2 others") leads to Settings → Synced devices.
- Creating a Home writes the creator's `member` entry (role `admin`, this device's agent) in the same step that
  creates the Home and its document, so the creator is a member from the first frame, offline, before any group exists.

### 4. Devices join and leave a member automatically

- **A new device of an existing member.** Pairing ([[ADR-012]]) already syncs the user's Homes to the new device
  ([[ADR-013]]). For each Home it learns, the new device adds its `agentId` to its member's `devices`. A device already
  in that Home's group invites it into the group over the trusted `DeviceSync` channel, and the group role of every
  device of an admin member is `admin` (x0x roles are per agent; the document's `role` is what the UI shows).
- **A device removed from `DeviceSync`.** Every remaining device deletes that agent from `devices` in each Home and
  removes the agent from the Home group (which rotates the keys). The member remains, with fewer devices. If it was the
  member's *last* device the member stays too: a person with no reachable device is still a member until they leave or
  an admin removes them.
- **A member leaving or being removed.** `removed` is set and all of their agents are removed from the group. The last
  admin cannot leave without naming another (CORE-EXPERIENCE §5).

### 5. Mapping an agent to a member

A device applies sync from a Home peer only if the sender is a verified, active agent in the Home group
([[ADR-005]], [[ADR-013]] ACL) **and** appears in some member's `devices`. An agent that is in the group but in no
`devices` list is shown as nothing. It is a joiner whose claim has not synced yet, and it is retried on the next round.

## Consequences

- The creator appears as a member immediately, once, however many devices they pair.
- Fan-out cost scales with devices, not members; NFR-2's "50 members" is 50 people, which can be 100–150 agents. Check
  that budget when Home sync is built.
- **Claims are not cryptographically bound.** After Automerge merges, who wrote an entry is lost, so a malicious Home
  member could write another member's `memberId` into their own device list and be treated as that person. This ADR
  accepts that: Home members are trusted at household level, and the damage is impersonation within a Home, not access
  to data (they were already admitted to the group). Hardening would be a claim signed by an existing device of the
  member before it is accepted, which needs an x0x sign/verify call that is not used today.
- The Profile document adds a second user document to sync; Profile edits (name, avatar) reach every Home through each
  device refreshing its `member` entry. Two devices renaming concurrently is last-writer-wins.
- The `HomeMember` table keyed by `profileId` becomes a local projection of the document (like `Home` in [[ADR-013]]),
  not a source of truth, and needs a column for the agent list or a sibling table. `ON DELETE CASCADE` from `Profile`
  would delete a person's membership when a Profile is deleted on a shared device; the cascade must not cross devices.
- REQUIREMENTS "x0x agent-id linking is parked" and the Non-Goals/Open Questions notes need updating.

## Decisions on the open questions (2026-10-02)

1. **`memberId` = Profile id.** The Profile is synced across a person's devices (§1).
2. **Claim hardening: accepted as a v1 limitation** (see Consequences). Signed claims remain the later fix.

Still open, not blocking:

- **Pending joiners.** A newly admitted agent with no claim yet shows nowhere (§5). Should admins see a "joining…" row?
- **A Profile that exists on two unpaired devices.** The same person who never paired joins a Home twice and is two
  members. Merging is out of scope; pairing is the way to become one member.
- **Presence.** "Online" for a member would be true when any of their devices is. Not designed here.

## Implementation notes

**Slice 1 (2026-10-02): the creator is a member.** `HomeRepository.create` inserts the current Profile as the Home's
`admin` member. The Members tab lists members (the current user first, marked "You", with a role badge).

Deliberately narrower than the Decision above, to be filled in by the slices that follow:

- The roster is the local `HomeMember` table only; the Automerge roster document (§2) and its sync arrive with Home sync.
  Migration `8.sqm` re-keys the table by `memberId` (no foreign key to `Profile`) and adds `name` and `avatarResourceId`.
- No `devices` yet (§2, §4): the agent id can't be read offline, and nothing needs it until the group exists.
- There is no Profile setup or switching in the app yet, so `ProfileRepository.current()` creates one Profile on first use,
  named after the device. Profile sync across devices (§1) is the next prerequisite for a second device to be the same
  member.

**Slice 2 (2026-10-02): the Profile syncs between paired devices.** A per-user Automerge document `user:profile` (one
entry per Profile id: name, avatar, `createdAt`; no wrapping map) rides the [[ADR-013]] sync. A device publishes its
Profile into it and, after each merge, adopts the **canonical** one: the oldest `createdAt`, lowest id on a tie. Two
devices that each made a Profile before pairing therefore converge on one id. Adoption renames the local `Profile`
row's id and re-keys its `HomeMember` rows, so Homes created before pairing follow. It resolves ADR-014 §1's
prerequisite.

- `createdAt` is when the entry was first published (the first run with this feature), not when the Profile row was
  made: `Profile` has no timestamp column. The "older device wins" rule is therefore approximate for devices upgraded
  at different times, which only matters in that the surviving name may be the other device's.
- Names are device names until Profile setup exists, so after pairing every device shows the winner's.
- A cleared avatar isn't propagated (nothing can clear one yet).

## References

- [[ADR-005]], [[ADR-012]], [[ADR-013]].
- CORE-EXPERIENCE §1b, §3 (creator is the sole Admin member), §5 (Member List); REQUIREMENTS FR-1, FR-4, FR-5, NFR-2.
- `HomeMember.sq`, `Profile.sq`, `MembersScreen`, `SyncedDeviceRepository`, `DeviceDataSyncService`.
