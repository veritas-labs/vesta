package uk.veritaslabs.vesta.x0x

/**
 * JNI surface of the embedded x0x daemon.
 *
 * KEEP IN PACKAGE `uk.veritaslabs.vesta.x0x`. The native library binds these methods by their JNI
 * symbol names (`Java_uk_veritaslabs_vesta_x0x_X0xBridge_*` in `ffi/x0x-ffi/src/lib.rs`), so moving
 * this class to another package without renaming the Rust exports in step makes every call throw
 * `UnsatisfiedLinkError` at run time, which no Kotlin compiler or test run here can see. Rename the
 * package and the Rust exports together when the native packaging is done on real hardware
 * (extraction plan WP26). `X0xBridgeSymbolsTest` guards the two against drifting apart.
 * Everything else of x0x lives in `uk.veritaslabs.kits.x0x`.
 *
 * The native library (`libx0x_ffi.so`, built from `ffi/x0x-ffi` by
 * `scripts/build-android-ffi.sh`, wired into `:androidApp:preBuild`) wraps
 * `x0x::server::serve` behind a C ABI — see `ffi/x0x-ffi/src/lib.rs`.
 */
internal object X0xBridge {
    init {
        System.loadLibrary("x0x_ffi")
    }

    /** Returns a handle (> 0), or 0 on failure (see [nativeLastError]). */
    external fun nativeStart(dataDir: String, identityDir: String?): Long

    /** Bound loopback port of the REST API, or 0 for an unknown handle. */
    external fun nativePort(handle: Long): Int

    /** Durable API bearer token, or null for an unknown handle. */
    external fun nativeApiToken(handle: Long): String?

    /** 0 on graceful shutdown, -1 unknown handle, -2 supervisor error. */
    external fun nativeShutdown(handle: Long): Int

    /** Last error recorded on this thread, or null. */
    external fun nativeLastError(): String?
}
