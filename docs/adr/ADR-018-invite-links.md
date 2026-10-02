# ADR-018: Invite links — joining a Home without both devices online together

**Status:** Proposed (2026-10-02). Not implemented. Builds on [[ADR-015]] (joining with a link), [[ADR-014]] (roster) and
[[ADR-017]] (retrying messages to offline devices). Adds a second way to join; [[ADR-015]]'s flow stays.

## Context

[[ADR-015]]'s join link is made by the *joiner* and lives 5 minutes: the joiner must be on the join screen while an admin
pastes it, and both devices online for the exchange. A Home lives only on its members' devices and an x0x direct message
to an offline agent can vanish, so someone must always be online and retrying. What can go is the need to coordinate a
moment: if each side retries in the background, the handshake completes whenever both happen to be online.

## Decision

**The admin makes the link, the joiner redeems it later.**

1. **Create.** An admin chooses Members → Invite → "Create invite link". The link, `vesta://invite/<token>`, carries the
   Home id and name, a random secret, an expiry (default 7 days, at most 30) and the agent ids of the Home's admin devices at that
   moment (capped at 5). The admin shares it any way they like, as text or a QR code.
2. **Record.** The Home's roster document gets flat `invite:<sha256(secret)>:…` keys (`createdBy`, `expiresAt`, `maxUses`,
   `revoked`), following the roster's convention ([[ADR-014]]). Uses are one `usedBy:<memberId>` key per admitted member
   rather than a counter, because two admin devices incrementing a counter at once would lose one on merge. The secret
   itself is never stored. Because the entry syncs, any admin
   device holding the document can validate the link, and revoking from any admin device reaches the others.
3. **Redeem.** The joiner opens the link. The device saves a pending join (Home id, secret, the admin agents, expiry) and
   sends `homejoin.request` (secret, member id, name, agent id) to each listed agent, again every sync round until it gets
   an answer or the link expires.
4. **Approve.** An admin device that receives a request checks that it is an admin of that Home, the invite exists, isn't
   expired or revoked, and that the requester isn't already a member. If so it stores a **pending request** locally and
   shows an approval card ("Sam wants to join Home X", as [[ADR-009]]). A valid secret alone gets the joiner nothing. On
   approval it sends the existing **Invite** ([[ADR-015]] step 2), and from there Invite, Accepted, roster write and Pantry
   sync are unchanged. A device that sees the requester already in the roster just re-sends Invite.
5. **Deny.** A bad secret, an expired or revoked invite, or a rejected request gets `homejoin.denied` (Home id, reason), so
   the joiner stops retrying and shows why. Only a request that fails the secret check is denied silently: a stranger
   learns nothing about which Homes exist.

**Single use.** Approval records the member in `usedBy` and an invite admits one member by default; a second request on a spent invite
is denied. Because `usedBy` syncs eventually, two admin devices approving at once can both admit. That is accepted: each
admission still needed an admin's explicit approval.

**Keep both flows.** [[ADR-015]]'s link carries no secret, so there is nothing to leak, and it stays the in-person
path. The picker's "Join a home" gains a second entry for pasting an invite link.

## Consequences

- The joiner can open the link whenever. They need Vesta running and online when an admin device is, not at a set time.
- An admin must be online at some point after the joiner redeems, and must act on the approval card.
- A forwarded or leaked link can create a pending request but cannot join without an admin's approval, which shows the
  requester's name and member id.
- The pending join and the pending approval live in local tables ([[ADR-017]] style) and survive restarts.
- Revoking an invite only stops future requests. Someone already approved is a member and is removed the normal way.
- The secret travels in an end-to-end encrypted direct message and only its hash is in the document, which members can read.
- JVM and Android only, as [[ADR-015]].

## Open points

- Whether the approval card also appears as a notification when the app is in the background (depends on mobile
  background behaviour, not decided here).
- Whether x0x stores could serve as a mailbox so the joiner needn't retry. Not investigated; this ADR assumes DM retry.

## Implementation notes

- **Slice 3.** The `JoinRequest` table holds each request with a status: `pending`, `approved` or `declined`. Approving
  sets `approved` and sends Invite; every sync round resends it, and a repeated request gets it again, until the joiner
  answers Accepted. That answer admits them and spends the invite, so approval survives a restart. A declined row is kept,
  so a repeated request is answered with `Denied` again and isn't put to an admin twice. Rows older than 14 days are dropped.
  A device that already lists the requester as a member re-sends Invite only to a device the roster lists for them.
- **Slice 4.** The `PendingJoin` table holds each link the user opened (secret, admin agents, expiry, and `denial` once
  refused). Every sync round resends the Request to each named admin, except after expiry or a refusal. An Invite is
  accepted from an agent the link named, so ADR-015's join link keeps working beside it. A pending join outlives its link's
  expiry by 14 days, so an Invite approved just before it still lands, and is dropped once the Home arrives another way.
- **Slice 5.** The link also carries the Home's name (last, as it may contain the separator) so the joiner's picker can say which
  Home it waits on; `PendingJoin` keeps it. `HomeJoining` creates, lists and revokes links, and exposes requests and pending
  joins as flows for the screens.

## Implementation slices

1. `homejoin.request` and `homejoin.denied` messages, `InviteLink` encode and decode, with tests.
2. `invite:` entries in the Home document: create, validate, revoke, expiry, use count.
3. Admin side: pending-request table and service, approve and decline, then Invite on approval.
4. Joiner side: pending-join table, retry each round, drop at expiry or on `denied`.
5. UI: create and revoke links on Members, approval card, "waiting for approval" state, paste-link entry on the picker.
