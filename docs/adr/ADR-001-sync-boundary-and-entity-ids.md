# ADR-001: Table-Level Sync Boundary, and UUID Primary Keys for Entities

## Status

Accepted — 2026-09-08

## Context

Vesta is building two forms of device sync, both riding on the same underlying mechanism: cr-sqlite CRDT
tables replicated over x0x (see [[ADR-002]] for the transport design — that ADR assumes the decision made
here but doesn't itself settle it).

1. **Full device sync** — a single user's own data (e.g. their Profile) replicated across every device
   they own.
2. **Partial (Home) sync** — data replicated only to the members of a specific Vesta Home, via the
   per-Home x0x group described in [[ADR-002]].

cr-sqlite makes a table into a CRDT with `crsql_as_crr(table)` — this is a per-*table* operation. cr-sqlite
has no concept of a per-*row* replication policy: once a table is a CRDT, `crsql_changes` exposes every
row's changes uniformly, and it is entirely up to the application which peers those changes get sent to.
That means "who is allowed to receive this row" cannot be enforced by cr-sqlite itself — it has to be
enforced by whichever boundary decides which changes go over which x0x channel (DM vs. group broadcast).

Separately, no id-generation mechanism exists anywhere in the codebase yet — this is greenfield work, not
a migration off some existing scheme. No UUID (or any id-generation) library is currently a dependency.

## Decision

### 1. The sync boundary is the table, not the row

Every table that opts into cr-sqlite replication is classified, at the schema level, as exactly one of:

- **`private`** — replicated only via full-device sync between devices belonging to the same user
  identity. Never sent to Home members, even for a user who belongs to a Home.
- **`shared`** — replicated via Home-scoped sync (the per-Home x0x group/topic in [[ADR-002]]) to every
  member of the Home(s) the row belongs to.

A table may not mix `private` and `shared` rows behind a visibility flag. If a future feature needs both
(e.g. "documents, some private, some shared with the household"), it gets split into two tables (e.g.
`PrivateDocument` / `HomeDocument`), not one table with a `visibility` column.

**Rationale:** because cr-sqlite replicates whole tables uniformly, putting the classification anywhere
other than the schema means a sync-transport bug (a wrong `WHERE` clause, a missed filter) could leak a
private row onto a Home's broadcast channel. Making the boundary a table means the transport layer can
pick DM-only vs. group-broadcast purely by table name — there is no code path that *could* route a given
row to the wrong channel.

This ADR does not pick the exact mechanism for recording the classification (a Kotlin annotation, a
sealed registry consulted by the sync layer, a naming convention). That's an implementation detail for
whichever change first adds a second CRDT table beyond `Profile` (see [[ADR-002]]'s build order). It
should, however, be something a test can assert against — every table that calls `crsql_as_crr` must
have a recorded classification, so adding a new synced table forces a conscious choice instead of a
silent default.

### 2. Every entity gets a UUID primary key — association tables are the exception

Every table representing a genuine entity (something with identity of its own, independent of any single
relationship) uses a client-generated UUID, stored as `TEXT`, as its primary key.

Pure association/join tables — rows that only encode a relationship and carry no identity beyond it (e.g.
a membership row linking a person to a group) — are **not** required to have a surrogate id. Their primary
key stays the natural composite of the foreign keys it joins. A surrogate id gets added to one of these
tables only when a concrete need shows up to address a specific row of it independently (a UI deep link, an
audit-log entry pointing at it, another table referencing it directly) — not pre-emptively for uniformity.

**Rationale:**

- No auto-increment integer ids on entity tables. In a local-first system, two devices can insert rows
  offline, concurrently, with no coordinating authority; sequential ids from two devices would collide the
  moment their changes merge. UUIDs make collision negligible without needing a central allocator — this is
  standard practice for CRDT/local-first systems generally, and is why cr-sqlite's own documentation
  assumes non-sequential keys.
- Association tables are deliberately excluded from that rule rather than included for uniformity.
  cr-sqlite's `crsql_changes` already packs a row's primary key — composite or single-column — into one
  opaque value, and the app's own merge path never manually reconstructs a keyed lookup against it; it
  round-trips through `crsql_changes` directly. So a compound key adds no real special-casing to the sync
  path that a surrogate id would avoid. Adding one anyway, with no current consumer that needs to reference
  such a row independently, would be exactly the kind of speculative abstraction worth avoiding — a
  redundant column and a redundant unique index, and two ways to identify the same row instead of one.
- Where an entity table does need an id, Kotlin's stdlib `kotlin.uuid.Uuid` is multiplatform
  (Android/iOS/JVM) and needs no extra dependency, making it the natural choice — noted for whoever
  implements the id-generation helper, not mandated by this ADR.

## Consequences

- Sync-transport code can be written once, generically, dispatching on a table's recorded classification,
  rather than growing bespoke per-table logic.
- New tables force an explicit `private`/`shared` decision at review time instead of an implicit default.
- No future migration off integer ids is ever needed for entity tables, since none are introduced in the
  first place.
- Association tables stay minimal — no redundant surrogate column or index — at the cost of sync/reference
  code needing to handle two shapes of primary key (single UUID vs. composite) if it ever needs to address
  rows generically across all tables, not just replicate them via `crsql_changes`. No such generic need
  exists today.
- UUIDs are less scannable in logs/debugging than small integers or literal test strings — an accepted
  cost of the convention.

## Open questions (follow-up, not blocking this ADR)

- Where the `private`/`shared` classification is recorded in code — deferred to the first PR that adds a
  second synced table.
- Whether any `private`-classified table is even implemented yet — the spike behind [[ADR-002]] only
  exercises a `shared`-shaped table; full-device sync of genuinely private data has no working example yet
  to generalize from.
- The exact UUID generation helper/location (e.g. a single `newId(): String` used by every repository) —
  not designed here, just unblocked by this decision.
- Whether a `private`/`shared` table split (e.g. `PrivateDocument` / `HomeDocument`) should share a single
  application-layer domain model. Leaning: yes for reads (both map into one type, e.g. `Document`, so
  callers above the repository boundary don't need to know which table a row came from), but no for
  writes — each table should keep its own explicit insert/update path, since collapsing writes into one
  "save a Document" function is exactly how a row-level visibility decision creeps back in at the
  application layer, undoing the point of the table-level split. Not settled here; revisit when the first
  such split table is actually built.

## References

- [[ADR-002]] — the sync transport this ADR's table classification feeds into.
