# ADR-007: SQLite as Automerge Persistence Store and Free-Text Search Index

## Status

Proposed — 2026-09-26. Answers [[ADR-005]]'s Open Question 1 (persistence) with a concrete schema, rather than
just "a SQLite BLOB." Also settles a question no prior ADR raised: how the app does free-text search, given
Automerge `Document`s are not queryable SQL rows. Not yet implemented.

## Context

[[ADR-005]] decided persistence would be `Document.save()`'s bytes in "a single BLOB column... not a generic
key/value `Storage` contract... not a hand-rolled chunk-log" — a decision, not a design. No table shape, no
answer for `SyncState` persistence's own storage, and no mention of search at all.

Separately, the app needs free-text search (e.g. "find the task that mentions the plumber") over data that now
lives inside Automerge `Document`s — nested CRDT structures, not SQL rows. SQLite's own FTS5 module is the
obvious mechanism, but it indexes rows in ordinary tables; it has no way to reach into an Automerge document's
internal representation directly. Something has to project the document's text content into a queryable table.

### cr-sqlite is not a candidate for either role, and turns out to already be dead code

Checked directly (2026-09-26): grepping the whole tree for `crsql_as_crr` and `crsql_changes` — the two calls
that actually make a SQLite table a cr-sqlite CRDT table or read its change feed — finds **zero** call sites
anywhere in application code. The only remaining reference is
`shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/db/CrSqliteSmokeTest.kt`, which calls `crsql_site_id()` purely to
prove the extension loads. Every other hit is the extension-loading plumbing itself (vendored binaries,
`DatabaseDriverFactory.*.kt`'s `crSqliteExtensionPath()`/`addExtension` calls) or comments — nothing that uses
what cr-sqlite actually does. `docs/adr/ADR-005-...md` already retired cr-sqlite as the *CRDT/sync* technology;
this confirms there isn't a second use of it (e.g. as a plain local-only extension) hiding anywhere either. It is
loaded, at real startup cost (binary extraction/dlopen on every platform), and does nothing.

This ADR doesn't itself remove that loading code — see the README/docs cleanup accompanying this ADR and Open
Questions below — but it proceeds on the basis that cr-sqlite is not, and shouldn't be treated as, part of either
the persistence or search design below. Both are built on plain `androidx.sqlite`-bundled SQLite, which already
backs `VestaDatabase` for `Home`/`HomeMember`/`Profile`/etc.

## Decision

### Persistence: two new plain SQLDelight tables in `VestaDatabase`, keyed by [[ADR-006]]'s `documentId`

No new database, no new driver, no `Storage` adapter interface — just more tables in the same
`VestaDatabase`/`BundledSQLiteDriver` setup the rest of the app already uses.

```sql
CREATE TABLE AutomergeDocument (
    documentId TEXT NOT NULL PRIMARY KEY,  -- ADR-006's "$homeId:$feature"
    homeId     TEXT NOT NULL,
    feature    TEXT NOT NULL,
    bytes      BLOB NOT NULL,              -- Document.save()
    updatedAt  INTEGER NOT NULL
);

CREATE TABLE AutomergeSyncState (
    documentId   TEXT NOT NULL,
    peerAgentId  TEXT NOT NULL,
    bytes        BLOB NOT NULL,            -- SyncState.encode()
    updatedAt    INTEGER NOT NULL,
    PRIMARY KEY (documentId, peerAgentId)
);
```

`homeId`/`feature` are denormalized off `documentId` (rather than parsed back out of the composite key at read
time) so "every document for this Home" and "every document for this feature across Homes" are both plain
indexed queries, not string-splitting. `AutomergeSyncState` is exactly what ADR-005 flagged as its own suggestion
— persisting each peer's `SyncState.encode()` alongside the document so a resumed connection skips re-deriving
shared sync history from scratch — now given an actual table.

Write path: after any local edit (the same moment [[ADR-006]]'s `MultiDocumentSyncHub.pumpAll(docId)` is called)
and after `onDocumentChanged`/`onChanged` fires for an incoming sync message, `Document.save()` and
`SyncState.encode()` (per active peer) are re-persisted. Read path: on registering a document with the hub (Home
opened / app launched), load the row if present, `Document.load(bytes)` / `SyncState.decode(bytes)`; absent row
means a fresh, empty `Document`.

### Search: an FTS5 table, populated as a derived projection — not the source of truth

```sql
CREATE VIRTUAL TABLE AutomergeSearchIndex USING fts5(
    documentId UNINDEXED,
    feature    UNINDEXED,
    entityId   UNINDEXED,
    text
);
```

One row per searchable entity (a task, a chore, a list item — whatever a feature's document models) inside a
document, keyed by `(documentId, entityId)` so re-indexing an entity is a delete-then-insert. `entityId` is
whatever stable id the feature's own data model already uses for that entity (a task's own id, not an Automerge
internal object id) — the projection has to be able to name *which* thing matched, independent of Automerge.

A search query (`... WHERE AutomergeSearchIndex MATCH ? ORDER BY rank`) returns `(documentId, feature,
entityId)` tuples, not full content — the FTS table is a lookup index, not a duplicate data store. The caller
resolves each hit back to the live entity by reading it out of the already-in-memory `Document` for that
`documentId` (Home documents are expected to be loaded whenever their Home is active — see Consequences for what
happens when they're not).

**Re-indexing trigger:** hang a re-index off the same `onChanged`/`onDocumentChanged` hook `AutomergeDocument`
persistence uses — after any local edit or applied incoming sync message, re-derive the changed document's
searchable text and rewrite its `AutomergeSearchIndex` rows. The simplest correct version re-derives *all* rows
for the whole document on every change (delete-then-reinsert by `documentId`), not a diff against what changed —
acceptable for a Home's expected data volumes (a household's tasks/chores/lists, not thousands of rows), and far
simpler than tracking which specific entities an incoming sync message touched. See Open Questions for what
outgrowing this would need.

## Consequences

- **Search is eventually consistent with the document, not live.** A query run in the moment between a document
  changing and its re-index running (however short) can miss the newest edit or return an entity that's since
  been deleted. Acceptable for a local-first task/chore/list app; would not be for a use case needing
  read-your-writes search guarantees.
- **Search only works for documents currently loaded and registered with the sync hub.** A Home's documents that
  haven't been opened this session (or ever, on this device) have no `AutomergeSearchIndex` rows — there's
  nothing to derive them from without loading and decoding the `AutomergeDocument` blob first. Search across
  "every Home this device has ever seen," not just currently-open ones, needs the persistence layer to
  eagerly load and index on startup, not just on demand — a real cost if there are many Homes/documents,
  not designed here (see Open Questions).
- **Full re-projection per change is simple but doesn't scale indefinitely.** Fine for expected Home data
  volumes; if any feature's document grows large (long history, many entities), whole-document re-indexing on
  every change becomes real, avoidable work. Not a problem this ADR solves — flagged for whoever hits it.
- **`AutomergeDocument`/`AutomergeSyncState` add real schema to a database ADR-005 didn't design one for.**
  Home/document deletion (leaving a Home, a feature being removed) now needs an explicit cleanup path across
  three tables (`AutomergeDocument`, `AutomergeSyncState`, `AutomergeSearchIndex`) — not designed here, flagged
  below.
- **cr-sqlite's continued presence in the build is now unambiguously dead weight, confirmed rather than
  assumed.** This ADR doesn't remove the extension-loading code or vendored binaries itself (out of scope for a
  persistence/search design), but the README and prior ADRs' framing have been updated to state this plainly
  (see the accompanying doc changes) rather than leave it as "superseded direction" phrasing that undersells that
  there are now zero call sites.

## Open Questions

1. **FTS5 availability in the bundled driver is assumed, not verified.** This ADR assumes `androidx.sqlite`'s
   `BundledSQLiteDriver` is compiled with `SQLITE_ENABLE_FTS5` (standard in most SQLite amalgamation builds, but
   not confirmed against this specific dependency in this session) and that SQLDelight's dialect handles a
   `CREATE VIRTUAL TABLE ... USING fts5(...)` statement in a `.sq` file without extra configuration. Needs
   confirming — with a smoke test analogous to the retired `CrSqliteSmokeTest` — before real implementation
   starts.
2. **Eager vs. lazy indexing across Homes.** Whether search needs to cover Homes/documents not currently open
   (see Consequences) is a product question this ADR doesn't answer; it changes whether indexing can stay
   purely change-triggered or needs a startup backfill pass.
3. **Cleanup on Home/document removal.** No design yet for what deletes `AutomergeDocument`/`AutomergeSyncState`/
   `AutomergeSearchIndex` rows when a Home is left or a feature's document is retired.
4. **Incremental re-indexing.** If whole-document re-projection turns out too expensive, the fix needs to know
   which entities an incoming sync message actually touched — `org.automerge`'s Java bindings' capacity for
   that (a patch/diff API, if one exists) hasn't been checked; not needed for the current, simple design.
5. **Ranking/relevance tuning.** FTS5's default `bm25()` ranking is taken as a given starting point; no product
   requirement has been raised yet for anything more specific (prefix search, typo tolerance, per-feature
   weighting).
6. **Removing cr-sqlite's dead extension-loading code.** Confirmed dead (see Context) but not removed by this
   ADR — vendored binaries (`shared/src/jvmMain/resources/crsqlite/`, `androidApp/.../libcrsqlite.so`,
   `iosApp/crsqlite.xcframework`), the `crSqliteExtensionPath()`/`addExtension` plumbing in
   `DatabaseDriverFactory.*.kt`, and `CrSqliteSmokeTest.kt` are all candidates for deletion in a follow-up change,
   not bundled into this one.

## References

- [[ADR-005]] — the "SQLite BLOB" persistence decision this ADR gives a concrete schema to, and the source of
  the `SyncState` persistence suggestion `AutomergeSyncState` implements.
- [[ADR-006]] — `documentId` (the `AutomergeDocument`/`AutomergeSyncState`/`AutomergeSearchIndex` key) and the
  `onChanged`/`pumpAll(docId)` hooks this ADR's write/re-index triggers hang off.
- [[ADR-001]] — the retired cr-sqlite table-classification design; this ADR is further confirmation that
  direction is gone, not merely deprioritized.
- `shared/src/commonMain/kotlin/uk/veritaslabs/vesta/db/DatabaseDriverFactory.kt`,
  `shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/db/CrSqliteSmokeTest.kt` — the cr-sqlite loading code and its
  one remaining test, confirmed to have no functional call sites elsewhere (see Context).
