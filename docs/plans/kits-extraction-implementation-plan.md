# Implementation plan: extracting the kits from `:shared`

Written 2026-10-15 so fresh sessions can do steps 3-6 of [shared-packages-extraction-plan.md](shared-packages-extraction-plan.md) ("Revised order") one work package (WP) at a time. **Status: proposal, nothing built.** Steps 1 and 2 are done ([ADR-035](../adr/ADR-035-document-type-registry.md), [document-registry-and-source-collapse-plan.md](document-registry-and-source-collapse-plan.md)).

Every WP is a commit-sized slice. After every WP Vesta builds and its tests are as green as the baseline (WP0). Nothing here changes user-visible behaviour, a wire format, a document id, a table or an invite link.

## Decisions already taken (do not reopen)

- **Separate repos.** The kits live in a sibling repo, `~/Development/VeritasLabs/vesta-kits`, published to **Maven Local only** for now. Vesta consumes artifacts, not project dependencies.
- **"Space" replaces "Home" in kit APIs.** Persisted names, wire ids, document ids, table names and invite-link formats stay byte-identical for existing installs.
- **Services only, no UI in kits.** Screens, view models, Compose resources and Compose-runtime helpers stay in Vesta.
- **Autonomi is out of scope.** **Kotlin explicit-API mode** is on in every kit.

## What the dependency analysis found

The extraction plan's package table was written from a file-name audit. This plan re-did it from the code (method: the import graph resolved to package, plus a token scan of every identifier against every top-level declaration, so that same-package references were not missed; the checks ran 2026-10-15 on `main` at `f4ebb2d`). The results change the module list, so read this section before the WPs.

### 1. Package names are not kit boundaries

`datasync/` (jvmAndroidMain) mixes four kits' code: the document plumbing (`SyncEnvelope`, `SyncEventRecorder`, `ChangesWaiting`), Home membership (`HomeRosters`, `HomeJoinService`, `HomeGroups`, `HomeHubs`, `HomeRegistry`, `ProfileRegistry`) and the engine (`DeviceDataSyncService`, `SyncActivityService`). `automerge/` mixes Vesta's seven features with the roster, Home registry and Profile registry documents. Splitting by package would cut straight through them. Appendix A is the file-level assignment this plan uses.

### 2. The real layering (verified, acyclic once seven small leaks are fixed)

```
x0x-kit        store-kit
   \   \       /   |
    \   docs-kit   |         blobs-kit (x0x + store only)
     \     |       |
      \  spaces-kit
       \   |
       sync-kit
```

`docs-kit` depends on `x0x-kit` and `store-kit`. `spaces-kit` on `docs-kit`, `x0x-kit`, `store-kit`. `sync-kit` on all of those. `blobs-kit` only on `x0x-kit` and `store-kit` (plus one Vesta leak, below). The extraction plan had `device-kit` below nothing and `crdt-docs` holding sync activity; in the code the **engine (`DeviceDataSyncService`) and `SyncActivityService` sit on top** of docs, spaces, profile and devices, because they read the roster, Profiles and the paired-device list. They cannot live in `crdt-docs`. This plan puts them, with device pairing, in **`sync-kit`** (the extraction plan's `device-kit`, renamed because it also holds the Home sync engine).

The `docs -> sync` edge (`SyncAcknowledgement` -> `DeviceDataSyncService`) is a KDoc reference only. The `x0x -> App` edge is the word "App" in a comment. Both are false positives. There are no real cycles.

### 3. The leaks that block each kit (all small; each is fixed in a WP)

| Kit | Leak | Evidence | Fixed by |
|---|---|---|---|
| all | **`VestaDatabase`**, one generated class, appears in 42 production files, and 56 test files open it through `openInMemoryDatabase` | `grep -rl VestaDatabase`, `grep -rl openInMemoryDatabase` | step 4 (WP8, WP9) |
| x0x | `X0xConnectivity` type lives in a Compose file (`X0xConnectivityIndicator.kt`); `X0xPeers.kt` and `X0xNetworkDiagnostics.kt` hold `@Composable remember…` wrappers (they import `compose.runtime`) | imports of those files | WP7 |
| x0x | `VestaAndroid` (the Android context holder) is declared in `EmbeddedX0xClient.android.kt` and used by `DatabaseDriverFactory.android.kt` and `Platform.android.kt` | `grep VestaAndroid` | WP2 |
| x0x | **JNI symbol names embed the Kotlin package**: `ffi/x0x-ffi/src/lib.rs` exports `Java_uk_veritaslabs_vesta_x0x_X0xBridge_nativeStart` etc. Moving `X0xBridge` to another package compiles and then fails at runtime with `UnsatisfiedLinkError` | `lib.rs:268-341` | WP13 |
| store | `DatabaseSafety` says "The Vesta database…"; `DatabaseDriverFactory` hard-codes `vesta.db` and `~/.vesta` | `DatabaseSafety.kt:21`, `*.jvm.kt:8-11` | WP3, WP9 |
| docs | `SyncModules` (`Pantry`, `Shopping`, `Cookbook`, …) in the recorder, and `SyncModuleName`, an enum of Vesta's features, in the Sync activity models | `SyncEventRecorder.kt:28-40`, `SyncActivityModels.kt:7` | WP6 |
| spaces | `HomeJoinService` and `HomeRegistry` call `storeEmptyPantry` / `storeEmptyShoppingList` (Vesta features) | `HomeJoinService.kt:355`, `HomeRegistry.kt:84` | WP4 |
| spaces | `MemberRole`, `MemberListItem`, `HomeShape`, `HomeDetails`, `HomeListItem` are declared in `ui/screens/*Screen.kt` and imported by 5 service files | `uileaks` grep, 15 edges | WP1 |
| spaces, sync | `InviteLifetime` (a Vesta constants object in `x0x/`) is used by `HomeGroups`, `HomeHubs`, `HomeJoinService`, `DeviceSyncService` | grep | WP3 |
| spaces, sync | `getDeviceName()` (root-package expect/actual) is called by `ProfileRepository` and `DeviceSyncService` | grep | WP2 |
| sync | `SyncedDevice` is declared in `SettingsSyncedDevicesScreen.kt`; the engine imports `VestaHomeSources`/`VestaHomeDocuments` | edges | WP1, WP5 |
| blobs | `BlobTransferService` and `DeviceBlobRuntime` take `CookbookSources`; `PhotoEncoder` imports `MediaKind` from `CookbookViewModel.kt` and `Placeholder` from the art file | edges | WP1, WP16 |
| assistants | The 5 generic files (329 lines) are small; the rest of `agent/` (7 files, 706 lines) is Vesta's wire protocol (`urn:vesta:*`, `AddPantryItem`, `PantryAdd`) | file read | WP25 (defer) |

### 4. Size

Kit-bound code is **93 files, about 9,100 lines of roughly 40,000** in `:shared` main source sets (x0x 15 files / 1,945 lines, store 9 / 239 plus 21 `.sq`, docs 17 / 1,243, spaces 21 / 2,625, sync 11 / 1,568, blobs 15 / 1,188, assistants 5 / 329). The other 174 files (30,763 lines; 97 are UI) stay in Vesta. The extraction is therefore not "most of the app"; it is the one quarter that is infrastructure, and the work is in the seams, not the volume.

### 5. Things the extraction plan did not know

- **Linux skips iOS.** `./gradlew :shared:compileKotlinIosSimulatorArm64` prints `SKIPPED` on this host. Nothing in iOS source can be verified unattended. Every WP that moves iOS files is marked "Mac gate owed" (Gate M, below).
- **Publishing from Linux omits the iOS klibs.** Vesta's iOS build on a Mac then cannot resolve the kits. Until publishing runs on a Mac too, iOS consumes nothing from the kits (WP27 records the decision).
- **Cross-module `internal`.** Tests in Vesta, and tests in a later kit, cannot see a kit's `internal` members. Code that one kit needs from another but apps must not use gets a `@RequiresOptIn` marker (`@KitsInternalApi`), not `internal` (convention below).
- **The whole schema is infrastructure.** All 21 `.sq` files describe infra; Vesta's domain data is in Automerge documents stored in `AutomergeDocument`. The only table Vesta's own code reads directly is `AppSetting`. This makes the database decision (step 4) much simpler than the extraction plan feared.
- **Two Rust/JNI/cinterop facts are tied to names**: the JNI symbols above and the iOS cinterop package `x0x.ffi` (`shared/native/x0xffi.def`).
- **`PendingMessage.payload` stores wire JSON** (`homejoin.left`, `homejoin.removed`) for retry. A message written by the old build must decode in the new one. Compatibility tests (WP10) cover stored payloads as well as live ones.

## Conventions for every WP

**Gates** (a WP names the ones it needs):

- **Gate V (Vesta):** `./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:verifyCommonMainVestaDatabaseMigration :desktopApp:compileKotlin :androidApp:compileDebugKotlin`, compared with the WP0 baseline. WP0 confirms the exact task names and writes them into this file. A failure outside the baseline's known failures is a regression. `DeviceDataSyncServiceTest` and the Compose UI acceptance tests are known to be flaky under load (ADR-035): rerun the one failing class alone before calling it a regression, and say so in the commit.
- **Gate K (kits repo):** in `vesta-kits`, `./gradlew build apiCheck publishToMavenLocal`, then Gate V in Vesta against the published artifacts.
- **Gate M (Mac, human):** `:shared:compileKotlinIosSimulatorArm64` and an Xcode build. Cannot run here.
- **Gate D (device, human):** launch the Android emulator and the desktop app; pair two instances; add an item. Needed where JNI or startup wiring changes.

**Rules** (from the project's standing conventions): every test method has `// Arrange` / `// Act` / `// Assert`; prefer `x?.let { }` to `if (x != null)`; every behaviour change has Given/When/Then acceptance tests in `docs/testing/acceptance-tests.md` (this work changes no behaviour, so the acceptance additions are the compatibility checks of WP10, plus the section "Kits extraction" added there); no `.sq` or `.sqm` edits except as WP9 and WP15 say; **commit only when the user asks**, ending messages with the attribution line the session gives.

**Execution protocol for an unattended session:** one WP per session; run the WP's gates; stop at the first red result that is not in the baseline and report, do not "fix forward" into the next WP; never run two import-rewriting WPs at once.

**Kit conventions** (applied in WP12, relied on by later WPs):

- Package root `uk.veritaslabs.kits.<kit>` (`x0x`, `store`, `docs`, `spaces`, `sync`, `blobs`). Maven group `uk.veritaslabs.kits`, artifacts `x0x-kit`, `store-kit`, `docs-kit`, `spaces-kit`, `sync-kit`, `blobs-kit`, plus `x0x-kit-testing` and `store-kit-testing`.
- `explicitApi()` on; `@RequiresOptIn(level = ERROR) annotation class KitsInternalApi` in `store-kit` (the root of the graph), applied to cross-kit plumbing that is `public` only because another kit needs it.
- Source sets: `commonMain`, plus the existing `jvmAndroidMain` convention (`androidMain.kotlin.srcDir("src/jvmAndroidMain/kotlin")` and the same for `jvmMain`, dependencies declared on both), because it already works here and a custom intermediate source set under AGP 9's `androidMultiplatformLibrary` plugin is an unverified change. `docs-kit` is JVM and Android only (Automerge has no iOS binding); the others keep their iOS targets.
- Versions: the kits repo's `libs.versions.toml` starts as a copy of Vesta's (Kotlin 2.4.20, AGP 9.1.1, coroutines 1.11.0, serialization 1.11.0, SQLDelight 2.4.0, androidx.sqlite 2.7.1, automerge 0.0.9). Drift is a known cost of separate repos; WP27 notes how to check it.
- Publishing: `maven-publish`, version in `gradle.properties` (`kitsVersion=0.1.0`), `publishToMavenLocal`. Vesta adds `mavenLocal()` to `dependencyResolutionManagement` filtered with `content { includeGroup("uk.veritaslabs.kits") }` so nothing else resolves from the local repository.
- Binary compatibility: the Kotlin binary-compatibility-validator plugin with JVM dumps and (if the pinned version supports it) klib dumps; baseline dump committed at the first release. Pre-1.0, a breaking change is allowed but must change the version and the dump in the same commit.
- Each kit's tests move with it; fixtures used across kits become `x0x-kit-testing` (`FakeX0xClient`, `FakeX0xNetwork`) and `store-kit-testing` (`openInMemoryDatabase`).
- Licences: kits are Apache-2.0 like Vesta. Vesta's `generateLicenses` task must not list the kits as third parties (WP27).

---

# Phase 0: baseline

## WP0. Baseline, the file-to-kit table, and the boundary test

**Goal.** Know what "green" is, and make the dependency analysis above an executable check that can only get stricter.

**Steps.**
- Run Gate V on `main`; record the test count, the failures and the skips, and the exact task names in a new "Baseline" section at the end of this file. (The registry plan measured 1,816 tests with one pre-existing failure, `CalendarEventsAcceptanceTest.aPrivateEventIsSavedOnThePrivateCalendarMarkedWithAPadlockAndHasNoParticipants`; re-measure, do not trust.) Record that iOS tasks are `SKIPPED`.
- Add `shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/kits/KitMembership.kt`: the table in Appendix A as code (file path prefix or name to kit), and `KitBoundaryTest.kt`: reads every `.kt` under `src/*Main`, takes its `package` and `import uk.veritaslabs.vesta.…` lines, resolves each import to a file, and checks the **allowed edges**: `x0x: {}`, `store: {}`, `docs: {x0x, store}`, `spaces: {docs, x0x, store}`, `sync: {docs, spaces, x0x, store}`, `blobs: {x0x, store}`, `assistants: {store}`; Vesta may import any kit; no kit may import Vesta.
- Today's violations are listed in the test as an `expectedViolations` set (the leaks in the table above). The test fails if a violation appears that is not in the set **and** if a listed one has gone (so the set shrinks with each WP and cannot grow). Same-package references are out of its reach until WPs 13, 17, 19, 21 and 23 repackage; the test notes that.
- Test method comments follow the AAA rule.

**Files.** New files only under `shared/src/jvmTest/.../kits/`; this plan file (Baseline section).

**Acceptance.** Gate V unchanged plus the new test passing. `git diff` touches only tests and this file.

**Order / parallel.** First. Nothing runs in parallel with it except WP8 and WP12 (they touch other repos or only docs). **Risk:** low. Unattended: yes.

**Commit.** `record the kit boundary as a test and the baseline it starts from`

---

# Phase A (step 3): the registry and engine take Vesta's types from outside

State after ADR-035: `HomeDocumentType`, `Scope` and `HomeDocument` are already `public`; `HomeDocumentRegistry`, `HomeDocumentStore`, `AutomergeDocumentCore`, the mailbox classes, `SyncAcknowledgement`, `ChangesWaiting` and `SyncableDocument` are `internal`; `DeviceDataSyncService` is public but takes `VestaHomeSources` (a Vesta class) and builds `VestaHomeDocuments` itself. `:shared` is one module, so `public` versus `internal` enforces nothing today. **What enforces the boundary inside `:shared` is the WP0 test; what makes the engine independent of Vesta is WPs 4-6 below.** The public surface of each kit is fixed in that kit's repackage WP (19, 21, 23), where the compiler can check it, not here.

## Decisions for step 3

**Roster hook.** The roster document type is registered by the engine, not by the app. The roster is membership machinery (`HomeRosters`, `afterReceive = project`, `afterSync = publishRosters`, `resyncsHome = true`); it needs the engine's `rosters`, `profiles` and `publishRosters`, all of which the engine owns. Vesta supplies **only its nine other types**. The engine builds `registry = HomeDocumentRegistry(listOf(rosterType) + appTypes)`, which keeps today's order (roster first for the mailbox; the engine's separate `SyncOrder` list, pinned by `DocumentIdsTest`, is untouched). `VestaHomeDocuments` shrinks to `VestaHomeSources.documentTypes(): List<HomeDocumentType>`. The `extraTypes` seam from the registry plan's WP9 becomes this single parameter.

**SeedGate: not extracted, and not a kit concern.** The "queue edits until the creator's document has arrived" logic exists because Pantry and the shopping list share one `items` map (ADR-008). That is Vesta's data model. The kit needs only a **lifecycle hook**, because three places currently call Vesta code when a Home appears:

```kotlin
interface HomeSeeding {              // becomes SpaceSeeding in the rename
    /** This device created the Home: seed the documents whose seed must exist before any other device hears of it. */
    suspend fun seedCreated(homeId: String)
    /** The Home arrived from another device (joined, or learned from the Home registry): store the unseeded placeholders. */
    suspend fun placeholdersForArrived(homeId: String)
}
```

`HomeRegistry.saved` calls `seedCreated` (today `pantrySources.forHome(home.id)`); `HomeRegistry.project` and `HomeJoinService` call `placeholdersForArrived` (today `storeEmptyPantry` + `storeEmptyShoppingList`). Vesta implements it over `VestaHomeSources`. `UnseededEditTest` (8 tests) stays the guard and does not move.

**What stays in Vesta, deliberately:** `VestaHomeSources`, the seven `*Sources`, `AgentCommandService`'s accessors, the `open…` lambdas in `main.kt`/`MainActivity.kt`.

## WP1. Domain types out of `ui.*`

**Goal.** Types the services need no longer live in screen files.

**Steps.**
- `MemberRole`, `MemberListItem` (from `MembersScreen.kt`) to `home/MemberRole.kt`; `HomeListItem` (from `HomePickerScreen.kt`) to `home/`; `SyncedDevice` (from `SettingsSyncedDevicesScreen.kt`) to `devicesync/`; `MediaKind` (from `CookbookViewModel.kt`) to `blobs/`.
- `HomeShape` is a Compose-resource enum and stays in `HomeEditScreen.kt`. `HomeRepository` stops using it: `HomeDetails` moves to `home/` with `shape: String?` (the stored value), `create`/`update` take `shape: String`, and `HomeEditViewModel` maps `HomeShape.storageValue` / `HomeShape.fromStorageValue` at its boundary. Behaviour is identical; the stored strings are the same.
- Fix imports (UI files in the same package used these names without an import; the compiler lists them).

**Files.** `home/*`, `devicesync/SyncedDeviceRepository.kt`, `blobs/PhotoEncoder.kt`, `ui/screens/{MembersScreen,HomePickerScreen,HomeEditScreen,SettingsSyncedDevicesScreen,CookbookViewModel,HomeEditViewModel}.kt` plus the UI files and tests that referenced them (about 25 files; the compiler finds them), `datasync/{HomeGroups,HomeHubs,HomeJoinService,HomeRosters}.kt` (import line only).

**Acceptance.** Gate V. `KitBoundaryTest`: the five `spaces -> vesta-ui` type edges and `sync -> SyncedDevice`, `blobs -> MediaKind` leave `expectedViolations`.

**Order / parallel.** After WP0. Touches import lines in the `datasync/` files that WP3 and WP4 also edit, so run those three **one after another**, not in worktrees. **Risk:** low-medium (many small import edits). Unattended: yes.

**Commit.** `move the member, device and media types the services use out of the screen files`

## WP2. Platform inputs are passed in

**Goal.** No kit-bound code reads a global it does not own.

**Steps.**
- `ProfileRepository` and `DeviceSyncService` take `deviceName: () -> String`; `DeviceDataSyncService` takes it and passes it on. Vesta passes `::getDeviceName`. Update the harnesses that construct them (`DeviceDataSyncServiceTest`, `ActivityDevice`, `DaemonHarness`, `DeviceSyncServiceTest`, `ProfileRepositoryTest`, `DevicePairingViewModelTest`, and the apps).
- Split `VestaAndroid`: the context holder that the embedded x0x client needs becomes `X0xAndroid` (in `EmbeddedX0xClient.android.kt`, still the same package; renamed with the package in WP13), installed by `VestaAndroid.install(context)`, which Vesta keeps for its own database path and `getAppInfo`. `MainActivity` still calls only `VestaAndroid.install(this)`.
- `Backoff` (used from `X0xEventHub.kt` by blobs) stays in x0x-kit unchanged.

**Files.** `profile/ProfileRepository.kt`, `devicesync/DeviceSyncService.kt`, `datasync/DeviceDataSyncService.kt`, `androidMain/.../x0x/EmbeddedX0xClient.android.kt`, `androidMain/.../Platform.android.kt`, `androidMain/.../db/DatabaseDriverFactory.android.kt`, `androidApp/.../MainActivity.kt`, `desktopApp/.../main.kt`, the harness tests listed.

**Acceptance.** Gate V; Gate D if the Android startup wiring changed. `KitBoundaryTest`: `store -> x0x (VestaAndroid)` leaves the set.

**Order / parallel.** After WP0. Disjoint production files from WP4 and WP7, so those two may run in worktrees alongside it; it shares `DeviceSyncService` with WP3, so WP3 comes after. **Risk:** low. Unattended: yes (Gate D owed to a human if `MainActivity` changed).

**Commit.** `pass the device name and the Android context to the services that need them`

## WP3. Brand and policy constants become configuration

**Goal.** Nothing a second app would need to choose is a literal inside a service.

**Steps.** Two small config classes with defaults equal to today's values, supplied by Vesta from one place (`VestaKitConfig.kt`):
- `HomeConfig` (becomes `SpacesConfig`): `inviteScheme = "vesta://invite"`, `groupName = "Vesta Home"`, `joinInviteLifetime = 1.hours`, `hubEnrolmentLifetime = 1.hours`, `hubCommandsRefresh = 30.minutes`, `mailboxStoreName = "mailbox"` (the store name is persisted in the x0x group, so the default is fixed and documented).
- `DeviceSyncConfig`: `pairingScheme = "vesta://pair"`, `groupName = "DeviceSync"`, `pairingInviteLifetime = 15.minutes`.
- `InviteLifetime` moves to Vesta (it is the policy; the kit receives numbers). `InviteLink.encode/decode` and `PairingLink.encode/decode` take the scheme from the config; the golden tests in WP10 pin the exact strings.
- `DatabaseDowngradeException` gets an `appName` parameter whose default message is unchanged for Vesta ("The Vesta database…").
- The group names are used to create groups; check (grep, then a test) that no code *finds* a group by name; if one does, the name is wire-significant and stays fixed in the config default with a comment.

**Files.** `homejoin/InviteLink.kt`, `devicepairing/PairingLink.kt`, `datasync/{HomeGroups,HomeHubs,HomeJoinService}.kt`, `devicesync/DeviceSyncService.kt`, `x0x/InviteLifetime.kt` (to `ui`-free Vesta package), `db/DatabaseSafety.kt`, harnesses.

**Acceptance.** Gate V. New tests: default config reproduces the exact scheme strings, group names and lifetimes. `x0x -> InviteLifetime` and the `spaces`/`sync -> InviteLifetime` edges leave the set.

**Order / parallel.** After WP1 and WP2 (shared files). **Risk:** low. Unattended: yes.

**Commit.** `make the invite and pairing schemes, group names and lifetimes configuration with today's values as defaults`

## WP4. The seeding hook

**Goal.** `HomeRegistry` and `HomeJoinService` no longer know Pantry.

**Steps.** Add `HomeSeeding` (above). `HomeRegistry(database, seeding)` replaces `HomeRegistry(database, pantrySources)`; `HomeJoinService` receives `seeding`. Vesta's `VestaHomeSeeding(sources: VestaHomeSources)` implements it with the calls that exist today, in the same order. `DeviceDataSyncService` takes `seeding: HomeSeeding` (default built from the `documents` it already receives, for this WP only; WP5 removes the default).

**Files.** `datasync/{HomeRegistry,HomeJoinService,DeviceDataSyncService}.kt`, new `automerge/VestaHomeSeeding.kt`, tests that build `HomeRegistry` (`HomeJoinRedeemTest`, `HomeJoinRequestsTest`, `DeviceDataSyncServiceTest`).

**Acceptance.** Gate V; `UnseededEditTest` and every Home-creation and join test unchanged. A new test: a recording `HomeSeeding` is called once on create and once on arrival, in that order relative to the registry write. `spaces -> vesta-features` (5 edges) leaves the set.

**Order / parallel.** After WP0; shares `HomeJoinService` with WP1 and WP3, so sequential with them. Disjoint from WP2 and WP7. **Risk:** medium: this is the Home creation and join path and the seeding order matters (the Pantry is seeded before any other device hears of the Home). Unattended: yes, with the ordering test.

**Commit.** `give Home creation and arrival a seeding hook instead of naming the Pantry`

## WP5. The engine takes the document types from outside

**Goal.** `DeviceDataSyncService` imports nothing from `automerge/` or `VestaHome*`.

**Steps.**
- Constructor: replace `documents: VestaHomeSources` and `extraTypes` with `documentTypes: List<HomeDocumentType>` and `seeding: HomeSeeding`. The engine builds the roster type (decision above) and `HomeDocumentRegistry(listOf(rosterType) + documentTypes)`.
- `VestaHomeDocuments` is deleted; `VestaHomeSources.documentTypes()` returns the nine types in the current order (pantry, shopping-list, cookbook, merchants, mealplan, calendar, tasks, private-calendar, private-tasks).
- Update `MainActivity`, `main.kt`, `AgentCommandService.homeSources`, and the harnesses (`DeviceDataSyncServiceTest`, `ActivityDevice`, `DaemonHarness`, `MailboxTest`, `MailboxCompactionTest`, `BlobTransferServiceTest`, `DocumentTypeRegistrationAcceptanceTest`, `VestaHomeDocumentsTest`).

**Files.** As listed. `DocumentIdsTest` (ids, order, scope) must pass unmodified; that is the proof that order and ids did not move.

**Acceptance.** Gate V; `DocumentTypeRegistrationAcceptanceTest` passes using the new parameter instead of `extraTypes`. `sync -> vesta-features` leaves the set. Gate D (pair two desktop instances, add a Pantry item, see it arrive) because this is the live sync path.

**Order / parallel.** After WP4. Sequential. **Risk:** medium-high (sync path, many call sites), but a mechanical signature change with the strongest test net in the repo. Unattended: yes, if Gate V is green before and after; do the Gate D smoke when the user is present.

**Commit.** `register the roster inside the sync engine and take Vesta's nine document types as a parameter`

## WP6. Sync module ids are opaque

**Goal.** The recorder and the Sync activity models do not list Vesta's features.

**Steps.** `SyncModules` keeps only the kit's own ids (`Members`, `Mailbox`); Vesta's seven move to `VestaSyncModules` (same strings, pinned by `DocumentIdsTest`). `SyncModuleName` (enum) becomes `@JvmInline value class SyncModule(val id: String)` with `Members` and `Mailbox` constants; `SyncEventItem.module` is a `SyncModule`. `SyncActivityService` stops mapping strings to enum values (the `when` at `SyncActivityService.kt:164-172`). The Sync activity screen maps `SyncModule.id` to its label and icon (Vesta, one `when` with an `else` that shows the id, so a module this version does not know is still shown).

**Files.** `datasync/{SyncEventRecorder,SyncActivityService}.kt`, `syncactivity/SyncActivityModels.kt`, `automerge/VestaHomeSources` (the nine types name `VestaSyncModules`), `ui/screens/SyncActivity*.kt`, `ui/components/SyncActivityComponents.kt`, tests (`SyncEventRecorderTest`, `SyncActivityServiceTest`, `SyncActivityAcceptanceTest`, `DocumentIdsTest`).

**Acceptance.** Gate V; `SyncEventQueriesTest` and the stored `SyncEvent.module` strings unchanged; the existing Sync activity acceptance test passes unmodified except for the type.

**Order / parallel.** After WP5. Sequential (UI files). **Risk:** low-medium. Unattended: yes.

**Commit.** `treat Sync activity modules as ids the app names, not a list of Vesta's features`

## WP7. Compose out of x0x and blobs

**Goal.** `x0x-kit` and `blobs-kit` have no Compose dependency.

**Steps.**
- Move `X0xConnectivity` (sealed type) to `x0x/X0xConnectivity.kt`. Move the `@Composable rememberX0xPeers`, `rememberX0xNetworkDiagnostics`, `rememberX0xConnectivity` wrappers to a Vesta UI file; the polling classes they wrap stay. `X0xConnectivityIndicator` and `X0xStatusCard` stay in Vesta.
- `Blobs.kt` has `staticCompositionLocalOf`; the `Blobs` interface and `PhotoAddResult` stay with the blobs code, the `LocalBlobs` CompositionLocal moves to Vesta UI.
- Check that no `x0x/` or `blobs/` (except `Placeholder.kt`, which stays in Vesta) file imports `androidx.compose`.

**Files.** `x0x/{X0xConnectivityIndicator,X0xNetworkDiagnostics,X0xPeers,X0xStatusCard}.kt`, `blobs/Blobs.kt`, UI files that call the `remember…` functions (they are in the same package today, so imports appear).

**Acceptance.** Gate V; a `KitBoundaryTest` rule: files assigned to `x0x` or `blobs` import no `androidx.compose.*`. `x0x -> vesta-ui` edges leave the set.

**Order / parallel.** After WP0. Disjoint production files from WP2 and WP4 (worktree-parallel is possible; UI import lines may conflict). **Risk:** low. Unattended: yes.

**Commit.** `keep Compose helpers and the x0x connectivity type apart so the client has no UI dependency`

---

# Phase B (step 4): database composition

## The decision

**Recommended: one kit, `store-kit`, owns the entire existing schema and its migration chain, and generates the single database class that every other kit and the app use. Vesta opens it and adds no tables.**

The facts that decide it (all verified in `shared/src/commonMain/sqldelight`):

- All 21 `.sq` files are infrastructure. Vesta's own data lives in Automerge documents inside `AutomergeDocument`. Vesta's code reads only `AppSetting` directly (`appSettingQueries`); every other `*Queries` is used by files that move into a kit.
- The schema is a single linear chain: 19 `.sqm` files, 20 checked-in snapshots, `verifyMigrations` on, `FullMigrationChainTest`. It is purely additive (`CREATE TABLE IF NOT EXISTS`).
- Tables reference `Home(id)` with `ON DELETE CASCADE`: `AutomergeDocument`, `SyncEvent`, `HomeMember`, `InviteCode`, `AssistantGrant`; `AssistantGrant` also references `Assistant`. Several others carry a bare `homeId`: `MailboxWrite`, `MailboxSequence`, `MailboxRead`, `MailboxSnapshot`, `BlobWant`, `JoinRequest`, `PendingJoin`, `PendingMessage`, `HomeGroup`, `AssistantActivity`. `Home.sq` queries join `HomeMember`.
- The query surface is small: 19 query sets used by 1-5 files each.

| Option | What it means | Verdict |
|---|---|---|
| **A. A database per kit** (`docs.db`, `spaces.db`, …) | Each kit owns its tables and migrations | Rejected. Loses the cascade (`AutomergeDocument` and `SyncEvent` are *docs-kit* tables that point at a *spaces-kit* table, an upward reference), loses the `Home`/`HomeMember` join, and needs a one-time **data migration from `vesta.db` into several files on every existing install**. A bad data migration destroys user data for good (docs/testing/database-migrations.md). |
| **B. SQLDelight multi-module** (`dependency(project(…))`) | Each kit has `.sq` files; the app's database composes them | Rejected for now. It is a same-build mechanism; across repos it would need the schema published and read back, which I could not confirm for SQLDelight 2.4.0. Migration numbering would also have to be shared across modules (one linear `user_version`), which is the same ownership problem in a harder form. |
| **C. Storage ports** (kits define interfaces, the app writes SQLDelight adapters) | Kits are database-agnostic | Rejected as the default. About 19 adapters in every consuming app, and the second app would copy Vesta's. Reasonable later if an app wants a non-SQLite store; nothing here prevents adding ports on top of `store-kit`. |
| **D. `store-kit` owns the whole schema** (recommended) | One module, one generated class, the existing chain moved verbatim | Chosen. No data migration, no change to any table, column, index, snapshot or `user_version`; cascades and joins keep working; `verifyMigrations` and `FullMigrationChainTest` move with the code and keep proving the chain. Cost: every database-using kit depends on `store-kit`, and a kit's tables are not independently versioned. That cost is already accepted: the decisions say the kits live in one repo and version together. |

**Per-app tables.** An app's own tables (Vesta has none) go in a second SQLDelight database in its own file, with no foreign keys into the kit schema. This is stated in the `store-kit` README so a second app does not extend the kit schema.

**Ownership inside `store-kit`.** The `.sq` files stay flat (generated names such as `homeQueries` stay; they are `internal` to the kits after extraction). A table-to-kit ownership table in the README records which kit's code may write which table, so the logical boundary is documented even though the physical one is shared.

**Things to verify, not assume** (WP15 does so first): that SQLDelight-generated code compiles under `explicitApi()` (if not, generated sources are excluded from the check, or the module opts out of explicit API for generated files only); that `generateAsync = true` and the `eygraber` androidx driver publish and resolve correctly from a KMP artifact; that `verifyMigrations` and `generateCommonMainKitsDatabaseSchema` work in the new module with the existing `databases/*.db` snapshots.

## WP8. Record the decision (ADR-036)

**Goal.** The decision above, with the options table, is an ADR before code depends on it.

**Steps.** Write `docs/adr/ADR-036-database-composition.md` (status Accepted once the user agrees), cross-linked from the extraction plan's finding 10. State the "per-app tables in their own file" rule and the verification list.

**Files.** `docs/adr/ADR-036-…md`, one line in the extraction plan.

**Acceptance.** User sign-off on option D (or a different choice, which changes WP9, WP15 and the kit list). Documents only.

**Order / parallel.** Any time; parallel with everything. **Risk:** none. Unattended: write it, then wait for the user's agreement before WP9.

**Commit.** `record the decision to move the whole schema into one store kit`

## WP9. Repackage the database layer inside `:shared`

**Goal.** The database has its final package and class name, still compiled in `:shared`, so extraction (WP15) is a file move.

**Steps.**
- Kotlin package `uk.veritaslabs.vesta.db` becomes `uk.veritaslabs.kits.store` (`DatabaseDriverFactory`, `DatabaseFiles`, `DatabaseOpenResult`, `DatabaseSafety`). The SQLDelight `packageName` and the database name change: `VestaDatabase` becomes `KitsDatabase`. **No `.sq`, `.sqm` or snapshot changes.** (SQLDelight's schema output directory is keyed by database name; if renaming moves the snapshot folder, keep the folder as it is and set `schemaOutputDirectory` to it.)
- `DatabaseDriverFactory` takes where the database lives from the app: `DatabaseLocation(appDirName = ".vesta", fileName = "vesta.db")` for JVM, `fileName` for Android and iOS. The defaults Vesta passes reproduce today's paths exactly (`~/.vesta/vesta.db`, `getDatabasePath("vesta.db")`, `<documents>/vesta.db`). A test asserts the three resolved paths.
- Mechanical rewrite of `VestaDatabase` to `KitsDatabase` and the db imports across 109 files; no logic edits.
- Add `@KitsInternalApi` here (it is used from WP13 on).

**Files.** `db/*` (all platform sets), `shared/build.gradle.kts` (sqldelight block), then every importer (about 109 files incl. tests), `docs/testing/database-migrations.md` (task names change).

**Acceptance.** Gate V including `verifyCommonMainKitsDatabaseMigration` (the renamed task) and `FullMigrationChainTest`; the three path assertions. A real-file check: open a copy of a database created by the previous build (from the WP10 fixture if it exists, otherwise create one by hand) and confirm it opens at version 20 with its data.

**Order / parallel.** After WP2 and WP3 (they edit `DatabaseDriverFactory.android.kt` and `DatabaseSafety.kt`). Sequential; touches ~109 files, so nothing runs beside it. **Risk:** medium (volume, and a renamed SQLDelight database). Unattended: yes for the mechanical rewrite; if the snapshot folder or `verifyMigrations` misbehaves after the rename, stop and report rather than moving snapshots.

**Commit.** `give the database its kit name and package and let the app say where it lives`

---

# Phase C (step 5): Home becomes Space in the API

## Inventory of every name that must not change

Everything in this table keeps its current spelling after the rename. The kit's *Kotlin* API says `Space`; the strings below stay. (Verified by grep of `.sq`, `.sqm` and main sources, 2026-10-15.)

| Name today | Where | Kind | Treatment |
|---|---|---|---|
| Tables `Home`, `HomeMember`, `HomeGroup` | `Home.sq`, `HomeMember.sq`, `HomeGroup.sq` | persisted | unchanged; generated names (`homeQueries`, rows `Home`, `HomeMember`) are `internal` in `store-kit` |
| Column `homeId` | `AutomergeDocument`, `SyncEvent`, `InviteCode`, `AssistantGrant`, `AssistantActivity` (nullable), `MailboxWrite`, `MailboxSequence`, `MailboxRead`, `MailboxSnapshot`, `BlobWant`, `JoinRequest`, `PendingJoin`, `PendingMessage`, `HomeGroup`, `HomeMember` | persisted | unchanged |
| Column `Home.shape`, `PendingJoin.homeName`, indexes `AutomergeDocument_homeId`, `SyncEvent_homeId_lastAt` | `.sq` | persisted | unchanged |
| Migrations `1.sqm`-`19.sqm`, snapshots `1.db`-`20.db`, `user_version` 20 | `sqldelight/` | persisted | immutable |
| Document ids `"$homeId:$feature"`; features `pantry`, `shopping-list`, `cookbook`, `merchants`, `mealplan`, `calendar`, `private-calendar`, `tasks`, `private-tasks`, `roster` | `AutomergeDocument.documentId`, `SyncEnvelope`, `MailboxWrite` | persisted + wire | unchanged; kit API `spaceDocumentId(spaceId, feature)` produces the same string |
| User document ids `user:homes`, `user:profile` | `UserDocument.documentId`, `SyncEnvelope` | persisted + wire | unchanged; constant renamed `SpaceRegistryDocumentId`, value `"user:homes"` with a comment saying why |
| Roster and registry document keys (`member:`, `device:`, `devicename:`, `hub`, `hub:`, `gone:`, `invite:`, `usedBy:`, `name`, `avatar`, `role`, `joinedAt`, `removed`, `group`, `createdBy`, `expiresAt`, `maxUses`, `revoked`; registry `name`, `shape`, `createdAt`, `deleted`) | `HomeRosterDocument.kt`, `HomeRegistryDocument.kt` | persisted + wire (inside Automerge) | unchanged (none contains "home") |
| Message kinds `homejoin.invite`, `.accepted`, `.left`, `.removed`, `.ack`, `.request`, `.denied`; `homegroup.invite`, `homegroup.invite.request`; discriminator `kind` | `HomeJoinMessage.kt`, `HomeGroupMessage.kt` | wire + persisted (`PendingMessage.payload`) | unchanged (`@SerialName` already explicit) |
| JSON fields `homeId`, `homeName`, `shape`, `createdAt`, `inviterMemberId`, `inviterName`, `secret`, `memberId`, `name`, `agentId`, `of`, `reason`, `groupId`, `invite` | the same messages, `BlobMessage` (`blob.want {homeId, hash}`) | wire | renamed Kotlin properties carry `@SerialName("homeId")` etc.; the engine's raw lookup `stringField(payload, "homeId")` keeps the literal |
| `PendingMessage.kind` values `left`, `removed` | `HomeJoinMessage.LeftKind/RemovedKind` | persisted | unchanged |
| Invite link `vesta://invite/<base64url("homeId:secret:expiresAt:agents:homeName")>` (positional, limit 5) | `InviteLink.kt` | wire (shared by link) | scheme from config; positional order and separator unchanged; Kotlin fields renamed `spaceId`, `spaceName` |
| Pairing link `vesta://pair/<token>` | `PairingLink.kt` | wire | unchanged (no Home in it) |
| x0x group names `Vesta Home`, `DeviceSync`; group store name `mailbox` | `HomeGroups.kt:22-23`, `DeviceSyncService.kt:27` | wire (visible to peers; `mailbox` is the store a Home's members replicate) | config defaults (WP3) |
| Sync activity module ids `pantry`, `shopping`, `cookbook`, `mealplan`, `calendar`, `tasks`, `merchants`, `members`, `mailbox` and event kinds / detail codes | `SyncEvent.module`, `.kind`, `.detail` | persisted | unchanged (the Vesta ones move to `VestaSyncModules`, WP6) |
| Agent protocol `urn:vesta:homes`, `urn:vesta:home:<id>`, `urn:vesta:document:<homeId>…`, `@context` JSON-LD, reply strings such as "not permitted for this home", "unknown home"; capability names `ListHomes`, `PantryAdd` stored in `AssistantGrant.capability`; policy names `Ask`/`Auto`; `skills/vesta` | `AgentCommandParser.kt`, `AgentReplies.kt`, `PantryCommandHandler.kt`, `Assistants.kt`, the skill | wire to external agents + persisted | Vesta-owned, **not renamed**; never moves to a kit |
| `AppSetting` keys | `AppSetting.sq` | persisted | Vesta-owned, unchanged |
| `vesta.db`, `~/.vesta/`, `.vesta/blobs`, `X0X_DATA_DIR`, `VESTA_AGENT_ALLOWLIST`, iCal `@vesta` / `PRODID` | various | persisted / env | Vesta-owned, unchanged |
| JNI symbols `Java_uk_veritaslabs_vesta_x0x_X0xBridge_*` | `ffi/x0x-ffi/src/lib.rs` | binary | not a Home name but the same hazard; changed together with the package in WP13 and guarded by a test |
| Vesta UI strings and Compose resource keys that say "Home" | `composeResources`, screens | UI | unchanged (Vesta may keep saying Home) |

`shape` is a Vesta notion (Family, Flatshare, …). In the kit it is an opaque `kind: String` that the app interprets; the stored column and JSON field stay `shape`.

## Renaming approach

- **Kit API names** (classes, parameters, properties): `Home*` becomes `Space*`, `homeId` becomes `spaceId`, `homeName` becomes `spaceName`, `shape` becomes `kind`. Examples: `HomeDocumentType` -> `SpaceDocumentType`, `HomeDocumentRegistry` -> `SpaceDocumentRegistry`, `HomeDocumentStore` -> `SpaceDocumentStore`, `HomeSeeding` -> `SpaceSeeding`, `HomeRepository` -> `SpaceRepository`, `HomeRosters` -> `SpaceRosters`, `HomeRegistry` -> `SpaceRegistry`, `HomeJoinService` -> `SpaceJoinService`, `HomeJoinMessage` -> `SpaceJoinMessage`, `HomeGroups` -> `SpaceGroups`, `HomeHubs` -> `SpaceHubs`, `HomeHub` -> `SpaceHub`, `InviteLink.homeId` -> `spaceId`, `HomeChangeListener` -> `SpaceChangeListener`.
- **Vesta's own code keeps "home"** (`homeId` in screens and view models, `PantrySources.forHome`, `AgentCommandService`). The edge is a thin adapter where a Vesta class calls a kit class with a `homeId` value. About 105 files and 2,000 `homeId` occurrences exist today, but only the ones in kit-bound files are renamed (WPs 19, 21, 23, 17).
- **Just in time, per kit, before its extraction** (WPs 17, 19, 21, 23), not as one repository-wide rename first. Reasons: each diff stays reviewable; x0x-kit has no Home vocabulary and can go first without waiting; later kits' names stay changeable while earlier kits are being published; every rename stays before that kit's first publication, which is the real constraint ("before any package is published"). This is a change of order inside step 5 only; steps 3, 4, 5 still all precede the publication of any Home-bearing kit.
- **Tool:** a checked-in script (`scripts/rename-space.sh`, a word-boundary `sed` over an explicit file list) so the rename is reproducible and reviewable as "script plus its output". Compiling tells what the script missed. `@SerialName` additions are hand-written.

## WP10. Compatibility tests that outlive the rename

**Goal.** Capture what must not change *before* anything is renamed, so every later WP is checked against bytes produced by the old code.

**Steps.** In `shared/src/jvmTest/.../compat/`:
- **Golden wire strings.** For each `HomeJoinMessage`, `HomeGroupMessage`, `DeviceSyncMessage`, `BlobMessage` with fixed inputs, the exact JSON text (literal in the test, captured from the current encoder), plus `decode(literal)` equals the object. For `InviteLink` and `PairingLink` the exact link strings and decode of the literal. For `SyncEnvelope` the exact bytes of one envelope.
- **Golden ids.** Extend `DocumentIdsTest` (already pins the ten ids) with `user:homes`, `user:profile`, the engine order, the mailbox order.
- **Existing-install fixture.** A one-off test writes `shared/src/jvmTest/resources/compat/vesta-v20.db`: a version-20 database created by the **current** schema with at least one row in every table, one Home with all ten documents saved by the current document functions, one roster with members, a device, a hub, an invite and a tombstone, a `PendingMessage` whose payload is a `homejoin.left` JSON, a `MailboxWrite`. Commit the file. The test `ExistingInstallStillOpensTest` opens it with the code under test through the production open path, asserts the version, loads every document through the registry, decodes the pending payload, and reads the roster. After every later WP this test must pass without the fixture being regenerated (a CI-style check: `git diff --exit-code` on the fixture after the run).
- **Schema guard.** `verifyMigrations` already fails on any table or column change; add a test asserting the list of table names and column names of `sqlite_master` against a literal, so a renamed column fails with a readable message.

**Files.** New under `jvmTest/.../compat/` and one resource file.

**Acceptance.** All new tests green on unchanged production code (`git diff` touches no production file).

**Order / parallel.** After WP0, and before WP17. May run beside WP8, WP12. **Risk:** low. The fixture must be produced by code that has not yet been renamed, so make it before WP17 (the schema is identical after WP9, so the bytes would not differ, but the golden strings come from the old encoders). Unattended: yes.

**Commit.** `pin the wire strings, ids and an old database as fixtures before renaming anything`

## WP11. The vocabulary ADR (ADR-037)

**Goal.** One page that says "Space", lists the table above as the compatibility contract, and states that Vesta's UI may keep saying Home.

**Files.** `docs/adr/ADR-037-space-vocabulary.md`; update the extraction plan's decisions line.

**Order / parallel.** Any time before WP17; parallel with everything. **Risk:** none. Unattended: yes.

**Commit.** `record the Space vocabulary and the names that must not change`

---

# Phase D (step 6): the kits repo and the extractions

## Module list and extraction order

| # | Module | Platforms | Depends on | Moves from `:shared` | Size |
|---|---|---|---|---|---|
| 1 | `x0x-kit` (+ `x0x-kit-testing`) | jvm, android, ios | none | `x0x/` minus 3 UI-ish files; `FakeX0xClient`, `FakeX0xNetwork`; (later) the Rust crate | 15 files, 1,945 lines |
| 2 | `store-kit` (+ `store-kit-testing`) | jvm, android, ios | none | `db/`, the 21 `.sq`, 19 `.sqm`, 20 snapshots, `DatabaseSafety` | 9 files, 239 lines + SQL |
| 3 | `blobs-kit` | jvm, android, ios | x0x, store | `blobs/` minus `Placeholder.kt` | 15 files, 1,188 lines |
| 4 | `docs-kit` | jvm, android | x0x, store | `docs/`, `mailbox/`, 5 files of `datasync/` | 17 files, 1,243 lines |
| 5 | `spaces-kit` | jvm, android, ios | docs, x0x, store | roster/registry docs, `HomeRosters/Registry/JoinService/Groups/Hubs`, `ProfileRegistry`, `home/`, `homejoin/`, `homegroup/`, `profile/` services | 21 files, 2,625 lines |
| 6 | `sync-kit` | jvm, android, ios | docs, spaces, x0x, store | `DeviceDataSyncService`, `SyncActivityService`, `devicesync/`, `devicepairing/PairingLink`, `syncactivity/` models | 11 files, 1,568 lines |
| 7 | `assistants-kit` | **deferred** | store | 5 files (329 lines) | see WP25 |

**Why this order.** x0x and store depend on nothing and every other kit needs them, so they come first and prove the publish-and-consume pipeline on the lowest-risk code. `blobs-kit` is third because it needs only those two and one small seam (WP16); doing it before `docs-kit` gives a second, cheap rehearsal of the pipeline before the three large kits. `docs-kit`, `spaces-kit`, `sync-kit` follow the dependency order and are the risky ones. The `app-shell-kit` of the extraction plan does not exist: `DatabaseSafety` goes to `store-kit`; `AppSettingsRepository` stays in Vesta (it imports `CalendarSettings`, `WeekStart` and `ThemeMode`, which are Vesta types); `Platform`, `getAppInfo` stay in Vesta.

**Pattern for each kit (two WPs).** *Repackage* (in `:shared`, Gate V): rename for Space where relevant, move to `uk.veritaslabs.kits.<kit>`, split `public` / `@KitsInternalApi` / `internal`, make `KitBoundaryTest` stricter. *Extract* (kits repo plus Vesta, Gates K and V): move the files and tests, add the module, publish, replace the sources in `:shared` with the dependency. The split is deliberate: the first half is fully verified by Vesta's own 1,800 tests with the code still in one module; the second half is then only file movement and build files.

## WP12. The kits repo and a published smoke module

**Goal.** Prove, before any code moves, that a KMP library built here publishes to Maven Local and that Vesta (including its Android and desktop modules) resolves and runs against it.

**Steps.**
- `~/Development/VeritasLabs/vesta-kits`: `git init`, copy the Gradle wrapper and `gradle-daemon-jvm.properties`, `libs.versions.toml` (versions as in the conventions), `settings.gradle.kts` with `includeBuild("build-logic")`, `build-logic/` with convention plugins: `kits.kmp-library` (targets jvm, android via `com.android.kotlin.multiplatform.library` with namespace `uk.veritaslabs.kits.<module>`, `iosArm64`/`iosSimulatorArm64`, the `jvmAndroidMain` srcDir convention, `explicitApi()`, JVM 11, test dependencies as in `shared/build.gradle.kts`), `kits.publishing` (`maven-publish`, group, version from `gradle.properties`, POM with Apache-2.0), `kits.api-check`.
- One module `smoke-kit` with one public function and one test. `./gradlew build apiDump publishToMavenLocal`.
- In Vesta, a throwaway branch (not committed): add `mavenLocal()` filtered by group, depend on `smoke-kit` from `:shared` `commonMain`, call the function from `jvmMain` and `androidMain`, run `:desktopApp:compileKotlin :androidApp:compileDebugKotlin :shared:jvmTest`. Record in the kits README what resolved and what did not (the `-android` variant selection under AGP 9.1.1; `-jvm`; the iOS variants absent on Linux).
- Add `README.md`, `LICENSE`, `NOTICE` copied from Vesta, `docs/` with a copy of the "Conventions" section of this plan.
- Delete `smoke-kit` once WP14 publishes a real module.

**Files.** All new, in the other repo. No Vesta file is committed.

**Acceptance.** Gate K for `smoke-kit`; the throwaway Vesta branch compiles and its tests run. If the Android variant does not resolve, stop: the finding changes the build conventions (for example publishing with the older `com.android.library` KMP setup) and must be settled before WP13.

**Order / parallel.** Can start at once and run beside every `:shared` WP (it shares no file with Vesta). **Risk:** **high for unattended execution** because the AGP 9.1.1 KMP-library publishing and consumption path is the least-known part of this plan; every statement about it here is unverified. Unattended: only the skeleton; the consume-side check should be reviewed by a human before WP14 starts.

**Commit (kits repo).** `add the kits build conventions and a smoke module that publishes to Maven Local`

## WP13. x0x: repackage inside `:shared`, and the JNI symbols

**Goal.** `x0x` is `uk.veritaslabs.kits.x0x` and the Rust crate agrees, with `:shared` still one module.

**Steps.**
- Move the 12 kit files (Appendix A) from `uk.veritaslabs.vesta.x0x` to `uk.veritaslabs.kits.x0x` (`commonMain`, `androidMain`, `jvmMain`, `iosMain`). The three Vesta files (`X0xStatusCard`, `X0xConnectivityIndicator`, `InviteLifetime`, plus the WP7 `remember…` wrappers) stay in `uk.veritaslabs.vesta.x0x`/`ui`.
- Make the cross-package public surface `public` with KDoc; everything else `internal`. `X0xAndroid` (WP2) is public. Mark the pieces Vesta must not use `@KitsInternalApi` (`httpRequest`, `openSseStream` expect/actual).
- **JNI.** Rename the five exports in `ffi/x0x-ffi/src/lib.rs` from `Java_uk_veritaslabs_vesta_x0x_X0xBridge_*` to `Java_uk_veritaslabs_kits_x0x_X0xBridge_*`. Add a unit test (`X0xBridgeSymbolsTest`, jvmTest) that reads `ffi/x0x-ffi/src/lib.rs`, lists the `external fun` names of `X0xBridge` and the `Java_…` exports, and asserts that for the bridge's current package every `external fun` has a matching export and no stale `vesta_x0x` export remains. It runs on Linux and catches the runtime-only failure at build time. The iOS cinterop package `x0x.ffi` and `x0xffi.def` do not change.
- Rewrite imports in about 75 files (production and tests). `LogTag = "VestaX0x"` becomes `"KitsX0x"` (log only).
- `StandaloneX0xClient` reads `X0X_DATA_DIR` and the standard x0x data directories; nothing there names Vesta.

**Files.** `x0x/*` in the four platform source sets; `ffi/x0x-ffi/src/lib.rs`; 75 importers; the new test.

**Acceptance.** Gate V; `X0xBridgeSymbolsTest`; `KitBoundaryTest` stricter: `x0x` files import nothing outside `x0x`. **Gate D owed** (a built `.so` loading under the renamed symbols can only be proven on a device or emulator; `cargo build` for the Android target plus `nm -D` on the output would also work if the NDK is installed, and is worth doing if it is). **Gate M owed** for the iOS source set.

**Order / parallel.** After WP2, WP7 (and WP0). Rewrites imports in 75 files, so sequential with everything else that touches `:shared`. **Risk:** medium-high: the JNI rename is invisible to the compiler. Unattended: yes for the Kotlin move and the symbol test; the by-hand Android launch is a human step before WP14 is called done.

**Commit.** `move the x0x client to the kits package and rename the JNI exports with it`

## WP14. Extract `x0x-kit` and `x0x-kit-testing`

**Goal.** First real kit published; Vesta consumes it.

**Steps.**
- In `vesta-kits`: module `x0x-kit` (the 12 files, their tests: `AbstractX0xClientFilesTest`, `StorePathTest`, `X0xConnectivityTest`, `X0xEventHubTest`, `X0xHealthTest`, `X0xNetworkMonitorTest`, `X0xPeersTest`, `X0xStuckDetectorTest`); module `x0x-kit-testing` (`FakeX0xClient`, `FakeX0xNetwork`, `FakeGroupStore` if it is separate, and the helper types they use). `explicitApi` errors fixed; `apiDump` baseline committed; `0.1.0` published to Maven Local.
- In Vesta: `libs.versions.toml` gets `kits = "0.1.0"` and library entries; `:shared` depends on `x0x-kit` (`api` for `commonMain`, since Vesta's own code uses the types) and `x0x-kit-testing` in `commonTest`/`jvmTest`; delete the moved files; `settings.gradle.kts` gets `mavenLocal()` filtered by group.
- The Rust crate and `scripts/build-*-ffi.sh` **stay in Vesta** in this WP (decision: native packaging is WP26). Vesta's `androidApp` still builds and stages `libx0x_ffi.so`; the kit contains only the Kotlin JNI declarations.
- Tests that moved must keep their AAA comments.

**Files.** `vesta-kits/x0x-kit/**`, `vesta-kits/x0x-kit-testing/**`, `shared/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts`, deletions under `shared/src/*/x0x/`, `commonTest/x0x/`, `jvmTest/devicesync/FakeX0xNetwork*.kt`.

**Acceptance.** Gate K, then Gate V. The Vesta test count may only change by the tests that moved (record the arithmetic: Vesta's count down by N, the kits' count up by N). Gate D (the embedded daemon starts on the emulator and the desktop app finds `x0xd`).

**Order / parallel.** After WP12 (human-reviewed) and WP13. Sequential. **Risk:** medium (the first time Vesta builds against a published artifact). Unattended: yes, if WP12's consume check was clean.

**Commit (kits).** `add x0x-kit and x0x-kit-testing`. **Commit (Vesta).** `use the published x0x kit instead of the in-tree client`

**As built (unattended run, 2026-10-16).** `x0x-kit` and `x0x-kit-testing` (package `uk.veritaslabs.kits.x0x.testing`; the fakes, `FakeGroupStoreTest` and `FakeX0xNetworkFilesTest` moved with them) published as `0.1.0` to Maven Local; `smoke-kit` and `consume-check` deleted. Deviations: (1) `X0xBridge` stays in `uk.veritaslabs.vesta.x0x` inside the kit (see WP13); Vesta's `X0xBridgeSymbolsTest` (androidHostTest) inspects the kit's compiled class against `lib.rs`. (2) The iOS cinterop (`x0xffi.def`, header copy) moved into the kit and out of `:shared`; because Kotlin/Native cannot run cinterop on Linux, the two kit modules declare iOS targets only on a macOS host (`kits.iosNeedsMacModules`), so the Linux-published artifacts have no iOS variant, the klib API dump is macOS-only, and Vesta's iOS source sets cannot resolve the kit until it is published from a Mac. **Gate M owes:** publish both kits from a Mac, run `apiDump` there and commit the klib dumps, build Vesta's iOS framework. (3) `KitBoundaryTest` keeps `Kit.X0x` as an extracted kit (`KitMembership.extracted`): no source of it may remain in `:shared`.
## WP15. Extract `store-kit` and `store-kit-testing`

**Goal.** The schema, the migration chain and the open path are published; Vesta opens `KitsDatabase` from the artifact.

**Steps.**
- In `vesta-kits/store-kit`: the 21 `.sq`, 19 `.sqm`, the `databases/*.db` snapshots (byte-identical copies; checksum them before and after), `db/*` code, `generateAsync = true`, `verifyMigrations`, `linkSqlite = false`, the two driver dependencies. Tests: `FullMigrationChainTest`, `AssistantMigrationTest`, `DatabaseSafetyTest`, `ProfileTest`, `SyncEventQueriesTest`, `AgentCommandLogTest`, `VestaDatabaseTest` (renamed), `TestDatabase`. `store-kit-testing` publishes `openInMemoryDatabase()`.
- First, as a spike inside this WP and before moving the bulk: confirm the three "verify, do not assume" items from the Phase B decision. If generated code breaks `explicitApi()`, apply the narrowest opt-out and record it.
- Vesta: `:shared` depends on `store-kit`; delete `commonMain/sqldelight`, the `sqldelight {}` block and the SQLDelight plugin from `:shared` (Vesta no longer has its own schema; `AppSetting` queries come from the kit's `KitsDatabase`). `docs/testing/database-migrations.md` moves to the kits repo and Vesta's copy becomes a pointer.
- The Vesta `generateLicenses` report must still list SQLDelight runtime (it comes through the kit now).

**Files.** `vesta-kits/store-kit/**`, `store-kit-testing/**`, `shared/build.gradle.kts`, deletions of `shared/src/commonMain/sqldelight/**` and `shared/src/commonMain/kotlin/.../db/**` (already `kits.store` after WP9), Vesta test deletions of the moved tests.

**Acceptance.** Gate K (`verifyMigrations` passes in the kit), Gate V, and `ExistingInstallStillOpensTest` (WP10) against the published artifact: the old database file opens at version 20 with its data. Compare the SHA-256 of every snapshot before and after the move.

**Order / parallel.** After WP9, WP10, WP14. Sequential. **Risk:** **high.** The migration chain protects user data for good, and this is the first time it is built outside the repo it was written in. Unattended: do the spike and the copy, stop before deleting Vesta's schema, and have the user run the old-database check by hand against a copy of a real `vesta.db` before the deletion is committed.

**Commit (kits).** `add store-kit with the whole schema and its migration chain`. **Commit (Vesta).** `open the database from the store kit`

**As built (unattended run, 2026-10-16).** `store-kit` and `store-kit-testing` (`openInMemoryDatabase()` in `uk.veritaslabs.kits.store.testing`) published as `0.1.0`; kits commits `09d06da` and the doc-pointer follow-up. Spike results: SQLDelight 2.4.0 generates `KitsDatabase` (`generateAsync`, `linkSqlite = false`, eygraber driver) in a kit module under `explicitApi()` with **no opt-out** (generated sources are not checked); `verifyCommonMainKitsDatabaseMigration` passes in the kit; `.sq`, `.sqm` and the 20 snapshots are byte-identical (SHA-256 compared before and after); the iOS targets of store-kit compile on Linux (no cinterop), so it publishes iOS klibs (never run; the klib API dump is Mac-only, Gate M). Deviations: (1) `AssistantMigrationTest` and `AgentCommandLogTest` stay in Vesta because they use `AssistantRepository` and `DatabaseCommandLog`; `DesktopDatabasePathTest` stays (it uses Vesta's location). (2) The kit also got its own `OldDatabaseStillOpensTest` (a copy of `vesta-v20.db`) and `SchemaGuardTest`. (3) The `.sq` files keep the directory `uk/veritaslabs/vesta/db/`, so generated queries and row types stay in package `uk.veritaslabs.vesta.db`; only `KitsDatabase` is `uk.veritaslabs.kits.store`. (4) `DatabaseSafety` is `public` under `@KitsInternalApi` (`@RequiresOptIn(ERROR)`, defined in store-kit) for Vesta's compat tests. (5) **Owed before any release: the manual check of a real `vesta.db` copy.** The plan's human check was replaced, at the lead's decision, by the automated fixture and migration-chain tests; everything is git-revertable and nothing ships. Gate M owes: publish from a Mac, commit the klib dumps, build the iOS framework (the `linkSqlite = false` setting now lives in the kit, not in the framework link).

## WP16. Blobs seam: `BlobReferenceSource`

**Goal.** `BlobTransferService` no longer takes `CookbookSources`.

**Steps.** Define in `blobs/`:

```kotlin
interface BlobReferenceSource {
    /** Every blob this Home's documents refer to, so a missing one can be wanted from peers and a held one kept. */
    suspend fun references(homeId: String): Set<BlobReference>
}
```

Vesta implements `CookbookBlobReferences(cookbooks: CookbookSources)` over the existing `blobReferences(...)` function in `CookbookDocument.kt`, and passes a list of sources to `BlobTransferService` and `DeviceBlobRuntime`. The list of Home ids comes from a `homes: suspend () -> List<String>` parameter (today `database.homeQueries.selectAll()` inside the service), so `blobs-kit` does not need `Home` knowledge either. `PhotoEncoder` uses `MediaKind` from `blobs/` (WP1) and the `Placeholder` it imports is passed in as a lambda or removed from the interface (check what it is used for; `Placeholder` is Vesta art and stays).

**Files.** `blobs/{BlobTransferService,DeviceBlobRuntime,PhotoEncoder}.kt`, new `automerge/CookbookBlobReferences.kt`, apps' `blobRuntime` lambdas, `BlobTransferServiceTest`, `TransfersJanitorTest`.

**Acceptance.** Gate V; the `BlobTransferServiceTest` and daemon integration tests unchanged in behaviour. `blobs -> vesta-features` and `blobs -> vesta-ui` leave the set.

**Order / parallel.** After WP1, WP7. Disjoint production files from WP19-WP24 (blobs only), so it may run in a worktree beside them; the apps' `main.kt`/`MainActivity` lines are the only overlap. **Risk:** low-medium. Unattended: yes.

**Commit.** `let the blob service find references through an interface instead of the cookbook`

## WP17. Blobs: rename and repackage

**Goal.** `blobs` is `uk.veritaslabs.kits.blobs` with Space vocabulary.

**Steps.** `homeId` -> `spaceId` in the blob code, with `@SerialName("homeId")` on `BlobMessage.Want.spaceId`; `BlobWant.homeId` stays in SQL. Package move; public / `@KitsInternalApi` / `internal` split (the surface Vesta uses: `BlobStore`, `BlobRepository`, `BlobTransferService`, `DeviceBlobRuntime`, `PhotoEncoder` and platform actuals, `PhotoFetcher`, `PhotoSources`, `BlobIngest`, `RepositoryBlobs`, `TransfersJanitor`, `Blobs`/`BlobRuntime`). Import rewrite in about 52 files.

**Acceptance.** Gate V; WP10 golden test for `blob.want`; `KitBoundaryTest`: `blobs` imports only `x0x`, `store`.

**Order / parallel.** After WP10, WP11, WP16, WP9. Sequential (52 importers). **Risk:** low-medium. Unattended: yes.

**Commit.** `rename the blob code to the kits package and Space vocabulary`

## WP18. Extract `blobs-kit`

**Steps / files.** As WP14: module, tests (`BlobStoreTest`, `BlobRepositoryTest`, `BlobIngestTest`, `BlobTransferServiceTest`, `TransfersJanitorTest`, `HttpPhotoFetcherTest`, `SkiaPhotoEncoderTest`, `PhotoLimitsTest`, `FakeBlobs`), publish, replace.

**Acceptance.** Gates K and V. **Order.** After WP15, WP17. **Risk:** low-medium (rehearsal of the pipeline on a kit that depends on two published kits). Unattended: yes. Gate M owed for the iOS photo encoder source.

**Commit (kits).** `add blobs-kit`. **Commit (Vesta).** `use the published blobs kit`

## WP19. docs-kit: rename and repackage

**Goal.** The document plumbing is `uk.veritaslabs.kits.docs`, in Space words, with the public surface chosen.

**Steps.**
- Files (Appendix A): `docs/*`, `mailbox/*`, and from `datasync/`: `SyncEnvelope`, `SyncableDocument`, `SyncAcknowledgement`, `SyncEventRecorder`, `ChangesWaiting`.
- Rename: `HomeDocument(Type|Registry|Store)` -> `SpaceDocument…`, `homeDocumentId` -> `spaceDocumentId`, `HomeMailboxDocuments` -> `SpaceMailboxDocuments`, `homeId` -> `spaceId` throughout. `AutomergeDocumentCore`, `DocumentHeads` unchanged.
- Public surface: `SpaceDocument`, `SpaceDocumentType`, `Scope`, `SpaceDocumentRegistry`, `SpaceDocumentStore`, `AutomergeDocumentCore`, `SyncableDocument`, `SyncEnvelope`, `SyncEventRecorder` (+ `SyncModules`' two kit ids and `SyncEventKinds`), `MailboxWriter`, `MailboxReader`, `SpaceMailboxDocuments`, `ChangesWaiting` and `SyncTargets`, `SyncAcknowledgement`, `StoredHeads`, `changesSince`/`encodeHeads`. Of these, `ChangesWaiting`, `SyncTargets`, `SyncAcknowledgement`, `MailboxReader/Writer` and the heads helpers are plumbing for `sync-kit` and `spaces-kit`: `@KitsInternalApi`. The app-facing ones are the document and registry types and `AutomergeDocumentCore`/`SpaceDocumentStore` (what Vesta's seven `*Sources` use).
- Rewrite imports (`docs` 32 files, `mailbox` 13, `datasync` 41 overlap). The `datasync` package is split: the five docs files move; the rest of `datasync` stays until WP21/WP23.
- Fix same-package references the compiler reveals (files that were in `datasync` and used `SyncEnvelope` without an import now need one).

**Acceptance.** Gate V; WP10 compat tests; `DocumentIdsTest`; `KitBoundaryTest`: `docs` imports only `x0x`, `store`; the `docs -> sync` KDoc reference is rewritten as a plain-text mention.

**Order / parallel.** After WP5, WP6, WP9, WP10, WP11. Sequential. **Risk:** medium (about 80 files touched, the sync path). Unattended: yes if WP10's net is in place.

**Commit.** `rename the document plumbing to the kits package and Space vocabulary`

**As built (unattended run, 2026-10-16).** Vesta `7bdf5ff`. `docs/`, `mailbox/` and the five `datasync` files are ONE package, `uk.veritaslabs.kits.docs`; the mailbox `StoredHeads` wrappers (duplicates of `DocumentHeads`) are deleted, so Docs is 16 files, not 17. `SpaceDocumentStore.forHome` is `forSpace` (Vesta's `*Sources.forHome` stay). Public plain: document, type, registry, store, `AutomergeDocumentCore`, `SyncEnvelope`, `SyncEventRecorder`, `SyncEventKinds`, `SyncModules`; `@KitsInternalApi` public: mailbox reader/writer/entry/snapshot/documents, heads helpers, `SyncableDocument`, `SyncAcknowledgement`, `ChangesWaiting`, `SyncTargets`. Vesta files that use them carry `@file:OptIn(KitsInternalApi::class)` (visible, removable when spaces/sync extract) instead of a module-wide opt-in. Tests that touch Vesta's documents (`SpaceDocumentRegistryTest`, `MailboxTest`, `MailboxCompactionTest`, `ChangesWaitingTest`, `SyncEventRecorderTest`) went back to Vesta packages.

## WP20. Extract `docs-kit`

**Files / steps.** As WP14. Tests that move: `jvmTest/docs/*` (3), `jvmTest/mailbox/*` (3), `SyncEnvelopeTest`, `SyncEventRecorderTest`, `ChangesWaitingTest`, and the document-core tests. `DocumentTypeRegistrationAcceptanceTest` and `DocumentIdsTest` go to **Vesta** (they register Vesta's types; they prove the extension seam from the consumer's side, which is the point). JVM and Android only; Gate M does not apply.

**Acceptance.** Gates K and V. **Order.** After WP14, WP15, WP19. **Risk:** medium. Unattended: yes. Watch: tests that use `internal` members of the docs code from outside the kit fail to compile; move them with the code or open the member under `@KitsInternalApi`, and say which in the commit.

**Commit (kits).** `add docs-kit`. **Commit (Vesta).** `use the published docs kit`

**As built (unattended run, 2026-10-16).** kits `6fb458d`, Vesta see git log. `docs-kit` published `0.1.0`, JVM + Android only: `kits.noIosModules=docs-kit` in the kits `gradle.properties` makes `kits.kmp-library` declare no iOS targets; Vesta declares `api(libs.kits.docs)` on `jvmMain` and `androidMain`, not `commonMain` (nothing in common/iOS source imports it). Moved with the kit: `AutomergeDocumentCoreTest`, `MailboxEntryTest`, `SpaceDocumentStoreTest`, `SyncEnvelopeTest` (21 tests), plus new `DocsKitBoundaryTest` (2, replaces the registry test's import scan). Deviation: the other five tests listed above need Vesta's Pantry/Roster documents and stay in Vesta (a kit-side version over synthetic documents is a follow-up; no `docs-kit-testing` module was needed). `DocumentTypeRegistrationAcceptanceTest` and `DocumentIdsTest` stay as planned. Gate M: klib API dump for a module with no iOS target is unverified on a Mac. The module opts in `KitsInternalApi` for its own use (compiler option, as blobs-kit).

## WP21. spaces-kit: rename and repackage (three commits)

This is the largest and most entangled kit (21 files, 2,625 lines; `HomeRosters` calls `ProfileRepository`, `HomeJoinService` calls `HomeRosters`, `HomeGroups` and `HomeHubs` call `rosters` and `profiles`). Do it as three commits in one session, each Gate V green:

- **21a. Documents and profile.** `HomeRosterDocument`, `HomeRegistryDocument`, `InviteSecrets`, `ProfileRegistryDocument`, `ProfileRegistry`, `HomeRegistry`, `ProfileRepository`, `ProfileChangeListener`. Rename; `shape` -> `kind: String` in the registry document API (the stored key stays `shape`); `SpaceRegistryDocumentId = "user:homes"`.
- **21b. Services.** `HomeRosters`, `HomeJoinService`, `HomeGroups`, `HomeHubs`. Rename; messages carry `@SerialName`. Configuration (WP3) and seeding (WP4) are constructor inputs.
- **21c. Repositories, messages, links.** `HomeRepository`, `MemberRepository`, `HomeChangeListener`, `RosterChangeListener`, `HomeJoining`, `HomeJoinMessage`, `HomeGroupMessage`, `HomeHub`, `InviteLink`. `HomeRepository` becomes `SpaceRepository` speaking strings; Vesta keeps a small `HomeRepository` adapter that maps `SpaceRepository` to `HomeShape`/`HomeDetails`/`HomeListItem` for its view models.

**Public surface.** What Vesta uses: `SpaceRepository`, `MemberRepository`, `SpaceJoining` (the interface the UI calls), `SpaceHub`, `SpaceChangeListener`, `RosterChangeListener`, `ProfileRepository`, `ProfileChangeListener`, `InviteLink`, `MemberRole`, `SpaceSeeding`, `SpacesConfig`. What `sync-kit` needs: `SpaceRosters`, `SpaceRegistry`, `ProfileRegistry`, `SpaceJoinService`, `SpaceGroups`, `SpaceHubs`, the registry document id constants, the message types, `HomeRosterDocument` functions the engine calls. These are `@KitsInternalApi`.

**Acceptance (each commit).** Gate V; WP10 compat tests (the invite-link strings, message JSON, existing-install fixture, `PendingMessage` payload); `HomeRoster*Test`, `HomeJoin*Test`, `HomeGroupsTest`, `HomeHubsTest`, the profile tests; `KitBoundaryTest`: `spaces` imports only `docs`, `x0x`, `store`.

**Order / parallel.** After WP1-WP4, WP19. Sequential; touches 28 + 20 + 35 importers. **Risk:** **high** for unattended: it is the membership and join path, and a rename slip in a `@SerialName` would only show as a failed join between two real versions. The golden tests catch the string forms; they cannot catch a semantic slip. Recommend the session stops after each of 21a/21b/21c for the user to run Gate D (create a Home, invite by link, accept on a second instance) at least after 21c.

**Commits.** `rename the profile and registry documents to the kits package`; `rename the membership services to the kits package and Space vocabulary`; `rename the Home repositories, messages and invite link to the kits package`

**As built (unattended run, 2026-10-16).** Three Vesta commits: `d275208` (21a), `9b2fec98` (21b), `307d445` (21c); all in package `uk.veritaslabs.kits.spaces` (ONE package, as docs), 24 files. Deviations from the split above: (1) 21b also moved the types the services speak (`SpacesConfig`, `SpaceJoining`, `SpaceJoinMessage`, `SpaceGroupMessage`, `SpaceHub`, `InviteLink`, `MemberRole`), because the services cannot be renamed without them; 21c therefore holds only `SpaceRepository`, `MemberRepository`, `RosterChangeListener` and the Vesta adapter. 21a also moved `SpaceSeeding` and `SpaceChangeListener` + `StoredSpace` (the registry implements and uses them). (2) `SpaceRepository` speaks `StoredSpace(id, name, kind, createdAt)`; Vesta keeps `HomeRepository(spaces: SpaceRepository)` mapping to `HomeListItem`/`HomeDetails` (`HomeListItem`, `HomeDetails`, `SelectedHome`, the view models stay in Vesta; `HomeShape` is the kind). (3) Kotlin renames: `RedeemResult.AlreadyInHome` is `AlreadyInSpace`, `SpaceRosters.homeDocument` is `spaceDocument`, `importLocalHomes` is `importLocalSpaces`; wire/persisted names verified by the compat tests, unchanged. Gate D was not run (nothing can be launched here); the join path (create a Home, invite by link, accept on a second instance) is owed by hand.

## WP22. Extract `spaces-kit`

**Steps / files.** As WP14. Tests that move: `jvmTest/home` (2), `homegroup` (2), `homejoin` (3), `profile` (1), `HomeGroupsTest`, `HomeHubsTest`, `HomeJoinRedeemTest`, `HomeJoinRequestsTest`, `HomeRosters*Test` (3), `commonTest/home`, `commonTest/homejoin`, `UnrecognisedKindTest`. Vesta keeps the view models (`InviteLinksViewModel`, `RedeemInviteViewModel`, `HubViewModel`), `SelectedHome`, `HomeRepository` adapter, and the screens.

**Acceptance.** Gates K and V, Gate D, Gate M (the `commonMain` part has iOS source).

**Order / parallel.** After WP20, WP21. Sequential. **Risk:** high (see WP21). Unattended: the move is mechanical; do it only with WP21 green and the user available for Gate D.

**Commit (kits).** `add spaces-kit`. **Commit (Vesta).** `use the published spaces kit`

**As built (unattended run, 2026-10-16).** kits `add spaces-kit`; Vesta `use the published spaces kit`. `spaces-kit` `0.1.0` is JVM + Android + iOS: `commonMain` (iOS compiles it on Linux: it needs only store-kit; `SpacesKitBoundaryTest` guards that no x0x, docs or Automerge import reaches it) and `jvmAndroidMain` (x0x-kit, docs-kit). Vesta declares `api(libs.kits.spaces)` in `commonMain`. Public plain: repositories, `SpaceJoining` and its data types, `SpaceHub`, listeners, `InviteLink`, `MemberRole`, `SpaceSeeding`, `SpacesConfig`, `ProfileRepository`, new `JoinDenial` (the `Denied*` strings the picker screen needs; `SpaceJoinMessage.Denied*` alias them). `@KitsInternalApi`: the six services, `SpaceJoinMessage`, `SpaceGroupMessage`, the roster/registry/profile documents and `InviteSecrets`; Vesta files that touch them carry `@file:OptIn`. Tests moved (146, Vesta's count down by 146 on jvmTest and 14 on testAndroidHostTest, the kits' up by the same plus 17 new: `SpaceRepositoryTest` 14 (a kind-string copy of the repository test; `HomeRepositoryTest` stays in Vesta as the adapter test) and `SpacesKitBoundaryTest` 3): `SpaceGroupsTest`, `SpaceHubsTest`, `SpaceRosters{DeviceName,Gone,Invite}Test`, `MemberRepositoryTest`, `ProfileRepositoryTest`, `SpaceRosterDocumentTest`, `ProfileRegistryDocumentTest`, `SpaceJoinMessageTest`, `InviteLinkTest`. Stayed in Vesta (they use Vesta's seeding, Pantry or the device-sync message family): `HomeJoinRedeemTest`, `HomeJoinRequestsTest`, `HomeSeedingTest`, `UnrecognisedKindTest`. Gate M owes: publish from a Mac and commit the klib dump, build the iOS framework (the kit's iOS klibs have never been linked). The `ProfileRepository` row import `uk.veritaslabs.vesta.db.Profile` is store-kit's generated package (WP15 deviation).

## WP23. sync-kit: rename and repackage

**Steps.** `DeviceDataSyncService`, `SyncActivityService`, `devicesync/*` (except `DeviceDataSyncService` is in `datasync/`), `devicepairing/PairingLink`, `syncactivity/{Models,Repository,SyncVerdict}`. Rename: `DeviceDataSync` interface parameters `homeId` -> `spaceId`; `SyncActivity*` models; `HomeJoining`-typed members. The engine's constructor (after WP5) is the kit's main public API: `client`, `database`, `devices`, `documentTypes`, `seeding`, `configs`, `deviceName`, timings. Keep the view models (`DevicePairingViewModel`, `SyncActivityViewModel`) in Vesta.

**Acceptance.** Gate V; compat tests; `DeviceDataSyncServiceTest`, `DocumentIdsTest`, `DocumentTypeRegistrationAcceptanceTest`, `SyncActivityServiceTest`, `UnsupportedInputActivityTest`, `DeviceSyncServiceTest`, daemon integration tests (opt-in, skipped unless a daemon is available: say so). `KitBoundaryTest`: `sync` imports only the four lower kits.

**Order / parallel.** After WP5, WP6, WP21. Sequential. **Risk:** high (engine; 21 test files; `datasync` package dissolves here). Unattended: yes for the mechanical move; Gate D before commit.

**Commit.** `rename the sync engine and device pairing to the kits package`

## WP24. Extract `sync-kit`

**Steps / files.** As WP14. Moves with it: `jvmTest/datasync` engine tests (`DeviceDataSyncServiceTest`, `ActivityDevice`, `DaemonHarness`, `SyncActivityServiceTest`, `UnsupportedInputActivityTest`, the daemon integration tests, `BlobTransferDaemonIntegrationTest` stays with `blobs-kit`), `jvmTest/devicesync`, `devicepairing`, `syncactivity`. The acceptance suite (`jvmTest/acceptance`, 21 files, Compose UI) **stays in Vesta**; `SyncActivityAcceptanceTest` stays because it drives Vesta's screen.

**Acceptance.** Gates K and V, Gate D (two-instance pairing and a Home sync end to end), Gate M.

**Order / parallel.** After WP22, WP23. Sequential. **Risk:** high; the test files here are the largest (`datasync` tests total about 6,200 lines) and the least independent, since `DeviceDataSyncServiceTest` builds the whole stack. Expect to split the move: engine tests first, harnesses second.

**Commit (kits).** `add sync-kit`. **Commit (Vesta).** `use the published sync kit`

## WP25. Assistants: not extracted yet (defer, with a split ready)

**Why defer.** The 5 generic files are 329 lines. The valuable idea (the `CommandFamily` plugin point) needs a redesign of `AgentCommand` (a sealed interface with `AddPantryItem`), `AssistantCapability` (an enum stored by name in `AssistantGrant.capability`) and the parser (`urn:vesta:*`), on the two-gate authorisation path, which the extraction plan says to keep unchanged. That is a design change on a security path with no second consumer to design against, and it is not something to do unattended.

**If it is done later, split it:**
- **25a (mechanical).** Move `AssistantRepository`, `Assistants`, `CommandLog`, `AgentAllowlist`, `EnvAgentAllowlist` to `assistants-kit` with the enum untouched. Vesta keeps `AgentCommand`, the parser, replies, handlers and hosts. Compile-level only, depends on `store-kit`.
- **25b (design, needs an ADR and the user).** `AssistantCapability` as an open id with registered capabilities (stored strings unchanged: `ListHomes`, `PantryAdd`), and `CommandFamily`. Tests: `AssistantPermissionsTest`, `AssistantsAcceptanceTest`, `AssistantRepositoryTest`, plus a new test that an unregistered capability is denied.

**Recommendation.** Leave `agent/` in Vesta until a second app needs assistants. Update the extraction plan to say so.

## WP26. Native packaging (the Rust crate) — human-owned

**JNI exports still use the Vesta package; rename together with the Rust crate when native packaging is done by a human.** That means: rename the package of `X0xBridge` to `uk.veritaslabs.kits.x0x`, rename the five `Java_uk_veritaslabs_vesta_x0x_X0xBridge_*` exports in `lib.rs`, change the proguard keep rule in `androidApp/proguard-rules.pro`, then run Gate D on an emulator. `X0xBridgeSymbolsTest` goes red if only one half is changed.

**Goal.** `x0x-kit` is self-contained on mobile: the `.so` for Android and the static library for iOS come from the kit, not from Vesta's `ffi/` and scripts.

**What it involves.** Move `ffi/x0x-ffi`, `scripts/build-android-ffi.sh`, `scripts/build-ios-ffi.sh` and the cinterop def to `vesta-kits`; package the `.so` into the `x0x-kit` Android artifact (unverified whether AGP 9.1.1's KMP library plugin accepts `jniLibs`; the fallback is a separate `x0x-kit-native` Android AAR); publish the iOS static libraries as an XCFramework for Xcode to link, because a klib cannot carry the archive; move the Rust half of Vesta's `generateLicenses`.

**Why not unattended.** It needs the Android NDK, an emulator or device, and a Mac (the iOS build cannot run on Linux), and none of it is checkable by the Kotlin test suite. WP13's symbol test covers only the name match. Leave Vesta building the native libraries from its own `ffi/` until a human does this with the hardware in front of them; WP14 already works that way.

## WP27. Close-out

- Vesta's `build.gradle.kts` `generateLicenses`: exclude group `uk.veritaslabs.kits` from the third-party list (`excludeOwnGroup` only knows Vesta's group); re-run it and diff `licenses.json` against the previous one.
- Decide the Mac publishing story: iOS needs the kits' iOS klibs, and publishing from Linux omits them. Either publish from the Mac as well (same version, `publishToMavenLocal` on both) or hold the iOS app on the pre-extraction `:shared` source until a Mac does. Record the choice; do not leave it implicit.
- Add `docs/testing/acceptance-tests.md` section "Kits extraction": Given a database and documents written by the build before the extraction, When the new build starts, Then the Homes, members, documents, pending messages and settings are intact (`ExistingInstallStillOpensTest`); Given an invite link made by the old build, When opened on the new one, Then it decodes to the same Home; Given a new-build device and an old-build device in one Home, When either edits, Then the other receives it (a manual two-version check, because the automated tests run one version).
- Update `shared-packages-extraction-plan.md`: status of findings 1-12, the corrected module table (this plan's), `device-kit` renamed `sync-kit`, `app-shell-kit` dropped, `assistants-kit` deferred. Move the infrastructure ADRs (003-008, 012-014, 017-021, 023, 033-035) into the kits repo's `docs/` with a pointer left in Vesta, or leave them and link; pick one.
- A checklist for version drift: kits' `libs.versions.toml` versus Vesta's (Kotlin, AGP, coroutines, serialization, SQLDelight, automerge).
- Colo (`~/Development/scratch/ColoPoc`, `town.colo.x0x.X0xClient`) has its own JVM-only x0x client and is the natural first outside consumer of `x0x-kit`; adopting it would be the first test that the API stands without Vesta. Not part of this plan.

---

# Order, dependencies and parallelism

`->` means "must be committed before". "Parallel" means "no file in common with" the listed WPs, so a separate worktree can run it.

| WP | What | Needs | Parallel with | Risk | Unattended |
|---|---|---|---|---|---|
| 0 | Baseline + boundary test | — | 8, 12 | low | yes |
| 1 | Domain types out of `ui.*` | 0 | 2, 7 (UI import lines may collide) | low-med | yes |
| 2 | Platform inputs passed in | 0 | 4, 7 | low | yes (Gate D owed) |
| 3 | Config constants | 1, 2 | — | low | yes |
| 4 | Seeding hook | 0 | 2, 7 | medium | yes |
| 5 | Engine takes types from outside | 4 | — | med-high | yes (Gate D owed) |
| 6 | Opaque module ids | 5 | — | low-med | yes |
| 7 | Compose out of x0x and blobs | 0 | 2, 4 | low | yes |
| 8 | ADR-036 database decision | — | everything | none | write, then wait for user |
| 9 | Database repackage | 2, 3, 8 (agreed) | — (109 files) | medium | yes |
| 10 | Compatibility tests + fixture | 0 | 8, 11, 12 | low | yes |
| 11 | ADR-037 vocabulary | — | everything | none | yes |
| 12 | Kits repo + smoke publish | — | every `:shared` WP | **high** (unverified tooling) | skeleton only; human reviews consume check |
| 13 | x0x repackage + JNI | 0, 2, 7 | — (75 files) | med-high | yes (Gates D, M owed) |
| 14 | Extract `x0x-kit` | 12, 13 | 16 | medium | yes if 12 was clean |
| 15 | Extract `store-kit` | 9, 10, 14 | 16 | **high** | spike + copy; user checks old DB before deleting |
| 16 | Blob reference seam | 1, 7 | 14, 15, 19-24 (except `main.kt`/`MainActivity`) | low-med | yes |
| 17 | Blobs rename + repackage | 9, 10, 11, 16 | — | low-med | yes |
| 18 | Extract `blobs-kit` | 15, 17 | — | low-med | yes (Gate M owed) |
| 19 | docs-kit rename + repackage | 5, 6, 9, 10, 11 | — | medium | yes |
| 20 | Extract `docs-kit` | 14, 15, 19 | — | medium | yes |
| 21a-c | spaces-kit rename + repackage | 1-4, 19 | — | **high** | stop after each; Gate D |
| 22 | Extract `spaces-kit` | 20, 21 | — | **high** | mechanical; user present for Gate D |
| 23 | sync-kit rename + repackage | 5, 6, 21 | — | high | mechanical; Gate D |
| 24 | Extract `sync-kit` | 22, 23 | — | **high** | split harness moves |
| 25 | Assistants | deferred | | | do not run |
| 26 | Native packaging | 14 | | high | **human only** |
| 27 | Close-out | all | | low | yes |

**Longest chain:** 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 9 -> 19 -> 20 -> 21 -> 22 -> 23 -> 24 -> 27 (kit WPs 12, 13, 14, 15, 17, 18 slot in as their needs are met). **What genuinely runs in parallel** (no common files): the kits repo work (12, and the kit-side halves of 14/15/18/20/22/24 until the Vesta swap) against any `:shared` WP; ADRs 8 and 11 against anything; 16 against the large renames. **What does not:** every WP that rewrites imports across 50-110 files (9, 13, 17, 19, 21, 23, and each "extract" Vesta side) must run alone, because their file sets overlap in imports and in `shared/build.gradle.kts`.

**Smallest useful stop points** (each leaves Vesta better and the next step optional): after WP7 (Vesta's engine and registry are independent of Vesta's features; nothing else is committed to); after WP14 (one kit published, the pipeline proven); after WP18 (three small kits out, the three large ones still in `:shared` and still buildable).

## Work that is too risky or too big to run unattended, and how to split it

| WP | Why | How to split or defer |
|---|---|---|
| 12 | AGP 9.1.1 KMP publishing and consumption unverified | Do the skeleton; a human reads the consume-check result before WP14. If it fails, stop. |
| 13 | JNI rename invisible to the compiler; iOS invisible to Linux | Kotlin move and `X0xBridgeSymbolsTest` unattended; Gate D by hand; Gate M by hand. |
| 15 | Migration chain protects user data; SQLDelight under `explicitApi` and in a new module unverified | Spike, then copy; user runs the old-database check on a copy of a real `vesta.db`; only then delete Vesta's schema in a second commit. |
| 21, 22 | Join and roster semantics; wire strings | Three commits (21a-c) with a stop after each; compat tests (WP10) are the safety net; Gate D after 21c and 22. |
| 23, 24 | The engine; biggest, most entangled tests (about 6,200 lines under `datasync`) | Repackage first (23), then extract in two commits: engine tests, then the harnesses. |
| 25 | Security path, no second consumer | Defer; 25a is optional and mechanical. |
| 26 | Needs NDK, emulator, a Mac | Human only. |

## Risks

| Risk | Handling |
|---|---|
| A persisted or wire name changes by accident during a rename | The inventory table; WP10 golden strings and the old-database fixture, produced by the old code and never regenerated; `verifyMigrations` and a column-name test for the schema. |
| JNI symbols drift from the Kotlin package | `X0xBridgeSymbolsTest` (WP13) plus Gate D. |
| Old and new builds disagree on the wire in the field | WP10 covers encode/decode of old bytes; the manual two-version check in WP27's acceptance section covers behaviour. |
| A kit needs another kit's `internal` member, or a test does | `@KitsInternalApi` for plumbing; tests move with the code; cross-kit tests use the testing artifacts. |
| Gradle: Maven Local drift between kit and Vesta | Fixed version per kit release; Gate K always republishes before Gate V; `mavenLocal()` is filtered to the kits group so a stale copy of anything else cannot resolve from it. |
| iOS breaks and nobody sees it | Every iOS-touching WP lists Gate M; WP27 records the Mac publishing decision. |
| Unattended sessions stack failing changes | Protocol above: one WP, stop at the first non-baseline red, no fixing forward. |
| The plan's counts or file lists are stale by the time a WP runs | Each WP says "the compiler lists the rest"; WP0's table is code, so a moved file fails the boundary test rather than being missed. |

## Answers to the open questions (team lead, unattended run, 2026-10-16)

The user was asleep for the run and asked the lead to carry it through; these defaults were taken and are reported in the final report. All are reversible while nothing is published beyond Maven Local.

- Database: option D (one `store-kit` owns the schema). **Agreed.**
- Just-in-time rename per kit. **Agreed.**
- `sync-kit` not `device-kit`; `app-shell-kit` dropped; `assistants-kit` deferred. **Agreed.**
- Packages and group `uk.veritaslabs.kits`. **Agreed.**
- iOS: no Mac. Gate M is recorded as owed, not run. iOS source is kept compiling by inspection only.
- WP26 (native packaging): **left to a human.** Vesta keeps building the Rust libraries.
- WP15's deletion of Vesta's own schema: **not done unattended**; the kit gets a copy first and the deletion waits for a human old-database check.

---

# Appendix A. File-to-kit assignment (2026-10-15, `main` at `f4ebb2d`)

Paths relative to `shared/src/<set>/kotlin/uk/veritaslabs/vesta/`. `(c)` commonMain, `(ja)` jvmAndroidMain, `(a)`, `(j)`, `(i)` the platform sets. This is what `KitMembership.kt` (WP0) encodes.

**x0x-kit** (15 files, 1,945 lines): `x0x/{AbstractX0xClient, X0xClient (with expect createX0xClient), X0xModels, X0xEventHub, X0xApiException, X0xHealth, X0xPeers, X0xNetworkDiagnostics, X0xNetworkPolicy, X0xStuckDetector}` (c); `x0x/StandaloneX0xClient.jvm` (j); `x0x/{EmbeddedX0xClient.android, EmbeddedX0x.android}` (a); `x0x/{EmbeddedX0xClient.ios, EmbeddedX0x.ios}` (i). Stays in Vesta: `X0xStatusCard`, `X0xConnectivityIndicator`, `InviteLifetime`, the WP7 `remember…` wrappers. Testing artifact: `commonTest/x0x/FakeX0xClient`, `jvmTest/devicesync/FakeX0xNetwork` (check whether the fake group store it uses is a separate file).

**store-kit** (9 files, 239 lines + 21 `.sq`, 19 `.sqm`, 20 snapshots): `db/{DatabaseDriverFactory, DatabaseOpenResult, DatabaseSafety}` (c); `db/DatabaseDriverFactory.{android,jvm,ios}`, `db/DatabaseFiles.{android,jvm,ios}`. Tests: `jvmTest/db/*` (8).

**docs-kit** (17 files, 1,243 lines): `docs/{HomeDocument, HomeDocumentType, HomeDocumentRegistry, HomeDocumentStore, AutomergeDocumentCore, DocumentHeads}`, `mailbox/{MailboxDocuments, MailboxEntry, MailboxReader, MailboxSnapshot, MailboxWriter, StoredHeads}`, `datasync/{SyncEnvelope, SyncableDocument, SyncAcknowledgement, SyncEventRecorder, ChangesWaiting}` (all ja).

**spaces-kit** (21 files, 2,625 lines): `datasync/{HomeRosters, HomeRegistry, HomeJoinService, HomeGroups, HomeHubs, ProfileRegistry}` (ja); `automerge/{HomeRosterDocument, HomeRegistryDocument, InviteSecrets, ProfileRegistryDocument}` (ja); `home/{HomeRepository, MemberRepository, HomeChangeListener, RosterChangeListener}`, `homejoin/{HomeJoining, HomeJoinMessage, InviteLink}`, `homegroup/{HomeHub, HomeGroupMessage}`, `profile/{ProfileRepository, ProfileChangeListener}` (c). Stays in Vesta: `home/SelectedHome`, `homejoin/{InviteLinksViewModel, RedeemInviteViewModel}`, `homegroup/HubViewModel`, the `HomeRepository` adapter (new).

**sync-kit** (11 files, 1,568 lines): `datasync/{DeviceDataSyncService, SyncActivityService}` (ja); `devicesync/{DeviceDataSync, DeviceSyncMessage, DeviceSyncService, PairingProgress, SyncedDeviceRepository}`, `devicepairing/PairingLink`, `syncactivity/{SyncActivityModels, SyncActivityRepository, SyncVerdict}` (c). Stays in Vesta: `devicepairing/DevicePairingViewModel`, `syncactivity/SyncActivityViewModel`.

**blobs-kit** (15 files, 1,188 lines): `blobs/{BlobRuntime, Blobs, PhotoEncoder}` (c); `blobs/{BlobIngest, BlobMessage, BlobRepository, BlobStore, BlobTransferService, DeviceBlobRuntime, PhotoFetcher, PhotoSources, RepositoryBlobs, TransfersJanitor}` (ja); `blobs/AndroidPhotoEncoder` (a); `blobs/SkiaPhotoEncoder` (j). Stays in Vesta: `blobs/Placeholder`, the `LocalBlobs` composition local.

**assistants (deferred)** (5 files, 329 lines): `agent/{AgentAllowlist, AssistantRepository, Assistants}` (c); `agent/{CommandLog, EnvAgentAllowlist}` (ja).

**Vesta, protocol (stays)** (7 files, 706 lines): `agent/{AgentCommand, AgentCommandParser, AgentReplies, AgentApprovalHost, AssistantHosts}` (c); `agent/{AgentCommandService, PantryCommandHandler}` (ja).

**Vesta, everything else** (174 files, 30,763 lines): `ui/**` (97 files, 23,659 lines), `App.kt`, the seven features' `automerge/*` (Pantry, ShoppingList, Cookbook, Merchants, MealPlan, Calendar, Task documents, sources and `VestaHomeSources`), `calendar/`, `tasks/`, `pantry/`, `recipes/`, `settings/`, `licenses/`, `files/`, `autonomi/`, `Platform*`.

# Appendix B. The cross-kit edges that remain after the fixes (what each kit may import)

| From | May import | Edges counted (files) | Concrete things used |
|---|---|---|---|
| docs | x0x, store | 2 | `X0xClient` (MailboxReader, MailboxWriter); database queries |
| spaces | docs, x0x, store | 6 + 8 | `SyncEventRecorder`, `SyncableDocument`, `HomeDocument`, `changesSince`/`encodeHeads`, `StoredHeads`; `X0xClient`, `X0xApiException`, `X0xDirectMessage`, `X0xGroupMember` |
| sync | docs, spaces, x0x, store | 11 + 25 + 7 | `ChangesWaiting`, `SyncTargets`, `HomeMailboxDocuments`, `MailboxReader/Writer`, `SyncAcknowledgement`, `SyncEnvelope`, `SyncEventRecorder`; `HomeRosters`, `HomeJoinService`, `HomeGroups`, `HomeHubs`, `HomeRegistry`, `ProfileRegistry`, `ProfileRepository`, the listeners and messages; `X0xClient`, `X0xEventHub`, `X0xDirectMessage` |
| blobs | x0x, store | 7 | `X0xClient`, `X0xEventHub` (`Backoff`), `X0xNetworkPolicy`, `X0xFileEvent`; `homeQueries.selectAll` (replaced by a `homes` lambda in WP16) |
| x0x, store | none | 0 | |

Everything above rests on greps and a symbol scan, not on a compiler run per kit; the first compile of a kit in WP14 and later is the final check, and the WP0 test keeps it from drifting.

# Baseline (WP0, measured 2026-10-16 on `main` at `871ce26`, before the boundary test was added)

**Gate V, exact command** (all task names exist; confirmed with `./gradlew tasks --all`):

```
./gradlew :shared:jvmTest :shared:testAndroidHostTest :shared:verifyCommonMainVestaDatabaseMigration :desktopApp:compileKotlin :androidApp:compileDebugKotlin --continue
```

Use `--continue` so one red task does not hide the others. About 2 minutes warm on an idle machine.

| Task | Result |
|---|---|
| `:shared:jvmTest` | 1,861 tests, 1 failed, 0 skipped |
| `:shared:testAndroidHostTest` | 199 tests, 0 failed, 0 skipped |
| `:shared:verifyCommonMainVestaDatabaseMigration` | passes |
| `:desktopApp:compileKotlin` | passes |
| `:androidApp:compileDebugKotlin` | passes |

Total 2,060 tests (the registry plan's 1,816 predates later work; re-measured as the plan asked).

**Known failure (pre-existing, intermittent):** `CalendarEventsAcceptanceTest.aPrivateEventIsSavedOnThePrivateCalendarMarkedWithAPadlockAndHasNoParticipants` failed on the first baseline run and passed on the second run (after WP0's test was added: 1,865 tests, 0 failed, `BUILD SUCCESSFUL`). Treat it as flaky; if it is the only red test, rerun `--tests '*CalendarEventsAcceptanceTest'` alone.

**Known flaky under load:** `DeviceDataSyncServiceTest` and the Compose UI acceptance tests. Both passed on the baseline run. Rerun the one class alone before calling a failure a regression.

**iOS:** the iOS targets are disabled on this Linux host ("Cross Compilation with Cinterop Not Supported"; `iosSimulatorArm64Test` disabled), so no iOS task runs here. Gate M is the human's.

**Boundary test (added by WP0):** `KitBoundaryTest` adds 4 tests to `:shared:jvmTest` (1,865 after WP0). Its `expectedViolations` starts with 26 entries, normalised to file level with platform suffixes folded (`Platform.kt` stands for the expect and its three actuals), and matches the leak table in section 3. Notes from writing it:

- It also found `store -> x0x`: `db/DatabaseDriverFactory.android` imports `VestaAndroid` from `EmbeddedX0xClient.android` (the plan lists this leak under x0x, fixed by WP2).
- Imports of `ui/screens/*Screen.kt` types by spaces and sync files are the WP1 leaks; `x0x/InviteLifetime` is WP3; `Platform` is WP2.
- It resolves an import through the file that declares the top-level name, so `SyncTargets` and `HomeMailboxDocuments`, which sit inside other files, resolve correctly.
- Same-package references and wildcard imports are invisible to it, as the plan said.
- A fourth test pins the Appendix A file counts per kit (x0x 15, store 9, docs 17, spaces 21, sync 11, blobs 15, assistants 5) so a rename cannot silently move a file out of a kit.
