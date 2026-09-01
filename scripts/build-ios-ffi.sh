#!/usr/bin/env bash
# Build libx0x_ffi.a (static) for iOS and stage it under
# ffi/x0x-ffi/dist/ios/<triple>/ for the shared module's cinterop and the
# Xcode app's linker (OTHER_LDFLAGS).
#
# The library is built as a static archive — `cargo rustc --crate-type
# staticlib` — so no Apple SDK/linker is required and the script works on
# Linux CI hosts as well as macOS.
#
# Usage: scripts/build-ios-ffi.sh [--platform device|simulator|all]
# Environment overrides:
#   X0X_PROFILE  cargo profile (default: release)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CRATE_DIR="$ROOT/ffi/x0x-ffi"
DIST_DIR="$CRATE_DIR/dist/ios"
PROFILE="${X0X_PROFILE:-release}"

command -v cargo >/dev/null || { echo "error: cargo not found — install Rust via https://rustup.rs" >&2; exit 1; }

# The x0x dependency tree compiles C code (aws-lc-sys, ring, bundled sqlite)
# through the host C compiler, which for Apple targets requires Apple's SDK
# (xcrun). That exists only on macOS — building the iOS FFI from Linux is not
# supported. The Xcode "Build x0x FFI" phase runs this script automatically
# on the Mac; from Linux, copy ffi/x0x-ffi/dist/ios over from a Mac build.
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "error: building the iOS x0x FFI requires macOS (Apple SDK needed for" >&2
  echo "       the C dependencies in the x0x tree). Run this on the Mac — the" >&2
  echo "       Xcode 'Build x0x FFI' build phase does it automatically — or copy" >&2
  echo "       ffi/x0x-ffi/dist/ios/ from a Mac." >&2
  exit 1
fi

PLATFORM=all
while [[ $# -gt 0 ]]; do
  case "$1" in
    --platform) PLATFORM="$2"; shift 2 ;;
    *) echo "usage: $0 [--platform device|simulator|all]" >&2; exit 1 ;;
  esac
done

TRIPLES=()
case "$PLATFORM" in
  device)     TRIPLES+=(aarch64-apple-ios) ;;
  simulator)  TRIPLES+=(aarch64-apple-ios-sim) ;;
  all)        TRIPLES+=(aarch64-apple-ios aarch64-apple-ios-sim) ;;
  *) echo "error: unknown platform '$PLATFORM' (device|simulator|all)" >&2; exit 1 ;;
esac

for triple in "${TRIPLES[@]}"; do
  rustup target list --installed | grep -qx "$triple" || {
    echo "error: Rust target '$triple' not installed. Run: rustup target add $triple" >&2
    exit 1
  }

  echo "==> building x0x-ffi for $triple (profile $PROFILE)"
  (
    cd "$CRATE_DIR"
    # staticlib only: skips the cdylib flavor, which would require an Apple
    # linker + SDK (unavailable on Linux).
    cargo rustc --target "$triple" --profile "$PROFILE" --crate-type staticlib
  )

  mkdir -p "$DIST_DIR/$triple"
  cp "$CRATE_DIR/target/$triple/$PROFILE/libx0x_ffi.a" "$DIST_DIR/$triple/libx0x_ffi.a"
  echo "    staged $DIST_DIR/$triple/libx0x_ffi.a"
done

echo "==> done: iOS x0x FFI libraries in $DIST_DIR"
