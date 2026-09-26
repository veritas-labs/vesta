# ADR-005: Home Sync — One Automerge Document per Home over One x0x Group

## Status

Accepted — 2026-09-26 (explicit user decision). Supersedes [[ADR-001]], [[ADR-002]], [[ADR-003]] entirely. Extends
[[ADR-004]] — does not replace it: ADR-004's `Document`/`SyncState`-over-x0x mechanism stands unchanged; this ADR
answers three things ADR-004 left open (persistence, multi-peer fan-out, Automerge-vs-cr-sqlite) and gives it a
concrete unit of scoping (one Document, one group, per Home) it didn't yet have.

**Extended by [[ADR-006]]** (2026-09-26) — not superseded: the one-Document-per-Home scoping below stands
unchanged. ADR-006 resolves this ADR's Open Question 2 (multi-document routing), covering both a device in
multiple Homes and a single Home with multiple documents (e.g. separate `tasks`/`chores`/`lists` documents rather
than one document holding every feature).

## Context

ADR-004 built and verified `Document`/`SyncState` sync directly over x0x direct messaging, but explicitly left
unresolved: how the `Document` persists (Open Question 1), how sync fans out to more than one peer (Open Question 3),
and whether Automerge or cr-sqlite ([[ADR-001]]/[[ADR-002]]/[[ADR-003]]'s direction) is the actual chosen path (Open
Question 8) — "not this ADR's concern to settle."

This ADR settles all three, by explicit user instruction (2026-09-26):

- **Automerge is the app-data model going forward.** The cr-sqlite CRDT-table design — [[ADR-001]]'s sync boundary,
  [[ADR-002]]'s Home-group broadcast, [[ADR-003]]'s per-user device group — is retired, not merely deprioritized.
  Nothing about *why* cr-sqlite loses out to Automerge is recorded here; the user's instruction settled the choice
  without re-litigating the comparison ADR-004 Open Question 8 left open.
- **No `org.automerge.repo.Repo`.** Raised explicitly as a risk before writing this ADR, since "one Repo per Home"
  reads as literally reintroducing the `Repo`/`Storage`/`Dialer`/`Transport` design ADR-004 built, tested, and
  deleted (see ADR-004 §"Designs tried", design 3). Confirmed: no. "Repo" here means an Automerge **Document** — the
  thing ADR-004 already settled on — not the `Repo` class. This ADR does not reopen ADR-004's rejection of
  `org.automerge.repo`.
- **Persistence is a SQLite BLOB.** Not design 1's hand-rolled per-chunk log, not design 3's generic `Storage`
  adapter contract (both already built and deleted per ADR-004) — just `Document.save()`'s encoded bytes, stored
  and loaded directly.

## Decision

### One Vesta Home ↔ one Automerge `Document` ↔ one x0x group

Every Home gets exactly one Automerge `Document` (the Home's app data — tasks and whatever else Vesta models, not
yet enumerated here) and exactly one x0x named group (`POST /groups`, `private_secure` preset — the same mapping
[[ADR-002]] already specified and which survives this ADR unchanged).

### The group's job narrows to membership and discovery — no broadcast

[[ADR-002]]'s "Two delivery paths" design (MLS `secure/encrypt` the payload, then `publish` the ciphertext to a
gossip topic for online members) is retired along with the rest of ADR-002. The group is used only for:

- **Invite/join** — `POST /groups/<gid>/invite` → `POST /groups/join`, same as ADR-002's Home-invite flow and
  ADR-003's device-enrollment flow both already assumed.
- **Roster/discovery** — `GET /groups/<gid>/members` tells a device who else it needs to sync with.

No `secure/encrypt`, no `publish`/`subscribe`, no gossip topic, no MLS epoch bookkeeping. That entire mechanism
(and its own open questions — topic derivation, epoch bookkeeping, decrypt-failure handling) goes away with it.

### All data sync is direct messages, fanned out across the group roster

Exactly [[ADR-004]]'s `DocumentSyncSession` — `connectTo(peerAgentId)`, `pumpAll()`, the accept-side-must-dial-back
handling — unchanged, generalized from the demo's one hardcoded peer to one `DocumentSyncSession` peer-connection
per member the group roster returns. This is [[ADR-002]]'s own phase-2 build step ("Multi-peer fan-out over DM ...
gets multi-device sync working correctly, if not efficiently") — the difference is that phase was written as a
stepping stone toward phase-3 group broadcast; under this ADR it's the destination, not a waypoint.

### Persistence: a BLOB, not a Storage adapter

`Document.save()` / `Document.load()` round-tripped through a single BLOB column keyed by the Home id. Explicitly
ruled out, per Context above: a generic key/value `Storage` contract (that shape existed only to satisfy
`org.automerge.repo.Repo`'s interface, which isn't in use) and a hand-rolled chunk-log table (design 1, already
tried and deleted). `SyncState.encode()`/`decode()` persisted per `(homeId, peerAgentId)` alongside the document,
so a resumed connection skips re-deriving shared sync history from scratch — this was ADR-004 Open Question 1's
own suggestion, now decided rather than merely proposed.

## Consequences

### What this resolves from ADR-004's Open Questions

- **OQ1 (persistence)** — answered above.
- **OQ3 (multi-peer group sync)** — answered: DM fan-out across the group roster; no broadcast/gossip layer.
- **OQ8 (Automerge vs. cr-sqlite)** — answered: Automerge. cr-sqlite's direction is retired.

### What this does not resolve — still open

- **OQ2, multi-document routing, is now sharper, not softer.** A device can belong to more than one Home, hence
  more than one `Document`, over the same `X0xClient`. ADR-004 already flagged that `directEvents` delivers every
  inbound message to every subscriber and that Automerge sync-message bytes don't self-identify their document —
  running two `DocumentSyncSession`s off one client today would break. This ADR makes that scenario the normal
  case (any user in ≥2 Homes hits it), not an edge case, and still doesn't design the envelope/document-id-prefix
  scheme needed to fix it.
- **Private, per-user (not per-Home) sync is untouched.** [[ADR-003]] designed a `private`-classified counterpart
  to Home sync — a user's own data replicated across their own devices, independent of any Home. This ADR's
  instruction was scoped to "Home"; it says nothing about whether an analogous per-user Automerge `Document` +
  per-user x0x group should exist. Retiring ADR-003 removes its cr-sqlite-based answer to that need without
  supplying a replacement — flagged here rather than assumed one way or the other.
- **No sync-time ACL.** Narrowing the group's job to membership/discovery doesn't, by itself, make
  `DocumentSyncSession` check a sync message's sender against that roster before applying it. ADR-004 Open
  Question 6 ("no ACL on who may sync") still applies in full; tying `connectTo`/accept-side dial-back to the
  group roster (only sync with, and only accept sync from, a current member) is implied by "the group is who you
  sync with" but not yet implemented or designed in detail.
- **Reconnection/liveness, production HTTP timeout tuning** (ADR-004 OQs 5, 7) — unchanged, not addressed here.

### Disposition of the retired ADRs

- **[[ADR-001]]** (table-level sync boundary, UUID primary keys) — Superseded. Its `private`/`shared`
  classification concept was cr-sqlite-table-shaped and doesn't carry over as written; whether an equivalent
  distinction is needed for Automerge Documents is the "private per-user sync" open question above, not answered
  by carrying ADR-001 forward.
- **[[ADR-002]]** (Home data sync via cr-sqlite + x0x groups) — Superseded. Its "one x0x group per Home" mapping
  is the one piece that survives, restated in this ADR's Decision above; its cr-sqlite mechanics and its
  broadcast/gossip transport do not survive.
- **[[ADR-003]]** (device sync via cr-sqlite + per-user x0x group) — Superseded. Its device-enrollment/certificate
  design (x0x `AgentCertificate`, QR-based pairing) is not cr-sqlite-specific and isn't invalidated by this ADR —
  only the cr-sqlite sync mechanics it assumed underneath are retired. See "private, per-user sync" above for what
  that leaves unresolved.
- **[[ADR-004]]** — not superseded. Its `Document`/`SyncState`-direct-over-x0x decision, and its rejection of
  `org.automerge.repo`, both stand; this ADR builds directly on top of it.

## References

- [[ADR-001]], [[ADR-002]], [[ADR-003]] — superseded by this ADR; kept for the rationale behind decisions that
  don't fully carry over (e.g. why UUID entity ids, why groups map one-per-Home, why device pairing is
  certificate-based).
- [[ADR-004]] — extended, not superseded; the `DocumentSyncSession`/`Document`/`SyncState` mechanism this ADR
  scopes to one-per-Home and gives persistence to.
