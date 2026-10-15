# ADR-034: Document format versioning, compatibility rules and a minimum-reader guard

**Status:** Proposed (2026-10-15). Amends ADR-010 (Pantry categories migration). Touches ADR-006 (sync envelope), ADR-013
(device data sync), ADR-020 (mailbox), ADR-033 (Sync activity).

## Context

Vesta is local-first with no server. Automerge documents (Pantry, shopping list, cookbook, merchants, meal plan, calendar,
tasks, Home roster, Home registry, profile registry) sync between devices that run **different app versions**, directly and
through hub mailboxes that can hold changes for weeks. Data loss is unrecoverable: there is no server copy to restore from.

What exists today:

- **No document-level schema version.** The only version is the Pantry's `categoriesVersion`.
- **Readers skip what they cannot parse.** A newer shape on an older app is silently ignored, but the older app can still
  *write* in the old shape, on top of data it does not understand.
- **In-document migration runs on every device.** `migratePantryCategories` (called from `openPantryDocumentSource`) opens a
  transaction on load and, if `categoriesVersion` is old, calls `seedCategories()`, which does
  `set(ROOT, "categories", MAP)`. Every device that loads the old document writes that change.
- **ADR-010's claim is wrong.** It says "two devices migrating before they sync each write an identical registry; Automerge
  keeps one, nothing is lost". Concurrent `set` of the same key to a *map* creates two distinct map objects; Automerge picks one
  as the winner and the other subtree becomes invisible. Custom categories another device added under the losing map are lost.
  Identical content does not help, because the objects are different.
- **Wire.** `SyncEnvelope` (`[len N][docId][sync message]`) has no version. `MailboxEntry` and the snapshot have a version
  byte, and an entry with a version other than 1 is skipped. The DeviceSync and HomeGroup JSON messages use
  `ignoreUnknownKeys` and silently drop an unknown `kind`, so a new message type vanishes on old apps with no trace.

The risk is concrete: the next shape change (a new meal-plan field, a re-keyed task) will meet a fleet where some phones
have not updated.

## Options considered

1. **Do nothing; rely on tolerant readers.** Cheapest, but cannot express "this change is unsafe for old writers", and leaves
   the migration race.
2. **Version only on the wire** (handshake refuses to sync with older peers). Blocks sync for the whole Home because one
   member has not updated, and still gives a document no way to describe itself. Rejected: availability loss, no data safety
   gain.
3. **Per-document `schemaVersion` + `minReaderVersion`, additive-only rules, a write guard, and a deterministic migration
   discipline.** Documents describe themselves, old apps keep relaying and storing, nothing is blocked from syncing.
4. **New document id per incompatible shape** (e.g. `pantry.v2`) with a one-shot copy. Safe for a hard break, expensive
   (duplicated history, two docs to sync), and loses concurrent edits made on the old doc after the copy.

**Recommendation: option 3 as the default, option 4 held in reserve** for a break that additive rules cannot express.

## Decision

### 1. Per-document `schemaVersion`

- Every document gets a root integer `schemaVersion`, written **in the creating transaction** (the same one that seeds the
  document), by a single shared helper so no module forgets it.
- A document with **no** `schemaVersion` is **v1**: the shape shipped before this ADR. We never write the key just to label an
  old document; a v1 document gains the key only when a feature moves it to v2 (see 4).
- `schemaVersion` is monotonic. Concurrent writers of the key resolve to the larger value on read (the reader takes
  `max` over `getAll`, not the LWW winner), so a stale device cannot lower it.
- Each module declares `SupportedSchemaVersion` (highest it can read **and** write) next to its document class.

### 2. Compatibility rules

These are review rules for every document change:

1. **Additive only.** New keys, new map entries, new optional fields. A reader must work when a key is absent.
2. **Readers tolerate.** Ignore unknown keys and unknown enum values; never fail a whole document because one entry is odd.
   Skipping an *entry* is allowed, deleting or rewriting it is not (an app must not "clean up" what it does not understand).
3. **Never change a key's type or meaning.** A key that held a string never holds a map; a value's unit never changes.
4. **Rename = add new + dual read.** Writers write the new key (and, while `minReaderVersion` allows older readers, the old
   one too); readers prefer the new key and fall back to the old. The old key is retired only after `minReaderVersion` is
   raised past every app that needs it.
5. **Never replace a map by `set` on a shared key.** Create a map once with a deterministic path and write into it (see 4).
6. A change that cannot obey 1-5 must raise `minReaderVersion` (3) or use a new document id (option 4).

### 3. Minimum-app-version guard

- A document may carry root integer `minReaderVersion` (default: absent = 1). It is the lowest `schemaVersion` an app must
  support to **write** to the document. Like `schemaVersion` it is monotonic (reader takes `max`). Raising it is a deliberate,
  release-noted act, done by the first release that needs it, not by older releases.
- On open, an app compares `minReaderVersion` with the module's `SupportedSchemaVersion`. If the app is older:
  - The document opens **read-only for the UI**: the module shows "Update Vesta to edit this" and disables writes. Read
    paths still render whatever they understand (rule 2).
  - **Sync is unaffected.** The device keeps applying incoming changes to its Automerge document, persists them, includes
    the document in sync rounds and relays through the mailbox exactly as now. No data is lost or dropped because the app is
    old; Automerge changes are opaque to the transport.
  - No migration and no seeding runs on that document (a stale device writing a seed is the worst case).
- Enforcement point: the `*DocumentSource` write path (one `requireWritable()` in the shared base) rather than each screen, so
  new write sites cannot bypass it. The UI reads a `writable` flow from the source.
- **Limit, stated plainly.** Apps shipped before the guard cannot honour `minReaderVersion`. Therefore (a) the guard ships and
  must reach users **before** any release raises `minReaderVersion` above 1, and (b) until adoption is high enough, shapes
  stay strictly additive (rule 1) and the guard is a safety net, not the plan. The sync activity screen (ADR-033) and the
  Hello frame (5) tell us which peers report which app version.

### 4. In-document migrations

- **Prefer no migration.** Additive keys with default-on-read need none. Only a change that must rewrite existing data (as
  ADR-010 did) is a migration.
- **Never run on load.** A migration is not triggered just because a document was opened. It runs only when the app is
  `>=` the target version, the document is writable (3), and the document is **not an empty shell** waiting for sync (no
  `schemaVersion` and no content), as ADR-010 already does for `categories`.
- **Deterministic, converging writes.** A migration must be idempotent and, when two devices run it concurrently, converge
  without conflicting objects:
  - Write each migrated record to a **deterministic key path that includes the target version**, e.g. `categoriesV3/<key>`.
    Two devices writing the same key with the same scalar value is a harmless same-value conflict; the content, not an
    object identity, is shared.
  - **Never `set(ROOT, key, MAP)` on a key another device may also create.** Create the container map under a
    deterministic, version-named root key and treat any concurrent duplicate as the same container by merging on read
    (`getAll` over conflicting map values and union their children), not by letting one win.
  - Do not delete the old structure in the migration. The new reader prefers the new path; the old path is removed only by a
    later cleanup release once `minReaderVersion` has passed it (rule 4).
  - Bump `schemaVersion` last, in the same transaction, with `max` semantics.
- **Single-writer preference.** Where a Home has a natural owner (the Home creator/admin), the migration may be restricted to
  that device and the others just wait for the changes. Convergent writes above are still required, because the admin can be
  offline and a rule that depends on one device would stall migration.

**ADR-010 amendment.** The sentence "Two devices migrating before they sync each write an identical registry; Automerge keeps
one, nothing is lost" is wrong and is struck. Replace with: concurrent re-seeds create two `categories` map objects, one
subtree wins, and custom categories added to the other are lost. `seedCategories` must stop doing `set(ROOT, "categories", MAP)`
on an existing document. The next change to the defaults (a `CategoriesVersion` bump) must follow this ADR's rules: write
into a version-named deterministic container, merge on read, never replace a root map. The existing destructive v2 reset was
accepted by the user and is not undone; the fix is for future bumps and for the shipped `seedCategories` path, which is
corrected in WP3.

### 5. Wire

Data frames stay exactly as they are, so old and new apps keep syncing.

- **Hello frame (new, optional).** Control frames start with byte `0x00`. Today `SyncEnvelope.decode` returns null when the
  length byte is 0, so an old app does not misparse a frame; it falls through to the other parsers and drops it. Layout:
  `[0x00][frame type: 1 = Hello][app protocol version: u16][count: u8][per document type: kind code u8, supportedSchemaVersion u8]`.
  Sent once per peer at the start of a sync round. A peer that never sends one is treated as "pre-ADR-034, supports v1
  everywhere". The result feeds Sync activity (peer app version) and lets a new app avoid *offering* a document type a peer
  cannot hold, but it never blocks the sync of documents the peer does support.
- **Incompatible shapes use a new document id** (option 4, e.g. suffix `.v2`), because an old app only opens the ids it knows
  and ignores the rest. This is the escape hatch for what rules 1-5 cannot express; the old document stays and is migrated by
  copy, with a documented cut-over.
- **Unknown messages are recorded, not dropped.** An unknown `kind` in DeviceSync/HomeGroup JSON, an unknown envelope
  document id, a `MailboxEntry`/snapshot with a newer version byte, and an undecodable frame each write a Sync activity
  event (`kind = failed`, `detail = unsupported_version` or `unknown_message`; no content, per ADR-033) and increment a counter.
  The mailbox keeps the newer-version entry in place (we skip *processing*, never delete) so an update can apply it later.
  The verdict gains "Some changes need a newer Vesta" when the counter is non-zero.

### 6. Tests

- **Fixtures.** For every module, a checked-in Automerge binary (`shared/src/jvmAndroidTest/resources/fixtures/<module>/v<N>.automerge`)
  for each **shipped** shape, including the v1 pre-ADR shapes captured from the current release builds (with the Pantry in
  its `categoriesVersion = 2` form). Fixtures are immutable once released; changes add a new file.
- **Load tests.** A parameterised test per module loads every fixture with current code and asserts the read model (counts and
  a few field values), that no entry is skipped, and that opening it does **not** change heads (no write on load).
- **Forward tests.** A fixture whose `minReaderVersion` exceeds the build's supported version opens read-only, rejects
  writes, and still merges and re-serialises incoming changes byte-compatibly.
- **Migration convergence.** For each migration, fork a v(N) fixture into two documents, run the migration on both with
  different concurrent user edits, merge both ways, and assert identical heads, no conflicting container objects, and that
  the concurrent edits survived. This test would have caught the ADR-010 bug.
- **Wire.** Old-style frame decoding tests: a Hello frame is ignored by the existing `SyncEnvelope.decode` (returns null); an
  unknown `kind` and a version-2 mailbox entry produce a recorded event and no crash.
- Acceptance criteria (Given/When/Then) added to `docs/testing/acceptance-tests.md` for read-only state, update-required
  message and the new Sync activity verdict.

### 7. Rollout (work packages)

| WP | Scope | Notes |
|----|-------|-------|
| WP0 | Capture v1 fixtures from the current build for all ten document types; add load-with-current-code tests. | Pure tests, no behaviour change. Land first. |
| WP1 | Shared helper: write `schemaVersion` at creation, `max`-read `schemaVersion`/`minReaderVersion`, per-module `SupportedSchemaVersion`. | Existing docs unchanged (v1). |
| WP2 | Write guard in the `*DocumentSource` base, `writable` flow, "Update Vesta to edit" UI state. Release this **before** anything raises `minReaderVersion`. | The guard is only useful once it is out in the field. |
| WP3 | Fix the Pantry migration: stop `set(ROOT, MAP)` re-seeds, convergence test, amend ADR-010. | Fixes a live data-loss path. |
| WP4 | Record unknown kinds / versions as Sync activity events; verdict text. | Builds on ADR-033. |
| WP5 | Hello frame and peer capability display. | Optional for correctness; informs release decisions. |
| WP6 | Review checklist in `CONTRIBUTING`/docs referencing the rules in 2 and 4. | Docs. |

WP0, WP3 and WP4 are independent and can go first; WP1 -> WP2 is the critical path.

## Consequences

- Documents describe themselves; an old app cannot silently write over a shape it cannot read once the guard is deployed.
- Old apps keep relaying, so a stale phone in a household never loses or blocks anyone's data.
- Shape changes become more expensive to write (dual keys, version-named paths, fixtures). That is the intent.
- The guard cannot protect apps that predate it; for that window the additive-only rule is the only defence.
- `max` semantics for `schemaVersion`/`minReaderVersion` mean a version can never be lowered; a mistaken raise needs a new
  release that supports the version, not a rollback.
- A read-only mode is a user-visible degraded state; copy and an update path (store link) are needed on both platforms.

## Risks

- **Mistaken `minReaderVersion` raise** locks out older apps from editing. Mitigation: raising requires a code review
  checkbox and a release-note line; the threshold is a constant per module, never computed.
- **Merge-on-read of conflicting containers** is subtle in Automerge's API; the convergence test is the safety net.
- **Hello frame ignored by old apps** means capability data is incomplete for a long time; it is advisory only.
- **Fixtures drift** if nobody captures them at release; WP6 adds a release checklist item.
- **Option 4 (new document id)** doubles storage for the affected document until the old one is retired.

## Open questions

- Should the Hello frame also carry the app build number for support diagnostics (privacy: it is a version, not an identity)?
- Where does the "Update Vesta" action point on Android sideloads and on desktop, which have no store?
