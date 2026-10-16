# Kits extraction: what is still owed (2026-10-16)

Prioritised. "Owner" says who can do it. Source: [ADR-038](../adr/ADR-038-kits-extraction.md) and the
[implementation plan](kits-extraction-implementation-plan.md). Nothing here is known to be broken in the Linux Gate V.

## 1. Before this work is trusted with real data

1. **Real `vesta.db` copy check (human).** Copy a real user database from before the extraction, open it with the new build, check every
   Home, member, document and pending message. The fixture test (`ExistingInstallStillOpensTest`) passes, but the fixture is synthetic.
   Vesta's own schema was already deleted (WP15) on the strength of that fixture, so do this before releasing.
2. **Gate D (human, emulator or two devices).** Run the real x0x daemon on Android (the `.so` is still Vesta's, loaded by the kit's
   `X0xBridge`), then pair two devices, join a Home, sync a Pantry item and a Task. Covers the JNI rename path, the join and roster
   semantics and the engine, none of which an automated test runs against a daemon here (daemon integration tests are opt-in).
3. **Two-version sync check (human, two devices).** Old build and new build in one Home each receive the other's edits
   (acceptance item 10).

## 2. iOS

4. **Gate M (human with a Mac).** Publish the kits from the Mac (same `kitsVersion`, `publishToMavenLocal`), run `apiDump` there and commit the
   `*.klib.api` files, build Vesta's iOS framework and run a smoke test. Until then Vesta's iOS targets do not resolve `x0x-kit`,
   `x0x-kit-testing` or `sync-kit`, and none of the kits' iOS code has been compiled to a binary.
5. **KMP iOS resolution failure on Linux (noticed).** Every Vesta Gradle configuration on Linux logs `e: KMP Dependencies Resolution Failure`
   for `appleMain` and `commonMain` (x0x-kit, sync-kit, testing artifact unresolved for `iosArm64`/`iosSimulatorArm64`). It does not fail the
   build (jvm and android tasks pass) but is noisy and could hide a real error. Options: publish the iOS klibs from a Mac once and keep them in
   Maven Local; or declare Vesta's apple targets only on a Mac host (as the kits do); or add a "no iOS" mode. `docs-kit` has no iOS binding at all,
   so Vesta's apple code can never use document plumbing directly.

## 3. Deferred work

6. **WP26 native packaging (human, NDK + device + Mac).** Move `ffi/x0x-ffi` and the build scripts into the kits, rename `X0xBridge` to
   `uk.veritaslabs.kits.x0x` together with the five `Java_..._X0xBridge_*` exports and the proguard rule, package the `.so` (check whether AGP 9.1.1
   accepts `jniLibs`) and an iOS XCFramework, move the Rust half of `generateLicenses`. `X0xBridgeSymbolsTest` guards the rename.
7. **WP25 assistants (when a second app needs them).** 25a is mechanical (five files, 329 lines to `assistants-kit`); 25b needs an ADR (open
   `AssistantCapability` ids, `CommandFamily`) and must keep the two-gate authorisation unchanged.

## 4. Code and API tidy-ups

8. **`SyncOrder` names Vesta's features inside `sync-kit`** (`DeviceDataSyncService.kt`: pantry, shopping-list, cookbook, merchants, mealplan,
   calendar, private-calendar, tasks, private-tasks). A second app's documents sort after these and the kit's tests register synthetic notes
   under Vesta's ids. Make the order a constructor/config parameter (ADR-035 calls it persisted behaviour, so keep Vesta's list as the default
   in `VestaKitConfig` and keep `DocumentIdsTest`).
9. **Test-local `homeId` names.** Kit tests still use `homeId` in test code and fixtures (for example `SpaceRepositoryTest`, `SpaceGroupsTest`,
   `SyncEventQueriesTest`, `SchemaGuardTest`). Where the name is a persisted column or `@SerialName` it must stay; elsewhere rename to `spaceId`.
   Also the generated query classes still live in `uk.veritaslabs.vesta.db` (`.sq` directory); moving them is a schema-neutral rename.
10. **Composition root.** `VestaApp`, `VestaKitConfig` and the `main.kt` / `MainActivity` wiring would be copied by a second app (finding 12).
11. **Infrastructure ADRs** (003-008, 012-014, 017-021, 023, 033-035) are still in Vesta; copy or move them to the kits repo when it has a
    remote or a second consumer.
12. **Colo as first outside consumer of `x0x-kit`** (`~/Development/scratch/ColoPoc`): the first proof that the API stands without Vesta.
13. **`SeedGate`** (Pantry and shopping list queueing edits until the creator's document arrives) was never generalised.

## 5. Test health

14. **Flaky families.** `DeviceDataSyncServiceTest` (timeouts, `everyDocumentsEditIsLoggedInSyncActivityUnderItsModule` this run),
    Compose UI acceptance tests (`CookingAcceptanceTest`, three failures this run with `UncaughtExceptionsBeforeTest` from `TestMainDispatcher`,
    which looks like a leaked main-dispatcher from a previous test and is a new member of the family), and
    `CalendarEventsAcceptanceTest.aPrivateEventIsSavedOnThePrivateCalendarMarkedWithAPadlockAndHasNoParticipants` (line 114). All five passed on a
    rerun of their classes alone. They fail more under load (a parallel kits build was not running at the time, so it is not only load).
    Worth a pass: use `runTest` with injected dispatchers and reset `Dispatchers.setMain` consistently in the UI tests.
15. **Test totals dropped** from 2,060 (WP0 baseline) to 1,439 jvm + 118 android host, because the moved tests now run in the kits
    (`./gradlew build` there). Gate K plus Gate V is the new full gate; neither alone is.
16. **Build-cache caveat.** Gate V's two compile tasks were served from the Gradle build cache after `clean`. Add `--no-build-cache` for a
    from-scratch confidence run before a release.

## 6. Behaviour changes noticed during the extraction (verified by reading the code)

17. **`HomeRepository.get` / `HomeDetails.shape` is now the stored string (WP1, `4a43680`).** Before, `get` mapped it with
    `HomeShape.fromStorageValue`, so an unrecognised stored shape became `null` and the edit screen showed "Other"; saving then overwrote the
    stored value with `other`. Now the string passes through untouched and `HomeEditViewModel` saves it back unchanged. **Not user-visible
    today:** shape selection is parked (commented out in `HomeEditScreen`), new Homes are created as `other`, and every shape value in
    the wild is one `HomeShape` knows. It only matters if a newer app writes a shape this one does not know, and then the new behaviour
    is the kinder one (the value survives a rename). No action needed; keep in mind if shape selection returns.
18. **Placeholder `updatedAt` uses the real clock (WP4, `276eb4a`).** `VestaHomeSeeding` takes its own `nowMillis` (default `Clock.System`)
    for `storeEmptyPantry` / `storeEmptyShoppingList`. `HomeRegistry` used to pass its injected clock to those calls (`HomeJoinService`
    always used the real one). **Not user-visible:** production code constructs `VestaHomeSeeding` with the default real clock everywhere
    (`main.kt`, `MainActivity`), so the stamped value is the same as before, and nothing reads `AutomergeDocument.updatedAt` (it is only
    written by the upserts). The only effect is in tests: a fake clock given to the registry no longer reaches the placeholder rows. Pass the
    same clock to `VestaHomeSeeding` in a test that cares.
