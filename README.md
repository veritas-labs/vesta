This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform, you
  need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications. It contains
  several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name. For
      example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls. Similarly, if you want
      to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

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

`EmbeddedX0x` (package `uk.veritaslabs.vesta.x0x`, present in the Android
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

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…