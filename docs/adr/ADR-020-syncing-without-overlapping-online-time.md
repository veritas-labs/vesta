# ADR-020: Syncing and joining without overlapping online time

**Status:** Proposed (2026-10-03). Decision step 1 (option 1) implemented 2026-10-05; the rest is not. Addresses the limit named in [[ADR-018]] (open point: "whether x0x
stores could serve as a mailbox") and the wording fixed in [[ADR-019]]. Builds on [[ADR-005]], [[ADR-013]], [[ADR-014]] and
[[ADR-017]].

## Context

Everything between devices is an end-to-end encrypted x0x **direct message**, retried by the sender every sync round
([[ADR-013]], [[ADR-017]]). There is no server and no mailbox: a message to an offline device can vanish, so the sender
resends until the other device happens to be online. Consequences:

- **Joining** ([[ADR-018]], [[ADR-019]]) needs the joiner and an admin device online together at least once after the link
  is redeemed and approved, within the link's 7 days and the 14-day retry window.
- **Data sync** between members needs two devices online together for one round (30 s). Each device keeps its whole
  Automerge document, so nothing is lost while apart, and Automerge syncs the full document, so a third device can carry
  changes between two that never meet. But a Home of two people with one device each has no such carrier.
- **Mobile.** A suspended phone does not retry. Background behaviour is undesigned ([[ADR-019]] open point), so overlaps
  may be rarer than "both online" suggests.

For a household this will usually work: people are home and online most evenings. It fails for the cases that matter
most to trust: a joiner who opens the link on a weekend and an admin who is away, or two members whose devices rarely
run at once.

x0x's bootstrap nodes are for discovery and NAT traversal and never hold application data. x0x **stores** are replicated
key–value CRDTs, but a replica lives on the agents that joined it: a store keeps data available only while at least one
replica is online. So a store is **not by itself a mailbox**. It only helps if some always-on agent replicates it.

## Options

1. **Make the overlap likelier (no new infrastructure).** Sync when a peer comes online instead of waiting for the next
   30 s round: x0x's `/presence/events` SSE reports online and offline transitions ([[ADR-002]] already planned this).
   Also run a round when the app returns to the foreground. This shortens the overlap needed from a full round to the
   moment two devices are both up. It does not remove the need for the overlap.

2. **An always-on Home hub.** A device that is nearly always online (a desktop left running, a Raspberry Pi, a small VPS)
   runs Vesta headless and is enrolled as an ordinary device of a member, so it is in the roster and syncs the Home like any
   other replica. Members then sync with the hub whenever each is online, and changes flow A → hub → B without A and B
   meeting. For joining, the invite link can name the hub's agent as one of the "admin agents" it already lists
   ([[ADR-018]] step 1), so the joiner's request is held somewhere online. The hub holds a full decrypted replica, so it
   is a device the user trusts, as a paired device is ([[ADR-013]]). It needs no new protocol, only a headless build and a
   way to enrol it (the existing device pairing, [[ADR-012]]).

3. **Encrypted change log in an x0x store.** Each member appends Automerge change chunks, encrypted with a Home key, as
   store entries. Others pull what they haven't seen. A replica that cannot read them (a relay, a hub, a friend's device)
   can store and forward. This is the only option that does not require the holder to be trusted with the data. It needs
   a Home key with rotation on member removal, an owner/permission model for the store ([[ADR-005]] noted that stores
   anchor trust to an owner), and a way to garbage-collect the log. It still requires *some* replica to be online.

4. **A hosted relay.** A server run by Veritas Labs holds opaque encrypted blobs. Gives the best reliability, but it
   adds a service to run and trust, and it undoes the "no server" property the design is built on. Not considered further.

## Decision

Proposed, in this order. Each step is independently useful, and a later one is only built if the earlier one is not
enough in use.

1. **Do option 1 now.** Sync on a peer's presence transition and on app foreground, as well as on the timer. It is small
   and makes both joining and sync complete as soon as the two devices are up together.
2. **Offer option 2 as the answer to "we are never online together".** A Home hub is a normal device in the roster, so
   it needs only the headless build and enrolment. Document it as a household choice, not a requirement. When an
   invite link is created and the Home has a hub, the link names the hub among its agents.
3. **Spike option 3 before committing.** The questions to answer against a real daemon: whether a store replica keeps and
   serves entries while its writer is offline; whether a replica that did not create the store can be written to by several
   members; how an owner-signed store fits several writers; how large a log gets. Do not build the Home key and log until
   the spike shows stores can carry it.
4. **No hosted relay.**

## Consequences

- With option 1 alone the limit remains: two people who are never online at once still can't sync, and a join still needs
  an overlap. The documentation should say so plainly.
- With a hub, a Home is eventually consistent without any two people overlapping, as long as the hub is up. A hub down
  for days is the same as today.
- A hub is another place the Home's data lives, decrypted. It must be a device the user owns, and removing it uses the
  normal device removal ([[ADR-012]]).
- Hub admin agents named in invite links lengthen the list that is capped at 5 ([[ADR-018]]); the hub should come first.
- Nothing here changes the message formats, so Homes made before this keep working.

## Open points

- Whether a hub can approve joins by itself. It should not: approval is the check on a leaked link ([[ADR-019]] step 4),
  so the hub holds the request and an admin's device still approves.
- Presence reliability: [[ADR-002]] flagged that the offline→online signal may be late or missed, so the timer stays as a
  fallback.
- How a mobile device wakes to sync when the app is closed. Undesigned, and the main reason overlaps are rarer on phones.
- Whether the headless build is worth shipping for households, or the log in option 3 turns out better for non-technical
  users.

## Implementation slices

1. **Done.** Presence-triggered and foreground-triggered sync rounds in `DeviceDataSyncService`, with tests on the in-memory mesh.
   `X0xClient.presenceEvents` and `X0xEventHub.presence` stream `/presence/events`; an `online` event for a peer runs
   `syncWith(peer)` (restart that peer's sync and resend queued join messages); `DeviceDataSync.syncNow()` runs a full
   round and is called from `App` on `ON_START`. The 30 s timer stays as the fallback.
2. Document what overlap is required today, in the user-facing help.
3. Store spike against a real x0x daemon (opt-in test, as [[ADR-012]]'s real-daemon test), written up here.
4. Headless Vesta and a hub enrolment flow; invite links list the hub first.
