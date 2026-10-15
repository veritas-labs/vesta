# Implementation plan: document type registry and DocumentSource collapse

Written 2026-10-15 so a fresh session can do steps 1 and 2 of [shared-packages-extraction-plan.md](shared-packages-extraction-plan.md) without re-deriving context. **Status: not started.** This is a refactor: no user-visible behaviour, wire format or schema changes. Each work package (WP) is a commit-sized slice that leaves `:shared:jvmTest` green.

## Goal

1. **Part A, the registry.** Adding a Home document type means writing its schema functions and registering it once. The sync engine, mailbox, acknowledgement, activity log and changes-waiting find it through one registry. Nothing in them names a Vesta feature.
2. **Part B, the collapse.** The seven `*DocumentSource` and `*Sources` pairs stop repeating the same lock, state, sync, persist and cache code. Each keeps only its domain methods.

The proof that Part A worked is a test that registers a document type the engine has never heard of and watches it sync (WP9).

## Non-goals (do not change)

- **Document ids stay byte-identical.** `"$homeId:$feature"` with the current feature strings (`pantry`, `shopping-list`, `cookbook`, `merchants`, `mealplan`, `calendar`, `private-calendar`, `tasks`, `private-tasks`, `roster`). They are on the wire (`SyncEnvelope`) and in the mailbox and `AutomergeDocument` rows. Devices on older versions must still interoperate.
- **No schema change.** No new `.sqm`, no change to `AutomergeDocument`.
- **The Home registry (`user:homes`-style id) and Profile documents** are user-scoped and have their own classes (`HomeRegistry`, `ProfileRegistry`, both `SyncableDocument`). They stay as they are. Folding them into the registry is a later step.
- **No Gradle module split, no renames of "Home".** Those are steps 3 and 5 of the extraction plan. This plan only keeps the new code from depending on feature code so that step 3 is mechanical.
- **`BlobTransferService` keeps using `CookbookSources`.** Its fix is a `BlobReferenceSource` interface, which is a separate step.

## State of the repo (read 2026-10-15; verify before relying on it)

All paths are under `shared/src/jvmAndroidMain/kotlin/uk/veritaslabs/vesta/` unless stated.

- **Engine:** `datasync/DeviceDataSyncService.kt` (698 lines). Where document types are named:
  - suffix constants `:59-68`, the ten `documentIds()` entries `:492-494`;
  - `run()` `:197-284`: one `localChanges` collector per type, plus a roster-changed fan-out that syncs eight documents by hand (`:206-216`), plus two `privateChanges` collectors;
  - `homeIdOf` `:433`; `activityHomeId` `:642`; `headsOf` `:662`;
  - `receive` `:496-533` and `applyHomeDocument` `:539-577`: one `endsWith` branch per type;
  - `pump` `:595-616`: another `endsWith` chain choosing `generateSync`;
  - constructor `:87-101`: seven sources as separate parameters, passed on at `:119` and `:123`.
- **Mailbox:** `mailbox/MailboxDocuments.kt`. `MailboxDocuments` (interface) and `HomeMailboxDocuments`, which wraps each source in an identical anonymous `MailboxDocument`. Four sources are nullable with a `null` default (`merchants`, `mealPlans`, `calendars`, `tasks`); the roster is first in `documentIds`.
- **Acknowledgement:** `datasync/SyncAcknowledgement.kt`, one `when` branch per document calling `isInSync`.
- **Activity:** `datasync/SyncEventRecorder.kt` has `SyncModules` and `moduleBySuffix`/`moduleOfDocumentId` (`:44-62`). `datasync/ChangesWaiting.kt:33` hard-codes the two private ids.
- **Sources:** `automerge/{Pantry,ShoppingList,Cookbook,Merchants,MealPlan,Calendar,Task}Sources.kt` and `…DocumentSource.kt`. All seven sources already have the same method names: `generateSync`, `receiveSync`, `heads`, `changesSince`, `applyChanges`, `isInSync`, `refresh`. Differences that matter:
  - **Calendar and Tasks** keep a second, private source per Home (`privateForHome`, `privateChanges`), with feature keys `private-calendar` and `private-tasks`.
  - **Tasks** `edit` takes a block returning a Boolean and publishes only when it changed; the others publish unconditionally.
  - **Merchants, Cookbook** choose a key up front and return it from `save`.
  - **Pantry** has two state flows (contents and categories), a `mutate` that returns a value, and a `pending` queue of edits made while the document is an unseeded placeholder, flushed in `receiveSync`.
  - **ShoppingList** has its own `pending` queue (same idea, different shape) and is built on the Home's `PantryDocumentSource` (so `ShoppingListSources` needs `PantrySources`).
- **Roster:** `datasync/HomeRosters.kt` is not a `*Sources` class. It has `generate(homeId, state)`, `receive(homeId, state, message): Boolean`, `heads`, `changesSince`, `applyChanges`, `isInSync`, and the engine calls `rosters.project(homeId)` and `publishRosters()` after a receive.
- **Wiring:** `agent/AgentCommandService.kt` holds eight near-identical `current…: Pair<VestaDatabase, X>?` fields and accessors; `androidApp/.../MainActivity.kt` and `desktopApp/.../main.kt` pass the sources positionally into `DeviceDataSyncService`.
- **Tests that construct these directly** (they will need edits): `datasync/DeviceDataSyncServiceTest.kt:85-100`, `datasync/ActivityDevice.kt:50`, `datasync/DaemonHarness.kt:94`, `mailbox/MailboxTest.kt:52`, `mailbox/MailboxCompactionTest.kt:41`, `blobs/BlobTransferServiceTest.kt`, and about 25 acceptance and source tests that call `…DocumentSource(` or `…Sources(`. `SyncEventRecorderTest.kt:~290` already pins the id-to-module mapping.
- **Conventions (project memory, must follow):** every test method has `// Arrange` / `// Act` / `// Assert`; prefer `x?.let { }` to `if (x != null)`; each feature has Given/When/Then acceptance tests in `docs/testing/acceptance-tests.md` plus unit tests; commit only when asked, ending messages with the attribution lines.

## Design decisions taken in this plan

1. **New package `uk.veritaslabs.vesta.docs`** (in `jvmAndroidMain`) for `HomeDocument`, `HomeDocumentType`, `HomeDocumentRegistry` and, in Part B, `AutomergeDocumentCore` and `HomeDocumentStore`. Dependency rule, checked by a grep in WP1: nothing in `docs` imports `automerge.*` feature classes, `ui.*`, `home.*`, `pantry`, or any feature package. It may import `db.VestaDatabase`, `org.automerge`, and `datasync.SyncEnvelope`-free types. That is what lets step 3 lift it into a module.
2. **One interface for what a Home document gives the engine:**

   ```kotlin
   internal interface HomeDocument {
       suspend fun generateSync(state: SyncState): ByteArray?
       suspend fun receiveSync(state: SyncState, message: ByteArray)
       suspend fun isInSync(state: SyncState): Boolean
       suspend fun heads(): ByteArray
       suspend fun changesSince(saved: ByteArray): ByteArray
       suspend fun applyChanges(changes: ByteArray)
   }
   ```

   The names are the ones the sources already have, so Part A adds `: HomeDocument` to them with no logic change. `MailboxDocument` becomes a typealias or is satisfied by `HomeDocument` (its three methods are a subset). `SyncableDocument` (`generate`/`receive`) stays for the user-scoped documents; merging the two is out of scope.
3. **One descriptor per type:**

   ```kotlin
   internal class HomeDocumentType(
       val feature: String,                       // "pantry"; the id is "$homeId:$feature"
       val module: String,                        // the Sync activity module name
       val scope: Scope,                          // Shared | Private
       val open: suspend (homeId: String) -> HomeDocument?,   // null: no such Home, or this device doesn't have it yet
       val localChanges: Flow<String>,            // homeId each time it is edited on this device
       val afterReceive: suspend (homeId: String) -> Unit = {},   // roster: project + publish
   )
   internal enum class Scope { Shared, Private }
   ```

   `Shared` = synced with the user's paired devices and the Home's members and hubs, carried by the mailbox, acknowledged. `Private` = paired devices only, no mailbox. These are exactly the two behaviours the engine has today, split by `homeIdOf` returning null for the private suffixes.
4. **The registry parses ids by the last `:` segment**, not by `endsWith` chains: `parse("abc:private-tasks") = ("abc", type)`. A feature key containing `:` is rejected when the registry is built, as is a duplicate key. The existing code already assumes Home ids contain no `:` (`recordUnknownDocument` uses `substringBeforeLast(':')`); WP1 adds a test that the id generator produces none.
5. **Vesta's own list lives in one place:** `automerge/VestaHomeDocuments.kt` builds the sources and the type list from a `VestaDatabase`. It replaces the eight fields of `AgentCommandService` and the seven constructor parameters of `DeviceDataSyncService`. Accessors the UI needs (`pantry(homeId)`, `tasks(homeId)`, …) stay as thin typed getters on it, so the `openPantry = { … }` lambdas in the apps are unchanged in WP7 and shrink in Part B.
6. **Composition, not inheritance, for Part B.** `AutomergeDocumentCore` is a class that features hold and delegate to (`HomeDocument by core`), because Pantry and Shopping need to differ in more ways than an open base class would stay clean about.
7. **Existing public constructors stay** in Part B (`TaskDocumentSource(document, list, persist, onLocalChange)`), so the ~25 tests that build sources by hand are not rewritten. Only the internals change.

## Part A: the registry

### WP0. Safety net before touching anything

- Run the full `:shared:jvmTest` and record the count and any failing or skipped tests (real-daemon tests are opt-in and skipped). Compile the Android and desktop apps. This is the baseline every later WP must match.
- Read `DeviceDataSyncServiceTest` and list, per document type, which of these it already proves: (a) a direct-message sync to a second device, (b) a catch-up through the mailbox, (c) the Home arriving after the document (the `waiting` buffer), (d) it appears in Sync activity. Write the gaps down in this file's "Coverage found" section, then add the missing tests **against the current code** so they are known good before the refactor. One parameterised test per behaviour (a)-(d) over all ten ids is the target, not ten copies.
- Add `DocumentIdsTest`: for each of the ten ids, the exact string, the module, whether it is private, and whether the mailbox lists it. Today this is spread over `SyncEventRecorderTest`, `MailboxTest` and the engine; pin it in one place with the literal strings, since the ids are the compatibility contract.
- Add a test for ordering. The engine syncs roster **last** (`documentIds()`) while the mailbox lists it **first** (`documentIds` in `HomeMailboxDocuments`). Pin both with a characterization test. Do not decide yet whether the order matters.

*Done when:* new tests pass on unchanged production code, and `git diff` touches only tests.

#### Coverage found (WP0, 2026-10-15)

**Baseline** (`./gradlew :shared:jvmTest`, unchanged code): 1816 tests, 1 failed, 0 test skips. The failure is pre-existing and unrelated: `CalendarEventsAcceptanceTest.aPrivateEventIsSavedOnThePrivateCalendarMarkedWithAPadlockAndHasNoParticipants` (a Compose UI test, `CalendarEventsAcceptanceTest.kt:114`). Not fixed here. The Android and desktop app compile was not part of this test-only change.

What `DeviceDataSyncServiceTest` already proved, per document:

| Document | (a) direct message to a second device | (b) mailbox catch-up | (c) Home arrives after the document | (d) in Sync activity |
|---|---|---|---|---|
| pantry | yes (paired, and unpaired members) | yes | no | no |
| shopping-list | yes | yes | no | no |
| cookbook | yes | no | no | no |
| merchants | yes | no | no | no |
| mealplan | yes | no | no | no |
| calendar | yes | no | no | no |
| private-calendar | yes (own device; never reaches another member) | n/a | no | no |
| tasks | yes | no | no | yes (sent and received) |
| private-tasks | yes (own device; never reaches another member) | n/a | no | no |
| roster | yes (rename, membership, roles; no single per-document test) | no | no | no |

`MailboxTest` covers the mailbox at the document level for pantry, shopping list, roster and merchants only, with no engine and no Sync activity per type. Nothing anywhere covered (c) for any type.

What WP0 added (all against the current code):

- `DocumentIdsTest`: the literal id of each of the ten documents, its Sync activity module, which are private, which the mailbox lists, the mailbox order (roster first), that a mailbox built without the four optional sources silently omits them, and that Home ids contain no `:`.
- `DeviceDataSyncServiceTest`, one loop-style test per behaviour over the same ten-case table (`documentCases`):
  - (b) `everyDocumentCatchesUpAMemberWhoWasAwayThroughTheMailbox`: the nine shared documents.
  - (c) `everySharedDocumentThatArrivesBeforeItsHomeIsKeptAndAppliedWhenTheHomeArrives`: the nine shared documents (private documents are not buffered, see below).
  - (d) `everyDocumentsEditIsLoggedInSyncActivityUnderItsModule`: all ten. The "received" assertion is discriminating; the "sent" one is weak, because a handshake is logged as sent too.
  - ordering: `theEngineSyncsAHomesDocumentsInOneFixedOrderWithTheRosterLast` (the engine's `documentIds()` is private, so its order is read off the order a round's first messages reach a peer) and `DocumentIdsTest.theMailboxListsTheRosterFirst`.
  - (a) needed nothing new.

Findings the refactor has to preserve or consciously change:

1. **The `waiting` buffer is fragile.** `receive` runs `waiting.keys.retainAll(existing)` after every registry message, so buffered document messages for a Home that does not exist yet are dropped by the first registry message that does not itself create the Home. The buffer only helps when the very next registry message creates the Home. The test therefore hands B the document messages and then a single registry message that carries the Home. After the buffer drains, nothing replies to the sender; recovery otherwise relies on a later round or roster publish.
2. **Private documents are not buffered.** A private-calendar or private-tasks message for a Home the device lacks is simply dropped (`calendarSources.privateForHome(...)?.receiveSync`), and recovered by a later round. Case (c) is therefore N/A for them, as (b) is.
3. **Engine order**: pantry, shopping-list, cookbook, merchants, mealplan, calendar, private-calendar, tasks, private-tasks, roster (roster last). **Mailbox order**: roster, pantry, shopping-list, cookbook, then merchants, mealplan, calendar, tasks (roster first). Neither order is known to matter.

### WP1. The interface, descriptor and registry

- Create `docs/HomeDocument.kt`, `docs/HomeDocumentType.kt`, `docs/HomeDocumentRegistry.kt`.
- `HomeDocumentRegistry(types)` offers: `parse(documentId): Parsed?` (home id plus type), `type(feature)`, `documentIds(homeId, scope? = null)` in registration order, `open(homeId, documentId): HomeDocument?`, `types`, and `moduleOf(documentId): String?`.
- Unit tests (`HomeDocumentRegistryTest`): parse of each shape; an unknown feature returns null; an id with no `:` returns null; duplicate or `:`-containing keys are rejected at construction; `documentIds` filters by scope and keeps registration order.
- Grep check for the dependency rule in design decision 1.

*Done when:* the new tests pass; nothing outside `docs` uses it yet.

### WP2. Make the sources and roster speak `HomeDocument`

- Add `: HomeDocument` (plus `override`) to the seven `*DocumentSource` classes. Their method names already match, so no logic changes. Check `Task…`/`Calendar…` `isInSync` and the rest exist (they do per `SyncAcknowledgement`).
- Give `HomeRosters` a `homeDocument(homeId): HomeDocument?` that returns an adapter over its existing `generate`, `receive`, `heads`, `changesSince`, `applyChanges`, `isInSync`, and returns null when `heads(homeId)` is null (no roster yet), matching what `HomeMailboxDocuments` does today. Its `receiveSync` returns normally; the "no roster" case is the null from `open`.
- Write `automerge/VestaHomeDocuments.kt`: builds the seven sources (Shopping from Pantry) and the ten `HomeDocumentType`s: `open` = `forHome`/`privateForHome` plus the roster adapter, `localChanges` = the sources' `localChanges`/`privateChanges`, roster `afterReceive` = `rosters.project(homeId)` then publish. It needs `HomeRosters` and a `publishRosters` callback, so it takes them as constructor arguments (the engine builds the registry; see WP7).
- Unit test: every type opens a document for an existing Home and returns null for a missing one.

*Done when:* all tests green; production behaviour unchanged (nothing consumes the registry yet).

### WP3. Acknowledgement and activity modules

Smallest consumers first, to prove the registry in use.

- `SyncAcknowledgement(registry)`: `isInSync(homeId, documentId, state)` = `registry.open(homeId, documentId)?.isInSync(state) ?: false`. Delete the 7-source constructor and the `when`.
- `moduleOfDocumentId` becomes `registry.moduleOf(documentId)`. `SyncModules` constants stay (the activity UI and storage use those strings) but `moduleBySuffix` goes. Update `SyncEventRecorderTest`'s mapping test to call the registry (it must produce the same ten results and null for registry and profile).
- `ChangesWaiting.privateDocumentIds(homeId)` = `registry.documentIds(homeId, Scope.Private)`.

*Done when:* `SyncActivityServiceTest`, `ChangesWaitingTest`, `SyncEventRecorderTest`, `DeviceDataSyncServiceTest` green.

### WP4. Mailbox

- `HomeMailboxDocuments(registry, rosters-less)`: `documentIds(homeId)` = `registry.documentIds(homeId, Scope.Shared)`; `open` = `registry.open`. Keep the `MailboxDocuments` interface, because `MailboxTest`'s `HidingDocuments` wraps it.
- Delete the anonymous wrappers and the nullable constructor parameters. This removes the case where a type is silently missing from the mailbox because a constructor argument was left `null`.
- Update `MailboxTest.kt:52` and `MailboxCompactionTest.kt:41` to build the registry.

*Done when:* the 31 mailbox tests are green. **Ordering:** if the WP0 order test shows the mailbox must list the roster first, give `HomeDocumentType` a `priority` or keep registration order roster-first and re-pin the engine's order in WP6.

### WP5. Engine: identify and route documents

Still in `DeviceDataSyncService`, but with the registry passed in alongside the existing source parameters (they go in WP7).

- `homeIdOf`, `activityHomeId` and `recordUnknownDocument` use `registry.parse`. A document id that parses but whose Home this device lacks is buffered in `waiting`, as today; one that doesn't parse is "unknown to this version" (ADR-034), as today.
- `peersFor`: `Private` type or non-Home document means paired devices only; `Shared` means paired devices plus the Home's roster devices. Same sets as today.
- `documentIds()` (`:492`): the two user documents plus `registry.documentIds(home.id)` for each Home.
- `headsOf` = `registry.open(homeId, documentId)?.heads()`.

*Done when:* `DeviceDataSyncServiceTest`, `UnsupportedInputActivityTest` and the order test are green.

### WP6. Engine: send and receive

- `pump`: replace the `endsWith` chain with `registry.open(homeId, documentId)?.generateSync(state)`; registry and profile stay as they are.
- `applyHomeDocument`: `registry.open(homeId, documentId)` null means "no such Home yet" (`false`, buffered); otherwise `receiveSync`, then `type.afterReceive(homeId)`. The roster special cases (`rosters.project`, `publishRosters` at `:527`, `:542`) move into the roster type's `afterReceive`.
- Keep `recordingReceived`, `states`, `waiting`, `lock` and `peerDownUntil` exactly as they are. This WP changes lookups only, not locking or ordering of sends.

*Done when:* everything green, plus a manual diff review of `pump` and `receive` showing no change in what is sent or when.

### WP7. Engine: local-change fan-out and constructor

- `run()`: replace the eight collectors and two private collectors by one loop over `registry.types`: for each type, collect `localChanges`; for `Shared` types deposit to the mailbox then `syncDocument`; for `Private` types `syncDocument` only. The roster-changed block (`:206-216`) becomes "deposit the roster, then `syncDocument` for every `Shared` type's id".
- Constructor: `DeviceDataSyncService(client, database, devices, documents: VestaHomeDocuments, …)` replaces the seven source parameters. `HomeRegistry(database, pantrySources)` and `BlobTransferService`'s `CookbookSources` get them from `documents` (`documents.pantries`, `documents.cookbooks`).
- `AgentCommandService`: its eight fields become one `homeDocuments(database)`; keep `pantry(database, homeId)`-style accessors as one-liners over it.
- Update `MainActivity.kt`, `main.kt`, and the test harnesses listed under "State of the repo" (`DeviceDataSyncServiceTest`, `ActivityDevice`, `DaemonHarness`, `BlobTransferServiceTest`).

*Done when:* `:shared:jvmTest` passes with the same count as WP0 plus the new tests, and both apps compile.

### WP8. Delete what is now dead

- Remove the suffix constants, `homeIdOf` chains, the nullable mailbox parameters, `moduleBySuffix`. Grep for the ten `…Suffix` names and the ten `…DocumentId` imports in `datasync/` and `mailbox/`; none should remain.
- Check `DeviceDataSyncService.kt` has no import of a feature `…Sources` class and no feature name. Target: roughly 150-200 fewer lines.

### WP9. Prove the goal: a type the engine never heard of

- **Seam (added during WP9):** `DeviceDataSyncService` and `VestaHomeDocuments` take a defaulted `extraTypes: List<HomeDocumentType>` appended to the registry, so a device (or a test) can register a type without editing the engine; `HomeDocumentType` and `Scope` became public for that. Committed separately, behaviour unchanged.

- `DocumentTypeRegistrationAcceptanceTest` (jvmTest): a throwaway `probe` document type, defined only in the test (a trivial Automerge map with a counter), registered through the same `HomeDocumentType` API. On the fake mesh with two devices in one Home:
  - **Given** a Home on two devices and a `probe` type registered on both, **When** device A edits it, **Then** device B has the edit through direct sync, with no engine change;
  - **Given** B was offline, **When** A edits and later B reconnects, **Then** B has it through the mailbox;
  - **Given** a `Private` probe, **Then** it reaches A's other paired device but not a Home member's device and is not in the mailbox;
  - **Then** Sync activity shows the probe's events under its module, and Changes waiting reflects an unacknowledged probe edit.
- Add the criteria to `docs/testing/acceptance-tests.md` as a section "Document type registry (this plan)".

*Done when:* the test passes and a reviewer can see no engine edit was needed to make it.

## Part B: collapse the DocumentSources

### WP10. `AutomergeDocumentCore`

Create `docs/AutomergeDocumentCore.kt`. It owns the `Document`, the `Mutex`, `persist` and `onLocalChange`, and implements `HomeDocument`:

- `suspend fun <R> edit(change: Document.() -> R): R`: lock, run, `publish()`, `persist(document.save())`, unlock, then `onLocalChange()`.
- `suspend fun editIfChanged(change: Document.() -> Boolean): Boolean`: as `edit`, but publishes and persists only when the block returns true (Tasks' behaviour); `onLocalChange` only when changed.
- `HomeDocument` implementation: `generateSync`, `receiveSync` and `applyChanges` through one `applyIncoming` (heads before and after; publish and persist only if they moved), `heads` (`encodeHeads`), `changesSince`, `isInSync`.
- `refresh()` and a `publish: () -> Unit` constructor argument that recomputes the source's state flow(s) from the document.
- Unit tests (`AutomergeDocumentCoreTest`) in jvmTest: edit persists and fires `onLocalChange` once; `editIfChanged` with no change does neither; two concurrent edits don't interleave (the lock); an incoming sync message that changes nothing doesn't persist; `receiveSync` from a second document converges.

### WP11. `HomeDocumentStore`

Create `docs/HomeDocumentStore.kt`: the per-Home cache that every `*Sources` class repeats.

```kotlin
internal class HomeDocumentStore<S : HomeDocument>(
    private val database: VestaDatabase,
    val feature: String,
    private val nowMillis: () -> Long,
    private val create: suspend (homeId: String, document: Document, persist: suspend (ByteArray) -> Unit, onLocalChange: () -> Unit) -> S?,
) {
    val localChanges: SharedFlow<String>
    suspend fun forHome(homeId: String): S?
}
```

`forHome` does what each `*Sources.forHome` does now: take the lock; return null and evict the cache entry if the `Home` row is missing; otherwise return the cached source or open it (load the stored bytes or start `Document()`, `persist` = `automergeDocumentQueries.upsert(documentId, homeId, feature, bytes, now)`, call `refresh()` when nothing was stored). `create` returning null is how Shopping says "no Pantry yet". Also adds `homeDocumentId(homeId, feature)`; the existing `xxxDocumentId` functions become one-line calls to it (their ~40 call sites are not edited).

Tests: opens once and returns the same instance; null for a missing Home and eviction after the Home is deleted; a stored document loads; a new document is stored straight away; `localChanges` emits the Home id on an edit but not on an incoming sync.

### WP12. Migrate the three simplest: Merchants, Cookbook, MealPlan

These are near-identical. Do Merchants first and treat it as the pattern review; then the other two.

- `MerchantsDocumentSource` keeps its constructor, holds an `AutomergeDocumentCore` built in `init`, declares `HomeDocument by core`, and each domain method becomes `core.edit { … }`. The state flow's `publish` is `{ state.value = document.readMerchants() }`.
- `MerchantsSources` becomes a thin class over `HomeDocumentStore<MerchantsDocumentSource>` (or is replaced by it in `VestaHomeDocuments`; keep the class name until the last WP so the many call sites do not move).
- `openMerchantsDocumentSource(...)` stays as a function (tests use it) but its body is now `HomeDocumentStore`'s open path.
- Run the matching `…DocumentSourceTest`, `…AcceptanceTest` and `DeviceDataSyncServiceTest`.

### WP13. Tasks and Calendar (the private variants)

- A second `HomeDocumentStore` for the private feature; `forHome` and `privateForHome` stay as methods on `TaskSources` and `CalendarSources`, each delegating to a store. `localChanges` and `privateChanges` stay as they are.
- Tasks uses `editIfChanged`. Calendar's `edit`/`save` shape: check which of the two it needs when reading the file (not read in detail while writing this plan).
- Extra care: `TaskSources`' two caches evict independently when a Home is deleted (`:44-56`); the store must do the same per instance.

### WP14. Pantry and ShoppingList (last: the special ones)

- **Pantry:** two state flows go through the core's `publish` (it updates both). `mutate(block)` is `core.edit`. The `pending` queue of edits made while unseeded, and its flush after an incoming message, stays in `PantryDocumentSource` for now, hooked in by wrapping `core.receiveSync`.
- **ShoppingList:** the store's `create` fetches the Home's Pantry source first and returns null without one; its `pending` queue stays in the class.
- **Decision to make here, not before:** both classes have a "queue edits until the creator's document has arrived" feature written twice with different shapes (`(Document) -> Unit` vs `suspend () -> Unit`). If, with the core in place, a shared `SeedGate(isSeeded)` falls out cleanly, extract it into the core; if unifying them needs contortions, leave both as they are. Do not force it. `UnseededEditTest` (8 tests) is the guard.

### WP15. Remove the per-type wiring and finish

- `VestaHomeDocuments` builds all stores in one place; `AgentCommandService`'s typed accessors read from it.
- Delete anything unused: grep for `openXDocumentSource` callers (keep any that tests still use), the repeated `_localChanges` flows, the `Pair<VestaDatabase, X>?` fields.
- Measure: `git diff --stat` across both parts. **Expected: about 900-1,000 lines removed** across the engine, mailbox, ack, sources and `AgentCommandService` (roughly 300 in Part A, 650 in Part B). That is lower than the 1,500-2,000 the extraction plan first guessed; the saving that matters is that a new type is now one registration.
- Write ADR-035 (document type registry and the document core), recording the decisions above and how the "As built" differs. Update the extraction plan's finding 1 and 2 with what shipped.

## Risks and how this plan handles them

| Risk | Handling |
|---|---|
| A document id or feature string changes, breaking older devices | `DocumentIdsTest` pins the literal ids (WP0); non-goal above. |
| A type is missing from one list, the existing hazard | One registry; WP4 removes the nullable parameters; WP9 proves registration is enough. |
| Order of sends or mailbox entries matters and is changed | WP0 pins both orders; WP4/WP6 re-pin explicitly if they change. |
| Lock or ordering behaviour in the engine changes | WP5-WP7 change lookups only; `lock`, `states`, `waiting`, `peerDownUntil` are untouched. Review `pump` and `receive` by diff. |
| `afterReceive` for the roster publishes at a different moment than `:527`/`:542` | WP6 moves the exact calls; `HomeRosters*Test` and `DeviceDataSyncServiceTest` roster cases are the guard. |
| Part B changes a persistence or publish detail (e.g. Tasks' publish-only-if-changed) | `editIfChanged` exists for exactly that; per-source tests plus `AutomergeFixtureSourceTest` and `UnseededEditTest`. |
| Large churn in tests that build sources by hand | Decision 7: public constructors stay, so only the six harnesses in WP7 change. |
| Android or iOS break because the refactor was only compiled for the desktop | Compile both apps at the end of every WP from WP7 on. iOS has no Automerge, so this code does not compile there. |

## Open questions for you

- **Package name `docs`:** acceptable, or prefer something that won't be confused with `docs/`? (`crdt`, `homedocs`.)
- **Registration order:** if WP0 shows nothing depends on the engine's roster-last order, may it be unified with the mailbox's roster-first order?
- **ADR-035:** write it as WP15 does, or fold this into the existing ADR-006 (the multi-document envelope)?
- **Pantry and Shopping `SeedGate`:** happy to leave the decision to WP14, or do you want it extracted regardless?
