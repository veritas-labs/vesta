# ADR-038: Kits extraction, as built

**Status:** Accepted and built, 2026-10-16 (unattended run by the team lead on the user's standing decisions). Not pushed anywhere:
the kits repo has no remote and publishes to Maven Local only.
Plan: [kits-extraction-implementation-plan.md](../plans/kits-extraction-implementation-plan.md). Background:
[shared-packages-extraction-plan.md](../plans/shared-packages-extraction-plan.md). Follow-ups:
[kits-extraction-followups.md](../plans/kits-extraction-followups.md). Related: [ADR-035](ADR-035-document-type-registry.md),
[ADR-036](ADR-036-kits-database-composition.md), [ADR-037](ADR-037-space-vocabulary.md).

## Context

Vesta's `:shared` module held infrastructure (an x0x client, an SQLite schema, Automerge document plumbing, Home membership, device
sync, blob transfer) that future apps could reuse. The decisions of 2026-10-15 were: separate repository, versioned libraries,
services only (no UI), "Space" as the neutral term, and persisted and wire names frozen.

## Decision (what was built)

**Repository.** `~/Development/VeritasLabs/vesta-kits`, group `uk.veritaslabs.kits`, one `kitsVersion` (0.1.0) for all, Apache-2.0,
Kotlin 2.4.20 / AGP 9.1.1 / Gradle 9.6.1 copied from Vesta. Convention plugins in `build-logic/` (`kits.kmp-library`,
`kits.api-check`, `kits.publishing`, `kits.root-api-validation`). Vesta resolves the kits from a `mavenLocal()` filtered to the kits
group.

**Six kits plus two testing artifacts** (package roots under `uk.veritaslabs.kits.`):

| Artifact | Package | Contents |
|---|---|---|
| `x0x-kit` | `x0x` | the x0x client, event hub, health, peers, diagnostics, embedded-daemon bridge |
| `x0x-kit-testing` | `x0x.testing` | `FakeX0xClient`, `FakeX0xNetwork` |
| `store-kit` | `store` | `KitsDatabase` (the whole schema, 21 `.sq`, 19 migrations), open path, downgrade guard, `@KitsInternalApi` |
| `store-kit-testing` | `store.testing` | `openInMemoryDatabase()` |
| `blobs-kit` | `blobs` | blob store, `BlobReferenceSource`, photo encoders, `blob.want` transfer |
| `docs-kit` | `docs` | `SpaceDocumentType`/`Registry`/`Store`, `AutomergeDocumentCore`, mailbox, sync envelope, activity recorder |
| `spaces-kit` | `spaces` | Spaces, members, rosters, invites, registries, profiles, join/group/hub services, `SpacesConfig`, `SpaceSeeding` |
| `sync-kit` | `sync` | device data sync engine, pairing, synced devices, sync activity models and verdict |

Dependencies: docs, blobs -> x0x + store; spaces -> store + x0x + docs; sync -> store + x0x + spaces + docs. Not extracted:
`assistants-kit` (deferred), `device-kit` (became `sync-kit`), `app-shell-kit` (dropped, services only).

**Public surface rules.** `explicitApi()` in every kit; binary-compatibility-validator (`apiCheck`) with a committed JVM dump.
What another kit or the app's own tests need but a third app should not rely on is `public` under `@KitsInternalApi`
(`@RequiresOptIn`, level ERROR, defined in `store-kit`); the rest is `internal`. Tests moved with their code; a cross-kit test uses
the testing artifacts; Vesta keeps a consumer-side proof (its ten documents syncing) and golden compatibility tests.

**What stayed in Vesta.** All UI and view models; the features' documents, sources and repositories (Pantry, shopping list, cookbook,
merchants, meal plan, calendar, tasks) and `VestaHomeSources`; the agent/assistant code (protocol, handlers, repository, command log,
allowlist); `VestaKitConfig`, `VestaHomeSeeding` and the app composition (`App.kt`, `main.kt`, `MainActivity`); settings, theme,
licences, onboarding; the Rust crate and its build scripts; the Autonomi status client.

**Compatibility.** Nothing persisted or on the wire changed (ADR-037): tables and columns, document ids `<homeId>:<feature>`, message
kinds and JSON fields (`@SerialName("homeId")`), the invite and pairing link layouts, migrations 1-19 and snapshots. Guarded by
`GoldenWireStringsTest`, `DocumentIdsTest`, `SchemaGuardTest` and `ExistingInstallStillOpensTest` against a version-20 database
fixture produced by the old code and never regenerated (copied into the kits as well).

**JNI exception.** `X0xBridge` is in `x0x-kit` but in package `uk.veritaslabs.vesta.x0x`, because the Rust crate (still Vesta's)
exports `Java_uk_veritaslabs_vesta_x0x_X0xBridge_*` and the JVM binds by fully qualified name. The package and the five exports are
renamed together when a human does native packaging (WP26). `X0xBridgeSymbolsTest` compares the compiled bridge's `native` methods
with `lib.rs` and fails if only one side changes. The Android `.so` and the iOS archive are still built by Vesta.

**iOS situation.** Kotlin/Native cinterop does not run on Linux, so `x0x-kit`, `x0x-kit-testing` and `sync-kit` declare iOS targets
only on a macOS host (`kits.iosNeedsMacModules`); `docs-kit` has no iOS targets (Automerge has no iOS binding). The kits were
published from Linux, so those three have jvm and android variants only. Decision: the kits are published to Maven Local from
whichever machine builds them; for iOS to consume them the same `kitsVersion` must also be published from a Mac. No Mac was available:
no iOS code in the kits has been compiled to a binary or run, and the klib API dumps were never produced (Gate M owed). On Linux
Vesta's Gradle configuration logs "KMP Dependencies Resolution Failure" for the `appleMain` and `commonMain` source sets (`x0x-kit`,
`sync-kit` and testing artifact unresolved for iOS); it is logged at error level but the jvm/android tasks pass and nothing fails.

**Deferrals.** WP25 assistants (security path, no second consumer, five generic files). WP26 native packaging (needs NDK, device, Mac).
Moving `SyncOrder` out of `sync-kit`. A composition root. The infrastructure ADRs were left in Vesta, not moved.

## Verification (2026-10-16)

- Fresh `git clone` of vesta-kits: `./gradlew build apiCheck publishToMavenLocal` passed with only what is in git (needs the Android SDK
  via `ANDROID_HOME` or a gitignored `local.properties`; the build cache was on).
- Vesta after `./gradlew clean`, Gate V against those artifacts: `:shared:jvmTest` 1,439 tests ran, 5 failed and all 5 passed when their
  classes were rerun alone (`CalendarEventsAcceptanceTest` 1, `CookingAcceptanceTest` 3, `DeviceDataSyncServiceTest` 1: the known flaky
  families); `:shared:testAndroidHostTest` 118 passed, 0 skipped; `:desktopApp:compileKotlin` and `:androidApp:compileDebugKotlin`
  passed (served from the build cache). The totals are lower than the 2,060 of the baseline because the moved tests now run in the kits.
- `generateLicenses` no longer lists the kits; its list differs from before only by a stale JNA entry (see the plan, WP27).
- Not run: Gate D (emulator with the real daemon), Gate M (Mac), a copy of a real `vesta.db`, the two-version sync check.

## Lessons

1. **Pin compatibility before moving anything.** Golden strings and an old-database fixture written by the old code, never
   regenerated, made every rename mechanical and let the compiler plus a few tests stand in for judgement.
2. **Rename just-in-time, per kit, with a script and `@SerialName`.** The compiler finds what a script misses; the wire does not.
3. **Seams before moves.** Hooks (`SpaceSeeding`, `BlobReferenceSource`, `SpacesConfig`, app-supplied document types) went in while
   the code was still in `:shared`, so each extraction was a copy plus an import change.
4. **Gradle plugin classloaders bite.** Plugins used by convention plugins (binary-compatibility-validator, SQLDelight) must be on the
   `build-logic` classpath, not applied with a `plugins { version }` block, or every convention plugin fails with a
   `ClassNotFoundException` for the Kotlin extension.
5. **Maven Local drift is the main hazard of separate repos.** A filtered `mavenLocal()`, one version for all kits and republishing
   before every Vesta gate keeps a stale artifact from passing for a green build.
6. **iOS cannot be verified from Linux** and cinterop decides which kits can publish iOS klibs; record what a Mac must do rather than
   assume it works.
7. **Split big test classes at the document boundary.** `DeviceDataSyncServiceTest` split into document-agnostic tests (kit, over synthetic
   note documents) and Vesta's own-document proof; moving it whole would have dragged the app into the kit.
8. **Rust and JNI names are a second hidden API.** A symbol test is the only cheap guard.
9. **Stale generated files hide.** The licence list carried a dependency nothing resolved for the last day of work; regenerating and diffing
   is the check.
