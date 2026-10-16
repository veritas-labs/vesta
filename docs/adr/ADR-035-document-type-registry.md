# ADR-035: Document type registry and document core

**Status:** Accepted, built (2026-10-15). Refines ADR-006 (multi-document envelope), ADR-013 (device data sync), ADR-020
(mailbox) and ADR-033 (Sync activity). Plan: [document-registry-and-source-collapse-plan.md](../plans/document-registry-and-source-collapse-plan.md).

## Context

Every Home document type (Pantry, shopping list, cookbook, merchants, meal plan, calendar, tasks, the roster, and the private
calendar and tasks) was named by hand in the sync engine, the mailbox, the acknowledgement, the activity log and
changes-waiting, and each `*DocumentSource` and `*Sources` pair repeated the same lock, state flow, sync, persist and per-Home
cache code. Adding a type meant editing about a dozen places, and a type missed in one list silently did not sync (the mailbox
had four nullable parameters for exactly this reason).

## Decision

1. **One descriptor per type.** `HomeDocumentType(feature, module, scope, open, localChanges, afterReceive, afterSync,
   resyncsHome)` in `docs/`, held by a `HomeDocumentRegistry`. The engine, mailbox, acknowledgement, activity module lookup,
   private-id lookup and changes-waiting go through the registry and name no Vesta feature.
2. **Document ids and feature strings do not change.** `"$homeId:$feature"` stays byte-identical; `DocumentIdsTest` pins the
   literal id of each of the ten documents. The registry parses an id on its last `:`; a feature containing `:` or a duplicate is
   rejected at construction.
3. **Scope.** `Shared` types go to paired devices, the Home's members and hubs, ride the mailbox and are acknowledged;
   `Private` types go to paired devices only. This is the split the engine already had.
4. **Composition for the collapse.** `AutomergeDocumentCore` (lock, edit, persist, sync, heads, changes) and
   `HomeDocumentStore` (per-Home cache, Home-exists check, `localChanges`) are held and delegated to by each source, not
   inherited. Existing public constructors of the sources stay, so tests that build sources by hand are untouched.
5. **One place builds the stores.** `VestaHomeSources` builds the seven `*Sources` of a database (Shopping from Pantry) and is
   the object the apps, `AgentCommandService` and the sync engine share. `VestaHomeDocuments` takes it and registers the ten
   types. `AgentCommandService`'s typed accessors read from `homeSources(database)`.
6. **Proof of extensibility.** A test registers a document type the engine has never heard of and watches it sync, ack, show in
   Sync activity and ride the mailbox: `DocumentTypeRegistrationAcceptanceTest`, added alongside this work.

## As built

Differences from the plan, and what was left alone:

- **Roster hooks are split.** The plan had one `afterReceive` (project and publish). The roster must project its members even
  for a message replayed from the "waiting" buffer, but must publish this device's own member and devices only for a live message
  after it has been recorded and before the sender is answered. So there are two hooks: `afterReceive = project` (also on replay)
  and `afterSync = publishRosters` (live only). A third field, `resyncsHome`, replaces the engine's roster special case
  "after a roster change, sync the Home's other shared documents".
- **Engine order versus mailbox order.** The engine's `SyncOrder` list keeps the send order with the roster last; the mailbox
  lists the roster first, and `VestaHomeDocuments` registers in the mailbox order. Both orders differ on purpose and both are
  pinned by tests (`DocumentIdsTest`), so a change to either fails a test instead of changing the wire silently. Unifying them was
  not needed and was not done.
- **`SeedGate` was not extracted.** The Pantry and shopping list "queue edits until the creator's document has arrived" code
  has different shapes and stays in each class; `UnseededEditTest` guards it.
- **Names.** The shared object is `VestaHomeSources` (public, because the apps build it), not a field set on
  `VestaHomeDocuments`; `VestaHomeDocuments` is internal and takes it. The old secondary constructor with seven source
  parameters was removed in WP15 along with the unused delegating getters; two mailbox tests now pass a `VestaHomeSources`.
- **Kept on purpose.** `openPantryDocumentSource` and the other `open…DocumentSource` functions remain because tests build
  sources with them; they are no longer used by production code except through the stores.
- **The saving is smaller than planned.** The plan expected about 900-1,000 lines removed. Measured on production Kotlin from
  the commit before the work (`8026c76`) to this one: +961 -1,174 lines over 31 files, a net 213 removed (the `docs/` package and `VestaHomeDocuments` add about 520). The new `docs/` package itself is about 400
  lines, which offsets most of what left the engine, mailbox and sources. The saving that matters is that a new type is one
  registration and cannot be missed in a list.
- **Tests.** `DocumentIdsTest`, `HomeDocumentRegistryTest`, `VestaHomeDocumentsTest` and the per-type coverage tests pin the
  behaviour; `DeviceDataSyncServiceTest` and the Compose UI acceptance tests are known to be flaky under load.
- **After the kits extraction (ADR-038).** The registry types and the engine moved to `docs-kit` and `sync-kit` and were renamed
  (`HomeDocumentType` is `SpaceDocumentType`, `VestaHomeDocuments`' role is the app-supplied list of types, `HomeDocumentRegistry`
  is `SpaceDocumentRegistry`); Vesta still supplies its ten types through `VestaHomeSources`. The engine's `SyncOrder` list, which this
  ADR calls the engine's own, still names Vesta's nine features and now lives inside `sync-kit` (a follow-up). Both orders and their tests
  are unchanged.

## Consequences

- A new Home document type is its schema functions plus one `HomeDocumentType` entry.
- The registry is internal to the shared module for now; extracting it as a package (extraction plan, finding 1) is
  mechanical but not yet done.
- Not changed: wire formats, document ids, the engine's lock, `states`, `waiting` and `peerDownUntil` behaviour.
- **Kits extraction, WP5 (2026-10-16).** `VestaHomeDocuments` was deleted. The engine registers the roster type itself, first, and
  takes the app's nine other types as `documentTypes` (`VestaHomeSources.documentTypes()`), replacing `extraTypes`; the order and
  ids are unchanged (`DocumentIdsTest`). Tests that need the registry without the engine use `TestHomeDocuments`.
