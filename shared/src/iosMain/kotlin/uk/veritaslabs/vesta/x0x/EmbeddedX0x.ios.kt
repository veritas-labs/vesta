@file:OptIn(ExperimentalForeignApi::class)

package uk.veritaslabs.vesta.x0x

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import x0x.ffi.x0x_ffi_api_token
import x0x.ffi.x0x_ffi_last_error
import x0x.ffi.x0x_ffi_port
import x0x.ffi.x0x_ffi_shutdown
import x0x.ffi.x0x_ffi_start

/**
 * C-interop surface of the embedded x0x daemon.
 *
 * `libx0x_ffi.a` (static library built from `ffi/x0x-ffi` by
 * `scripts/build-ios-ffi.sh`, run automatically by the Xcode "Build x0x FFI"
 * build phase) wraps `x0x::server::serve` behind the C ABI in
 * `ffi/x0x-ffi/include/x0x_ffi.h`; cinterop bindings are generated from that
 * header (see `shared/build.gradle.kts`), and Xcode links the archive into
 * the app (OTHER_LDFLAGS[sdk=…] in the iosApp project).
 */
private object X0xBridge {
    /** Returns a handle (> 0), or 0 on failure (see [nativeLastError]). */
    fun nativeStart(dataDir: String, identityDir: String?): ULong =
        // const char* parameters map to String? — cinterop handles the
        // NUL-terminated conversion.
        x0x_ffi_start(dataDir, identityDir)

    /** Bound loopback port of the REST API, or 0 for an unknown handle. */
    fun nativePort(handle: ULong): Int = x0x_ffi_port(handle)

    /** Durable API bearer token, or null for an unknown handle. */
    fun nativeApiToken(handle: ULong): String? {
        val needed = x0x_ffi_api_token(handle, null, 0u)
        if (needed < 0) return null
        return memScoped {
            val buf = allocArray<ByteVar>(needed)
            val written = x0x_ffi_api_token(handle, buf, needed.toUInt())
            if (written < 0) null else buf.toKString()
        }
    }

    /** 0 on graceful shutdown, -1 unknown handle, -2 supervisor error. */
    fun nativeShutdown(handle: ULong): Boolean = x0x_ffi_shutdown(handle) == 0

    /** Last error recorded on this thread, or null. */
    fun nativeLastError(): String? = x0x_ffi_last_error()?.toKString()
}

/**
 * An x0x daemon running embedded in this process.
 *
 * x0x — the post-quantum agent gossip network — runs its full REST +
 * WebSocket + SSE API on an ephemeral loopback port; talk to it over HTTP
 * exactly as an external `x0xd` would be driven. Embedding policies
 * (x0x ADR-0057): self-update is disabled in-process, and the host owns the
 * filesystem — data and identity directories must live under the app's
 * private storage.
 *
 * [start] blocks until the API listener is bound (first run also generates
 * the agent's ML-DSA-65 identity); call it from a background thread.
 *
 * @param dataDir persistent state directory (stores, api token). Created if
 *   missing.
 * @param identityDir directory for the agent identity key; null keeps the
 *   identity under [dataDir].
 */
class EmbeddedX0x private constructor(val handle: ULong) {

    /** Bound loopback port of the REST API. */
    val port: Int = X0xBridge.nativePort(handle)

    /** Durable bearer token for the local REST API (`Authorization: Bearer …`). */
    val apiToken: String = requireNotNull(X0xBridge.nativeApiToken(handle)) {
        "embedded x0x daemon lost its API token (handle $handle)"
    }

    /** Base URL of the local REST + WebSocket + SSE API. */
    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    /** Graceful shutdown; stops HTTP/SSE, background tasks, gossip and QUIC. */
    fun shutdown(): Boolean = X0xBridge.nativeShutdown(handle)

    companion object {
        /**
         * Start an embedded daemon. Returns null on failure — the cause is
         * available from [lastError] on the same thread.
         */
        fun start(dataDir: String, identityDir: String? = null): EmbeddedX0x? {
            require(dataDir.isNotBlank()) { "dataDir must not be blank" }
            val handle = X0xBridge.nativeStart(dataDir, identityDir)
            if (handle == 0UL) return null
            return EmbeddedX0x(handle)
        }

        /** Last error recorded on this thread, or null. */
        fun lastError(): String? = X0xBridge.nativeLastError()
    }
}
