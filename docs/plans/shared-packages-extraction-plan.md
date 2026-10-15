# Extracting reusable packages from Vesta

Status: proposal, 2026-10-15. Nothing here is built. It comes from a code audit of `:shared`, the Android and desktop apps, and the build files. The audit read the sync, join, pairing, mailbox, blob, agent, x0x, DB and app-wiring code. It did not read every file and did not build or run anything.

## Goal

Several apps will be built on the same architecture (x0x networking, Automerge documents, workspace membership, invites, device pairing). The shared features must be written once and reused, not copied per app.

## Why this needs doing

Everything lives in one Gradle module, `:shared`, under one package (`uk.veritaslabs.vesta`). Platform code is split across `commonMain`, `jvmAndroidMain`, `androidMain`, `jvmMain` and `iosMain`. Nothing enforces a boundary between generic infrastructure and Vesta's own features, and the infrastructure has absorbed Vesta's domain. The sync engine has the name of every Vesta document type written into it.

## Target packages

Names are provisional.

| Package | Contains | Depends on |
|---|---|---|
| `x0x-kit` | client, SSE and event hub, health, peers, diagnostics, embedded daemon, FFI, status UI | none |
| `crdt-docs` | Automerge document handle, registry, persistence, sync envelope, mailbox, ack, changes-waiting, sync activity | x0x-kit |
| `groups-kit` | workspace ("Home") roster, invite links, join protocol, hub, x0x group lifecycle | crdt-docs |
| `device-kit` | pairing link, `DeviceSync` group, device roster, device data sync | groups-kit |
| `assistants-kit` | agent commands, assistant grants, approval | x0x-kit |
| `blobs-kit` | blob store, transfer, janitor, photo encoding | crdt-docs |
| `app-shell-kit` | DB safety, settings, profile, onboarding, theme scaffolding, generic UI | none |

## Findings

### 1. The sync engine hard-codes every document type (highest value)

**Shipped (2026-10-15, [ADR-035](../adr/ADR-035-document-type-registry.md)).** `HomeDocumentType` and `HomeDocumentRegistry` replace the suffix constants, `endsWith` chains, duplicate lists and nullable mailbox parameters; the engine, mailbox, acknowledgement, activity modules and changes-waiting are registry lookups, and `VestaHomeDocuments` is Vesta's one list of ten types. Roster behaviour became two hooks (`afterReceive`, `afterSync`) and a `resyncsHome` flag. Still open: the registry is internal to `:shared`, not yet its own package. The text below describes the state before this work.

`DeviceDataSyncService.kt` is 698 lines, and each new feature must be added to it by hand.

- **Suffix constants.** `DeviceDataSyncService.kt:59-68` has a `":pantry"`, `":cookbook"`, `":tasks"` suffix per document type, plus `:private-*` variants.
- **Constructor.** It takes `pantrySources`, `shoppingSources`, `cookbookSources`, `merchantsSources`, `mealPlanSources`, `calendarSources` and `taskSources` as separate parameters.
- **Dispatch.** Several `if (documentId.endsWith(...))` chains cover receive, generate, home-id extraction and heads (around lines 434-441, 544-574, 598-616, 644-665).
- **Duplicate lists.** The same document list is repeated at line 119 (`HomeMailboxDocuments`) and line 123 (`SyncAcknowledgement`).
- **Silent-failure risk.** In `HomeMailboxDocuments`, `merchants`, `mealPlans`, `calendars` and `tasks` are nullable, with a different default from the others. A document type added to one list and missed in another silently does not sync.
- **Mailbox wrappers.** The mailbox hand-wraps each type in an identical anonymous `object : MailboxDocument { heads / changesSince / applyChanges }` (`MailboxDocuments.kt`). `SyncableDocument` is already the right interface but is not the single seam.
- **Module names.** `SyncModules` and `moduleBySuffix` in `SyncEventRecorder` are a fourth copy of the list.

**Proposal:** one `SyncableDocumentType` descriptor (feature key, scope shared or private, factory) and one `DocumentRegistry`. Engine, mailbox, ack, activity and changes-waiting all become lookups. A new app registers its types and gets sync without touching engine code.

### 2. Every `*DocumentSource` and `*Sources` pair is a copy

**Shipped (2026-10-15, [ADR-035](../adr/ADR-035-document-type-registry.md)).** `AutomergeDocumentCore` and `HomeDocumentStore` (composition, not inheritance) hold the shared lock, sync, persist and per-Home cache code, and `VestaHomeSources` builds all seven `*Sources` in one place. The saving measured on production code was well under the estimate below (see ADR-035 "As built"). The Pantry/Shopping `SeedGate` was not extracted. The text below describes the state before this work.

Seven document types each have a `*DocumentSource` (about 80-250 lines) and a `*Sources` (about 30-90 lines).

- **Identical machinery.** Each repeats a mutex plus `MutableStateFlow`, `generateSync`, `receiveSync`, `heads`, `changesSince`, `applyChanges`, `applyIncoming`, `refresh`, `publishAndPersist` and the `onLocalChange` callback.
- **Identical wrapper.** The per-Home cache is the same too: a map, a lock, a `localChanges` flow, a check that the Home row exists, and an `open…DocumentSource` function (load or create, upsert, refresh).
- **Duplicated ids.** `fun xxxDocumentId(homeId) = "$homeId:$feature"` appears once per type.
- **Domain code is small.** Only `readX`, `putX` and `deleteX` differ.

**Proposal:** a generic `AutomergeDocumentHandle<T>` taking `read: Document.() -> T` and a persist function, plus a generic `PerScopeSources<T>(featureKey, factory)`. Each feature shrinks to its schema functions and its repository interface. Expected saving is roughly 650 lines in the sources alone, and about 900-1,000 with the registry work of finding 1 (see [document-registry-and-source-collapse-plan.md](document-registry-and-source-collapse-plan.md); the first draft's 1,500-2,000 was an overestimate). The saving that matters is that a new document type becomes one registration.

### 3. x0x client and daemon layer

The most ready-to-extract layer, already behind an interface with `Abstract…` plus per-platform actuals.

- **Core client.** `X0xClient`, `AbstractX0xClient`, `X0xModels`, `X0xEventHub`, `X0xApiException`.
- **Platform actuals.** `StandaloneX0xClient.jvm`, `EmbeddedX0xClient.android`, `EmbeddedX0xClient.ios`, the Rust crate `ffi/x0x-ffi`, and `scripts/build-android-ffi.sh` and `scripts/build-ios-ffi.sh`.
- **Health and diagnostics.** `X0xHealth`, `X0xPeers`, `X0xNetworkDiagnostics`, `X0xNetworkPolicy`, `X0xStuckDetector`, `X0xConnectivityIndicator`.
- **UI.** `X0xStatusCard`, `NetworkPeersScreen`, `NetworkDiagnosticsScreen`.

Leaks to clean up:

- `InviteLifetime.kt` cites Vesta plan docs in its comments, and its fields (`HubEnrolment`, `HomeJoin`) are domain terms.
- `X0xStatusCard` is described as a "Home-screen demo card" and probably belongs in an `x0x-ui` artifact.
- The client's KDoc says it was ported from the Colo project. Colo is already a second consumer, which is a reason to share one client.
- The Gradle logic for the Rust FFI and cinterop lives in `shared/build.gradle.kts` and needs to move into a convention plugin.

### 4. Membership, invites and join

- **`HomeRosterDocument` (405 lines).** A generic "who is in this workspace" CRDT with members, devices, hubs, group id, invites, tombstones and roles, all as flat scalar keys. It is the best candidate for a `Workspace` abstraction.
- **`HomeJoinService` (365 lines).** The protocol (Invite, Accepted, Left, Removed, Ack, Request, Denied, plus pending-message retry per ADR-017) is domain-neutral except that it imports `storeEmptyPantry` and `storeEmptyShoppingList`. A new Home seeds Vesta's own documents inside the join logic. That must become a hook such as `onWorkspaceCreated`.
- **`InviteLink`.** The scheme is hard-coded to `vesta://invite` and the payload carries a `homeName`. It needs a configurable scheme and a generic name field.
- **`HomeGroups` (287) and `HomeHubs` (156).** The x0x group lifecycle: create, join, role sync, removal, replace-on-creator-removal. Generic apart from the group name `"Vesta Home"` and the word "Home". The hub concept (an always-on x0x-only peer, ADR-020) is central to sync without overlapping online time.
- **Tables.** `HomeGroup.sq`, `HomeMember.sq`, `InviteCode.sq`, `JoinRequest.sq`, `PendingJoin.sq`, `PendingMessage.sq`, `Mailbox.sq`.

Traps:

- **Naming.** "Home" is in class names, table names, message kinds and screens. Rename it before extraction. The table rename needs a SQLDelight migration.
- **Domain depends on UI.** `HomeGroups`, `HomeHubs` and `HomeJoinService` import `uk.veritaslabs.vesta.ui.screens.MemberRole`. That enum has to move out of `ui.screens`.

### 5. Device pairing and device sync

- **Files.** `DeviceSyncService` (352 lines), `DevicePairingViewModel`, `PairingLink`, `DeviceSyncMessage`, `SyncedDeviceRepository` and the pairing screens are generic.
- **Coupling.** `PairingLink` is hard-coded to `vesta://pair`. `DeviceSyncService` hard-codes `GroupName = "DeviceSync"` and imports `getDeviceName` from the app's root package.
- **Layering.** `DeviceDataSyncService` mixes device-to-device data sync with Home sync and mailbox hooks. Split them. Device sync is the primitive. Home sync is the same documents with a different peer set and the mailbox on top.

### 6. Mailbox (offline catch-up)

`MailboxWriter`, `MailboxReader`, `MailboxEntry`, `MailboxSnapshot` and `StoredHeads` let two devices that are never online together still converge. This is the most reusable idea in the app. It is already written against `MailboxDocuments` and only needs the registry from finding 1. `changesSince` and `encodeHeads` need to leave the Vesta package.

### 7. Sync activity and diagnostics (ADR-033)

`SyncEventRecorder`, `SyncActivityService`, `SyncActivityRepository`, `SyncActivityViewModel`, `SyncVerdict`, `ChangesWaiting`, `SyncAcknowledgement`, `SyncActivityComponents` and `SyncActivityScreen`. Every sync app needs this. To genericise: `SyncModules` (a hard-coded list) and the private-document ids named in `ChangesWaiting.privateDocumentIds`.

### 8. Assistants and agent commands (ADR-009, ADR-027)

The grant model (capability, per-workspace policy of Ask or Auto, activity log, approval card) is reusable. Pantry-specific parts are `AddPantryItem`, `PantryCommandHandler` and the `PantryAdd` capability. These sit behind sealed types, so the shape needs a plugin point: a `CommandFamily` registry where an app registers its own commands and capabilities. Keep the two-gate auth design (x0x trust plus in-app approval) unchanged. Also generic: `AgentAllowlist`, `AgentReplies`, `CommandLog`, and the `skills/vesta` skill as a template for per-app skills.

### 9. Blob storage and transfer (ADR-023)

`BlobStore`, `BlobRepository`, `BlobTransferService`, `TransfersJanitor`, `PhotoEncoder` (with platform actuals), `PhotoFetcher`, `BlobImage`. One coupling blocks reuse: `BlobTransferService` imports `CookbookSources` and scans only the cookbook for blob references. It needs a `BlobReferenceSource` interface that any document can implement. `Placeholder.kt` (287 lines) is Vesta art and stays in the app.

### 10. Database infrastructure

- **Generic.** `DatabaseSafety` (downgrade refusal, `VACUUM INTO` backups before upgrade), `DatabaseOpenResult`, `DatabaseDriverFactory` and `DatabaseFiles` (expect/actual), `AutomergeDocument.sq`, `UserDocument.sq`, `AppSetting.sq`, `PeerSync.sq`.
- **Coupling.** `DatabaseDowngradeException` says "The Vesta database…". `AutomergeDocument.homeId` has a foreign key to `Home(id)`, so a package that owns that table needs a generic `scopeId`.
- **Unresolved design problem.** There is one 20-version SQLDelight schema for every concern. A shared package cannot own part of a schema an app also owns. Options: per-package databases (cleanest, loses cascade deletes); SQLDelight multi-module with `dependency(project(...))`; or packages exposing `.sq` files that apps include. This must be decided before any database-backed package is extracted.

### 11. App shell and generic UI

- **Profile.** `ProfileRepository`, `ProfileRegistryDocument`, `ProfileRegistry`, `ProfileChangeListener` are generic apart from the `user:profile` doc id.
- **Settings and theme.** `AppSettingsRepository` and `ThemeMode` are generic. `VestaTheme` and `VestaTypography` are branding. `AppTheme.Vesta` implies multiple themes, so a theme-token interface that apps implement would work.
- **Licences, branding, onboarding.** The `generateLicenses` task, `OpenSourceLicense`, the branding dir plus symlink convention, the onboarding slide-toggle framework (ADR-030) and `scripts/slides-to-vectors.py`.
- **File I/O.** `FileExporter` and `FileImporter` are already small expect/actual interfaces.
- **Components that move as-is.** `ConfirmDeleteDialog`, `PageDots`, `PrimaryButton`, `SecondaryButton`, `RadioOptionCard`, `ActionCard`, `ContentWidth`, `FabClearance`, `Undo`, `BarcodeCameraView`, `PhotoPicker`.
- **Feature screens worth sharing.** `InviteLinksScreen`, `RedeemInviteScreen`, `MembersScreen`, `MemberDetailScreen`, the `DevicePairing*Screen` set, `HubScreen`, `HubManagerScreen`, `HubTutorialScreen`, `SyncActivityScreen`, `SettingsSyncedDevicesScreen`, `ProfilePickerScreen`, `ProfileNameScreen`. These need a check for hard-coded "Vesta" and "Home" copy and will need string resources or a `Terms` object.

### 12. App composition

`VestaApp` in `App.kt` is 1,035 lines. It builds the database, x0x client, device sync, profiles, hubs, assistants, blobs, settings and onboarding in one composable, with the whole navigation graph. `main.kt` passes nine `open…` lambdas and `dataSync` takes ten positional constructor arguments. A new app would copy all of this. The extracted packages need a composition root, such as `VestaCore.create(config)`, that returns the wired services and leaves only feature-specific parts to the app. The per-document `open…` lambdas are a symptom of finding 1.

### Smaller items

- **Autonomi.** The `autonomi/` package has only a status client (`connectionStatus`) and a status card. Nothing else in the code uses Autonomi for storage, and the embedded library was dropped from the Android build in a recent commit. Decide whether `autonomi-kit` is a real package or a placeholder before extracting it.
- **Platform primitives.** `Platform`, `getAppInfo` and `getDeviceName` are in the root package, and `DeviceSyncService` and others import them. Move them to `app-shell-kit`.
- **Versioning.** ADR-034 (document format versioning) and `UnsupportedVersion` events should ship inside `crdt-docs`.
- **Tests.** `jvmTest/datasync` (19 files) and `acceptance` (21) should move with the code. The fake mesh they use should become a published `x0x-kit-testing` artifact. The acceptance suite will need splitting by package.
- **ADRs.** ADRs 003-008, 012-014, 017-021, 023, 027, 033 and 034 describe infrastructure and should travel with the packages.

## Suggested order

1. **Type registry (finding 1), in place, before extracting anything.** It removes the engine's knowledge of Vesta documents and makes later splits mechanical. It is the riskiest change because it touches the sync path, so the existing tests must be green first.
2. **Collapse the DocumentSource duplication (finding 2).** The most code removed for the least risk.
3. **Split `:shared` into Gradle modules along the table above, `x0x-kit` first.** Fewest dependencies, cleanest interface.
4. **Resolve database composition (item 10)** before touching `groups-kit` or `device-kit`. It affects every table.
5. **Rename "Home" to a neutral term** before any package is published, because afterwards it is a breaking change for every consumer.

## Decisions (2026-10-15)

- **Separate repos.** Consuming apps pull published artifacts, so each kit is a versioned library with a deliberate public API. Vesta becomes a consumer of its own kits. Consequences: Kotlin explicit-API mode and a binary-compatibility check on every kit; semantic versioning; the kits live in one kits repo (they version together and depend on each other) and publish to a Maven repository; Vesta pins released versions rather than project dependencies. Breaking changes must be settled before the first release.
- **Neutral term: "Space".** Rename "Home" to "Space" in the kits' API before the first release. Persisted names and wire ids must not change for existing installs: document ids stay `"$spaceId:$feature"` byte-identical to today's `"$homeId:$feature"`, and tables, x0x group names and invite-link formats keep their current spelling or get an explicit migration. Vesta's UI may keep saying "Home".
- **Services only, no UI.** Finding 11 (generic UI) is dropped. `x0x-kit` loses its status UI; `app-shell-kit` shrinks to DB safety and settings services (rename or fold into the other kits). Each kit exposes state as flows and commands; apps build their own screens.
- **Future apps use Autonomi; Vesta does not.** Nothing in Vesta touches Autonomi today, so no existing code needs extracting for it. It is a new capability for future apps. It becomes a separate kit (working name `autonomi-kit`) once an app needs it, designed against that app's real requirements: what it carries (blobs, documents, or both) and how it sits beside x0x. Do not add storage interfaces to the existing kits speculatively; add them when the first Autonomi app shows what shape it needs.

## Revised order

3. Make the registry and engine public inside `:shared` (explicit API; Vesta's ten types supplied from outside; roster hook and SeedGate decisions).
4. Resolve database composition (item 10).
5. Rename Home to Space in the API, with compatibility for persisted names.
6. Create the kits repo; extract `x0x-kit`, then `crdt-docs`, `groups-kit`, `device-kit`, `blobs-kit`, `assistants-kit`; publish; Vesta consumes the artifacts.
