# Vesta

**Vesta** is a modular, local-first household app: a shared pantry and shopping list, a cookbook, a meal planner, tasks
and chores, and a calendar. Pick the tools you need and ignore the rest. There is no account and no central server.
Your home lives on your devices and they sync directly with each other, peer to peer, over
[x0x](https://github.com/saorsa-labs/x0x).

It is built with Kotlin Multiplatform and Compose Multiplatform for Android and desktop (Linux, with macOS expected to
work). Every feature can be used by people through the app and by AI agents through x0x (see
[Agents](#ai-agents)).

> [!WARNING]
> **Vesta is alpha, experimental software.** Features are unfinished, parts of the app have only been tested on a
> few devices, and data formats and the sync protocol may change between releases, possibly without a migration path.
> Expect bugs. Don't rely on it as the only copy of anything important. Bug reports are very welcome (see
> [Contributing](CONTRIBUTING.md)).

> [!CAUTION]
> **x0x uses a lot of network data.** On Android, Vesta runs a complete x0x node inside the app. It keeps peer
> connections open and exchanges gossip and sync traffic in the background, so it can use a noticeable amount of mobile
> data and battery. If you are on a metered or limited plan, use Wi-Fi, and keep an eye on usage in Android's
> data-usage settings, at least until you know how it behaves for you. We have not yet measured or optimised this.

## What's in it

- **Homes**: a Home is a household (or any group). Members join by invite link; each Home syncs between its members'
  devices and between your own devices.
- **Pantry and shopping list**: stock counts per item, a shared list, "add checked to Pantry".
- **Cookbook**, **meal planner**, **tasks and chores**, **calendar** (a private calendar per Home, synced only
  between your own devices).
- **Sync without being online together**: optional always-on hubs ([Run an always-on hub](docs/product/RUN-A-HUB.md)).
- **Assistants**: scoped, per-Home permissions for external AI agents.

See [Vision](docs/product/VISION.md), [Core experience](docs/product/CORE-EXPERIENCE.md) and
[Syncing and joining](docs/product/SYNC-AND-JOINING.md) for the thinking behind it, and [docs/adr](docs/adr) for the
design decisions.

## Platform support

| Platform | Status | Notes |
|---|---|---|
| Android 8.0+ (API 26) | Supported, prebuilt APK | x0x is embedded in the app. |
| Linux (x86_64) | Supported, prebuilt `.deb` / `.rpm` | Needs a separate `x0x` install (see below). |
| macOS | Should work, **untested** | The desktop app is the same JVM code and the build is configured for a `.dmg`, but it has not been run or released. Build from source. |
| Windows | Build from source, **not working out of the box** | The desktop app does not yet look for the x0x daemon in Windows locations, so it will not find it. Contributions welcome. |
| iOS | **Not supported** | The code and an Xcode project exist from the Kotlin Multiplatform template, but the iOS app is not built, tested or maintained, and Automerge, which Vesta needs, has no iOS binding. |

## Install

### Android

1. Download `vesta-<version>.apk` from the [latest release](../../releases/latest).
2. Open it on your phone. Android will ask you to allow installs from your browser or file manager ("install unknown
   apps"); this is needed because the APK is not from the Play Store.
3. Open Vesta. It starts its own x0x node, so there is nothing else to install.

Read the data usage warning at the top of this page first.

### Linux

Vesta on the desktop talks to an **x0x daemon that you install and run yourself**; it does not bundle one.

1. Install x0x from [the x0x project](https://github.com/saorsa-labs/x0x) and start it:

   ```
   x0x start
   ```

   The app finds the daemon through its data directory (`~/.local/share/x0x`, or whatever `X0X_DATA_DIR` points at), so
   start x0x first or leave it running. Use **x0x 0.46.3 or newer**. If the app says it can't find x0x, this is why.

2. Install Vesta from the [latest release](../../releases/latest):

   Debian, Ubuntu and derivatives:

   ```
   sudo apt install ./vesta_<version>_amd64.deb
   ```

   Fedora, RHEL, openSUSE and derivatives:

   ```
   sudo dnf install ./vesta-<version>-1.x86_64.rpm
   ```

3. Start **Vesta** from your application menu.

### macOS, Windows, or anything else

Build from source (below). On macOS you can produce a disk image with `./gradlew :desktopApp:packageDmg`; the x0x
daemon has to be installed and running as on Linux. On Windows, see the table above.

## Build from source

You will need:

- **JDK 17 or newer** (Gradle fetches the JVM 11 toolchain it needs).
- **The Android SDK** (set `ANDROID_HOME`, or put `sdk.dir=/path/to/Sdk` in a `local.properties` file). Gradle
  configures the Android modules even if you only want the desktop app.
- **Vesta's kits**: the libraries in veritas-kits that Vesta depends
  on are not on a public Maven repository. Clone that repository and publish it to your local Maven repository first,
  and again whenever you update it:

  ```
  git clone <veritas-kits repository>
  cd veritas-kits && ./gradlew publishToMavenLocal
  ```

- **For the Android app only**: Rust (<https://rustup.rs>), the Android NDK, and the Rust cross-targets, as described
  in [x0x networking](#x0x-networking-embedded-on-mobile-daemon-on-desktop) below. The first Android build compiles the
  whole x0x tree per ABI, which takes about 10 minutes per ABI.

Then:

```
./gradlew :desktopApp:run                 # desktop app (needs x0x running)
./gradlew :androidApp:assembleDebug       # Android debug APK
./gradlew :desktopApp:packageDeb          # Linux .deb (run on Linux)
./gradlew :desktopApp:packageDmg          # macOS .dmg (run on macOS)
./gradlew :desktopApp:packageMsi          # Windows .msi (run on Windows)
```

Packages are written to `desktopApp/build/compose/binaries/main/`.

For desktop hot reload while developing: `./gradlew :desktopApp:hotRun --auto`.

### Running tests

- Desktop (JVM): `./gradlew :shared:jvmTest`
- Android host tests: `./gradlew :shared:testAndroidHostTest`
- Both: `./gradlew :shared:check`

Feature behaviour is specified in [docs/testing/acceptance-tests.md](docs/testing/acceptance-tests.md).

### Compose previews

Every screen in [commonMain](./shared/src/commonMain/kotlin) carries a light/dark × phone/tablet
`@Preview` matrix. Keep IntelliJ's **Fast Preview** turned off and use the preview's *Build &
Refresh* — Fast Preview recompiles Kotlin without re-running the Gradle compose-resources tasks,
which desynchronises code from resources and leaves stale classes in `/tmp/overlay*` that outlive
`clean` and Invalidate Caches.

When a preview fails to render, see
[Troubleshooting Compose previews](./docs/troubleshooting/compose-previews.md) — it covers the known
failure modes (`MissingResourceException`, `CompositionDataTree.mapTree` NPE, AAR-metadata
`compileSdk` failures, material3/Compose version skew) and how to diagnose a new one.

## Project layout

```
androidApp/   Android app (entry point, packaging, embedded x0x .so files)
desktopApp/   Desktop (JVM) app and native packaging
shared/       Shared Kotlin Multiplatform code: UI, app logic, platform source sets
iosApp/       Leftover iOS entry point (unsupported)
ffi/x0x-ffi/  Rust crate that embeds the x0x daemon for mobile
skills/       Agent skill for driving Vesta over x0x
docs/         Product docs, ADRs, design notes, test specs
branding/     Logo and brand assets (not Apache-licensed, see TRADEMARK.md)
```

## AI agents

Vesta can be driven by an external AI agent over x0x direct messages (list Homes, add to the Pantry, and so on), with an
explicit in-app approval step and per-Home scoped grants. See [skills/vesta](skills/vesta/SKILL.md) and
[ADR-009](docs/adr/ADR-009-agent-commands-over-x0x.md) / [ADR-027](docs/adr/ADR-027-assistants.md).

## Contributing

Contributions are welcome, whether bug reports, fixes, docs, or ports to other platforms. Read
[CONTRIBUTING.md](CONTRIBUTING.md) first. Security issues: see [Security](#security).

## Security

Vesta is alpha software and has not been independently audited. Don't put anything in it you can't afford to
expose. Please report vulnerabilities privately through GitHub's "Report a vulnerability" button on the Security tab rather than in a public issue.

---

## x0x networking (embedded on mobile, daemon on desktop)

Vesta uses [x0x](https://github.com/saorsa-labs/x0x) — the post-quantum
agent gossip network — for computer-to-computer networking. The integration
model differs by platform:

- **Android / iOS**: the full x0x daemon is **embedded in-process**. It runs
  its REST + WebSocket + SSE API on an ephemeral loopback port and the app
  talks HTTP to it, exactly as the `x0x` CLI talks to a standalone `x0xd`.
- **Desktop**: the app connects to a standalone `x0xd` daemon's REST API
  (`http://127.0.0.1:12700`); nothing is embedded and no Rust is built for
  the JVM target.

### How the mobile embedding works

```
ffi/x0x-ffi/                  Rust crate: wraps x0x::server::{serve, DaemonConfig}
  ├─ src/lib.rs               C ABI (x0x_ffi_start/_port/_api_token/_shutdown/_last_error)
  │                           + JNI shims for Android (X0xBridge)
  ├─ include/x0x_ffi.h        C header (used by Kotlin/Native cinterop on iOS)
  └─ Cargo.toml               depends on the published `x0x` crate (0.40.x)

scripts/build-android-ffi.sh  cargo build per Android ABI (NDK clang) →
                              androidApp/src/main/jniLibs/<abi>/libx0x_ffi.so
scripts/build-ios-ffi.sh      cargo rustc --crate-type staticlib per iOS triple →
                              ffi/x0x-ffi/dist/ios/<triple>/libx0x_ffi.a

shared/src/androidMain/...    EmbeddedX0x (JNI; loads libx0x_ffi.so)
shared/src/iosMain/...        EmbeddedX0x (cinterop; links libx0x_ffi.a)
```

Wiring:

- Android: `:androidApp:preBuild` runs the Rust build; the `.so` files are
  packaged from `androidApp/src/main/jniLibs` (AGP's default jniLibs dir).
- iOS: Gradle generates the cinterop bindings from the C header (no native
  lib needed at that stage); the Xcode project has a "Build x0x FFI" script
  phase that builds `libx0x_ffi.a` and `OTHER_LDFLAGS[sdk=…]` links it into
  the app (device and simulator separately). **The iOS FFI build requires
  macOS** — the C parts of the x0x dependency tree (aws-lc-sys, ring,
  bundled sqlite) need Apple's SDK — which is fine, since the iOS app is
  built in Xcode on a Mac anyway.

### Prerequisites (one-time, for mobile builds)

- Rust via <https://rustup.rs>
- Android NDK (e.g. `sdkmanager "ndk;29.0.14033849"` — whatever
  `ANDROID_NDK_HOME` points at)
- Rust cross-targets:

  ```
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android \
                    aarch64-apple-ios aarch64-apple-ios-sim
  ```

### Using it from Kotlin

`EmbeddedX0x` (package `uk.veritaslabs.kits.x0x`, present in the Android
and iOS source sets of `:shared`) starts the daemon and exposes its endpoint:

```kotlin
val x0x = EmbeddedX0x.start(
    dataDir = context.filesDir.resolve("x0x").path,     // app-private storage
    identityDir = context.filesDir.resolve("x0x-identity").path,
) ?: error("x0x failed to start: ${EmbeddedX0x.lastError()}")

// x0x.baseUrl   -> "http://127.0.0.1:<port>"
// x0x.apiToken  -> durable bearer token for the local REST API
// e.g. GET "$baseUrl/health" with header "Authorization: Bearer ${x0x.apiToken}"

x0x.shutdown() // graceful: stops HTTP/SSE, gossip runtime and QUIC endpoint
```

`start` blocks until the API listener is bound (first run also generates the
agent's ML-DSA-65 identity) — call it off the main thread. Embedding policies
(x0x ADR-0057): self-update is disabled in-process, and the host owns the
filesystem — always pass directories inside the app's private storage.

### Notes

- The first mobile build compiles the whole x0x tree per ABI (allow ~10 min
  per ABI); subsequent builds are incremental no-ops. The `.so` is ~45 MB
  per ABI — it is a complete daemon (MLS, QUIC, sqlite, post-quantum crypto,
  HTTP server), stripped on staging.
- Environment knobs: `X0X_PROFILE` (cargo profile, default `release`),
  `X0X_ANDROID_ABIS`, `X0X_API_LEVEL` (default 24 = minSdk), `X0X_STRIP`,
  `ANDROID_NDK_HOME`. See the script headers.
- The REST/WS/SSE surface is documented in the
  [x0x API reference](https://github.com/saorsa-labs/x0x/blob/main/docs/api-reference.md).

---

## Local database (SQLDelight)

> **cr-sqlite has been removed.** It was vendored as a loadable SQLite extension for CRDT-table sync, but that
> sync design was retired by [ADR-005](./docs/adr/ADR-005-home-automerge-document-over-x0x-group.md) in favor of
> Automerge `Document`s. Confirmed 2026-09-26 that nothing in the app called `crsql_as_crr` or read
> `crsql_changes` — the extension was loading at startup, on every platform, doing nothing — so the vendored
> binaries, the extension-loading code, and its smoke test were deleted outright rather than left in place.
> Automerge `Document`s now persist as plain SQLite BLOBs in this same database, and free-text search runs as an
> FTS5 projection — both designed in
> [ADR-007](./docs/adr/ADR-007-sqlite-persistence-and-search.md).

`KitsDatabase` (schema in [shared/src/commonMain/sqldelight](./shared/src/commonMain/sqldelight),
driver setup in `shared/src/*Main/kotlin/.../db/DatabaseDriverFactory.*.kt`) runs on
[androidx.sqlite's bundled SQLite driver](https://developer.android.com/kotlin/multiplatform/sqlite)
via the [sqldelight-androidx-driver](https://github.com/eygraber/sqldelight-androidx-driver)
adapter — the same self-compiled SQLite build on Android, iOS and desktop, rather than each
platform's own system SQLite.

Because the driver is suspending, SQLDelight is configured with `generateAsync = true` —
generated `SELECT`s need `awaitAsList()`/`awaitAsOne()`/`awaitAsOneOrNull()` (from
`app.cash.sqldelight:async-extensions`), and mutating statements (`INSERT`/`UPDATE`/`DELETE`)
are themselves `suspend` functions.


---

## Licence

The source code is released under the [Apache License 2.0](LICENSE); third-party
attributions are in [NOTICE](NOTICE). The name **Vesta**, the logo and the other
brand assets in [branding/](branding/) are not covered by that licence — see
[TRADEMARK.md](TRADEMARK.md) for what forks may and may not do.

The libraries Vesta ships with, and their licences, are listed in the app under
**Settings → Open source licences**. That list is generated, so after changing a
dependency (including the Rust crates in `ffi/x0x-ffi`) regenerate and commit it:

```
./gradlew generateLicenses
```
