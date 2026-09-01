#!/usr/bin/env bash
# Build libx0x_ffi.so for Android and stage it into
# androidApp/src/main/jniLibs/<abi>/ (AGP's default jniLibs source set, so
# the .so files are packaged with no extra Gradle wiring). Invoked
# automatically from :androidApp:preBuild; can also be run by hand.
#
# Environment overrides:
#   ANDROID_NDK_HOME (or NDK_HOME / ANDROID_NDK_ROOT)  NDK location
#   X0X_ANDROID_ABIS  space-separated Rust triples
#                     (default: aarch64-linux-android armv7-linux-androideabi x86_64-linux-android)
#   X0X_PROFILE       cargo profile (default: release)
#   X0X_API_LEVEL     Android API level for the NDK clang wrappers (default: 24 = minSdk)
#   X0X_STRIP         1 (default) to llvm-strip the staged .so, 0 to keep symbols
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CRATE_DIR="$ROOT/ffi/x0x-ffi"
# Staged into AGP's default jniLibs location, so no Gradle DSL is needed.
DIST_DIR="$ROOT/androidApp/src/main/jniLibs"
PROFILE="${X0X_PROFILE:-release}"
API_LEVEL="${X0X_API_LEVEL:-24}"
ABIS="${X0X_ANDROID_ABIS:-aarch64-linux-android armv7-linux-androideabi x86_64-linux-android}"
DO_STRIP="${X0X_STRIP:-1}"

command -v cargo >/dev/null || { echo "error: cargo not found — install Rust via https://rustup.rs" >&2; exit 1; }

# --- locate the NDK ---------------------------------------------------------
ndk_dir="${ANDROID_NDK_HOME:-${NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
if [[ -z "$ndk_dir" || ! -d "$ndk_dir" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
  if [[ -d "$sdk/ndk" ]]; then
    ndk_dir="$(ls -d "$sdk"/ndk/* 2>/dev/null | sort -V | tail -1)"
  else
    ndk_dir=""
  fi
fi
[[ -n "$ndk_dir" && -d "$ndk_dir" ]] || {
  echo "error: Android NDK not found. Install it (e.g. 'sdkmanager \"ndk;29.0.14033849\"') or set ANDROID_NDK_HOME." >&2
  exit 1
}

case "$(uname -s)" in
  Linux*)  HOST_TAG=linux-x86_64 ;;
  Darwin*) HOST_TAG=darwin-$(uname -m) ;;
  *) echo "error: unsupported host OS $(uname -s)" >&2; exit 1 ;;
esac
TOOLCHAIN="$ndk_dir/toolchains/llvm/prebuilt/$HOST_TAG/bin"
[[ -d "$TOOLCHAIN" ]] || { echo "error: NDK toolchain not found at $TOOLCHAIN" >&2; exit 1; }

# --- build each ABI ---------------------------------------------------------
for triple in $ABIS; do
  case "$triple" in
    aarch64-linux-android)       ABI=arm64-v8a;    PREFIX=aarch64-linux-android ;;
    armv7-linux-androideabi)     ABI=armeabi-v7a;  PREFIX=armv7a-linux-androideabi ;;
    i686-linux-android)          ABI=x86;          PREFIX=i686-linux-android ;;
    x86_64-linux-android)        ABI=x86_64;       PREFIX=x86_64-linux-android ;;
    *) echo "error: unknown Android Rust triple '$triple'" >&2; exit 1 ;;
  esac

  rustup target list --installed | grep -qx "$triple" || {
    echo "error: Rust target '$triple' not installed. Run: rustup target add $triple" >&2
    exit 1
  }

  CLANG="$TOOLCHAIN/$PREFIX$API_LEVEL-clang"
  [[ -x "$CLANG" ]] || { echo "error: NDK clang wrapper not found: $CLANG" >&2; exit 1; }

  ENV_US_LC="${triple//-/_}"      # e.g. aarch64_linux_android
  ENV_US="${ENV_US_LC^^}"         # e.g. AARCH64_LINUX_ANDROID

  echo "==> building x0x-ffi for $triple (Android ABI $ABI, profile $PROFILE)"
  (
    export "CARGO_TARGET_${ENV_US}_LINKER=$CLANG"
    export "CC_${ENV_US_LC}=$CLANG"
    export "CXX_${ENV_US_LC}=$TOOLCHAIN/$PREFIX$API_LEVEL-clang++"
    export "AR_${ENV_US_LC}=$TOOLCHAIN/llvm-ar"
    cd "$CRATE_DIR"
    cargo build --target "$triple" --profile "$PROFILE"
  )

  mkdir -p "$DIST_DIR/$ABI"
  cp "$CRATE_DIR/target/$triple/$PROFILE/libx0x_ffi.so" "$DIST_DIR/$ABI/libx0x_ffi.so"
  if [[ "$DO_STRIP" == "1" ]]; then
    "$TOOLCHAIN/llvm-strip" "$DIST_DIR/$ABI/libx0x_ffi.so"
  fi
  echo "    staged $DIST_DIR/$ABI/libx0x_ffi.so"
done

echo "==> done: Android x0x FFI libraries in $DIST_DIR"
