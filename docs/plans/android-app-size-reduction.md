# Plan: shrink the Android app

Written 2026-10-02 so a later session can run it without re-deriving context. "Memory" here means **installed and
download size** (APK and on-device storage), which is what failed: a debug install needed about 650 MB on an emulator
with 644 MB free (`INSTALL_FAILED_INSUFFICIENT_STORAGE`). It does not cover runtime RAM; see "Out of scope".

## Goal

Make the Android app small enough to install comfortably on the emulator and on a phone, and cheap to download, without
changing behaviour. Each work package is independent and ships on its own; do them in the order below (smallest risk
first) and re-measure after each.

## Baseline (measured 2026-10-02, debug build)

| Build | APK size |
|---|---|
| Full debug (`:androidApp:installDebug`: arm64-v8a, armeabi-v7a, x86_64) | **328 MB** |
| x86_64 only (`-Pandroid.injected.build.abi=x86_64`) | **141 MB** |

Contents of the x86_64-only APK (uncompressed → packed):

| What | Size | Where it comes from |
|---|---|---|
| `lib/x86_64/libx0x_ffi.so` | 56 MB → 56 MB | `ffi/x0x-ffi`, built by `scripts/build-android-ffi.sh` into `androidApp/src/main/jniLibs/<abi>/`. Already `release` + thin LTO + `llvm-strip`; arm64 is 50 MB, armv7 38 MB. |
| `lib/x86_64/libant_ffi.so` | 38 MB → 38 MB | The `com.autonomi:ant-android` dependency (`libs.versions.toml`, `ant-android = 0.0.8-rc.1`). |
| `native/**` Windows and macOS Automerge libraries | 34 MB → about 12 MB | The `org.automerge:automerge` JAR bundles every desktop platform's `automerge_jni`. None can run on Android, which uses `org.automerge:androidnative` (`lib/<abi>/libautomerge_jni_*.so`, 6 MB). |
| `classes*.dex` | 45 MB → about 17 MB | Debug build, `isMinifyEnabled = false` (`androidApp/build.gradle.kts`). |
| ML Kit barcode (`libbarhopper_v3.so`, models) | about 7 MB | Barcode scanning. |
| `libsqliteJni.so`, other | about 1 MB | |

The full build is large because every ABI repeats the x0x and Autonomi libraries (about 94 MB per ABI).

## Work packages

### WP1 — Keep desktop Automerge natives out of the APK (about 12 MB packed, trivial)

In `androidApp/build.gradle.kts`, extend `packaging { resources { excludes += … } }` with `"/native/**"`. Check that the
exclusion does not also match anything Android needs: Android's Automerge natives live under `lib/<abi>/`, not
`native/`.

Verify: rebuild, `unzip -l <apk> | grep -c 'native/'` is 0, the app starts, a Home can be created (Automerge works) and
a Pantry item saved.

### WP2 — Build only the ABIs that are needed (about 190 MB off the debug build)

- **Debug/emulator:** document and use `-Pandroid.injected.build.abi=x86_64` (this is what Android Studio does for an
  emulator). Optionally set `X0X_ANDROID_ABIS="x86_64-linux-android"` for `scripts/build-android-ffi.sh` while
  developing on an emulator, which also shortens the Rust build. Note this in the README.
- **Release:** ship per-ABI downloads rather than one fat APK, using an Android App Bundle (`:androidApp:bundleRelease`),
  or `splits { abi { … } }` if an APK is needed. A phone then downloads only its own ABI, about 140 MB instead of 330 MB.
  Drop `armeabi-v7a` entirely if the minimum supported device is 64-bit (decide with the product owner; minSdk is 26).

Verify: `bundletool` (or the Play Console size report) for the per-device size; install the x86_64 build on the emulator.

### WP3 — Shrink the code with R8 for release (about 10–20 MB packed, moderate risk)

Set `isMinifyEnabled = true` and `isShrinkResources = true` for `release` in `androidApp/build.gradle.kts`, and add
keep rules to `proguard-rules.pro` for what reflection or JNI reaches: the JNI bridge (`X0xBridge` in
`EmbeddedX0x.android.kt`), Automerge's JNI classes, kotlinx.serialization models (`HomeJoinMessage`, `X0xModels`,
agent command types), and SQLDelight/androidx.sqlite drivers. Start from the libraries' own consumer rules and add only
what testing shows is missing.

Verify: a release build that is run through the whole flow on a device or emulator: create a Home, pantry add and
sync, invite and join (JNI, serialization), barcode scan, agent command. R8 problems show up as runtime
`ClassNotFoundException` or `NoSuchMethodError`, not at build time, so this package needs a manual pass.

### WP4 — Question the Autonomi library (up to 38 MB per ABI)

`libant_ffi.so` is the largest single item after x0x. Find out what in the app uses it
(`EmbeddedAutonomiClient.android.kt`, `StandaloneAutonomiClient.jvm.kt`) and whether v1 needs it on Android.
Options, from cheapest: (a) confirm it is needed and leave it; (b) move it behind a build flag or a separate dynamic
feature module so the base install does not carry it; (c) ask the `ant-android` maintainers whether a smaller build
exists (it may be unstripped or built with all features). Do not remove it without checking what depends on it.

### WP5 — Look at the x0x library size (56 MB, only if still needed)

It is already release, thin LTO and stripped, so remaining gains are in the Rust build: `lto = "fat"`, `opt-level = "z"`
or `"s"`, `codegen-units = 1`, `panic = "abort"`, and dropping unused x0x features. These trade build time and some speed
for size, and x0x is another project's code, so measure the gain (`ls -l` on the stripped `.so`) before adopting and check
the daemon still behaves under load. Treat as optional.

## Out of scope

- **Runtime memory (RAM).** The emulator is at 2 GB RAM and 228 MB heap; nothing here measured the app's live memory use.
  If that matters, profile it separately (Android Studio profiler, or `adb shell dumpsys meminfo uk.veritaslabs.vesta`).
- **Emulator storage.** Raised separately from 6 GB to 16 GB in `~/.android/avd/Pixel_7a.avd/config.ini` (backup kept as
  `config.ini.bak-before-resize`); it may need a wipe-data to take effect.

## Measuring

After each work package, record the numbers in the table above (or a new column):

```
./gradlew :androidApp:assembleDebug                         # full
./gradlew :androidApp:assembleDebug -Pandroid.injected.build.abi=x86_64
ls -l androidApp/build/outputs/apk/debug/*.apk
unzip -lv <apk> | sort -k1 -n -r | head -25                 # what is big
```

Release numbers need `assembleRelease` or `bundleRelease` with a signing config; compare packed sizes, not the
uncompressed total.

## Order and expected result

WP1 (trivial), then WP2 (biggest for users), then WP3 and WP4 once there is time for a manual regression pass. Expect
roughly: full debug 328 MB → about 316 MB after WP1; a release bundle delivering about 110–130 MB per device after WP1–WP3;
more if WP4 removes or slims `libant_ffi`.
