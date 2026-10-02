# ADR-015: Joining a Home with a link

**Status:** Accepted (2026-10-02). Implemented. Builds on [[ADR-014]] (a Home's members and their roster), [[ADR-012]]
(the pairing flow this copies) and [[ADR-005]] (one Home, one document set). Replaces the 6-digit invite code of
CORE-EXPERIENCE §4 and `docs/design/join-home.md` as the *mechanism* (the screens' intent still applies).

## Context

[[ADR-014]] made a Home sync between its members' devices, but nobody could become a member: the Members tab's Invite
button did nothing and "Join a home" on the picker did nothing. The product spec describes a 6-digit code an admin reads
out and the joiner types. That needs somewhere to look a code up, and a Vesta Home lives only on its members' devices.

## Decision

**A link, as in device pairing.** The person joining opens "Join a home". It shows a **join link**
(`vesta://join/<token>`, also as a QR code): their device's x0x agent id, their member id (Profile id, [[ADR-014]]),
their name and a 5-minute expiry. It carries no secret. An admin of the Home opens Members, Invite, and pastes the link.

**Direct messages only, no x0x group.** [[ADR-005]] gave each Home an x0x group for admission and discovery. That is not
used here: a Home syncs only with the devices its roster lists ([[ADR-014]] slice 6), and every direct message is
end-to-end encrypted to its recipient, so a group would add a second source of truth, a 90-second admission wait and key
rotation machinery for no access control the roster doesn't already give. Removing a member means ceasing to list their
devices; nothing needs re-keying because nothing is group-encrypted. A group can be added later if group messaging is.

**The exchange** (`HomeJoinMessage`, `HomeJoinService`):

1. Admin's device checks the user is an admin of the Home, the link is unexpired and its member isn't in the Home already.
2. It sends **Invite** (Home id, name, shape, creation time, and the inviter's member id and name) to the link's agent,
   again every 10 s (a message to a peer that isn't listening yet can vanish) for up to 90 s.
3. The joiner accepts only while it is showing an unexpired link, only from a `verified` sender, only for a Home it
   doesn't have, and not from itself (same member id). It creates the `Home` row and an **empty** Pantry (the admin seeded
   the real one, [[ADR-013]]), records the Home in its own registry so its other devices get it, writes the inviter
   (admin) and itself (member) into the Home's roster, and answers **Accepted**.
4. On **Accepted** from the link's agent the admin writes the joiner into the roster. From then on the roster's devices are
   each other's peers, and the Pantry and roster sync ([[ADR-014]] slice 6).

The joiner admits the inviter itself because a device only syncs a Home with agents its roster lists, so it must be told
who invited it. The admin admits the joiner only once it has accepted, so an invitation nobody takes leaves no phantom member.

**UI.** The picker's "Join a home" tile opens the link screen (it reuses the pairing QR screen with its own copy) and
opens the Home on success. Members' Invite button opens a screen to paste a link, showing progress, success, or why it
failed (not a link, expired, already a member, not an admin, no answer).

## Consequences

- Joining needs both devices online together and the joiner on the join screen. An offline joiner can't be invited later
  from a stale link (5 minutes).
- Trust at admission rests on the humans, as with pairing: whoever pastes a wrong link invites the wrong agent, who then
  receives the Home's contents. The first valid invitation received while the link is showing wins.
- A member who is already in the Home on another device can't join again (same member id): they pair the device instead.
- The joiner's Pantry fills from the admin's by sync; until then it is empty. Only the admin's device seeds the document.
- Admin-only invitation is enforced on the inviting device, and roster claims aren't signed ([[ADR-014]]).
- JVM and Android only (Automerge). No invite for iOS yet.
- CORE-EXPERIENCE §4's code expiry, rotation and "max pending codes" don't apply: a link is single-use by construction.

## References

- `JoinLink`, `HomeJoinMessage`, `HomeJoining`, `HomeJoinService`, `HomeInviteViewModel`, `HomeInviteScreen`, `JoinHomeFlow`;
  `DeviceDataSyncServiceTest` (join end to end over the in-memory mesh).
