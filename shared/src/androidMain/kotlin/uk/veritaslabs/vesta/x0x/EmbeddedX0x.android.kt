package uk.veritaslabs.vesta.x0x

/**
 * JNI surface of the embedded x0x daemon.
 *
 * The native library (`libx0x_ffi.so`, built from `ffi/x0x-ffi` by
 * `scripts/build-android-ffi.sh`, wired into `:androidApp:preBuild`) wraps
 * `x0x::server::serve` behind a C ABI — see `ffi/x0x-ffi/src/lib.rs`.
 */
private object X0xBridge {
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
class EmbeddedX0x private constructor(val handle: Long) {

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
    fun shutdown(): Boolean = X0xBridge.nativeShutdown(handle) == 0

    companion object {
        /**
         * Start an embedded daemon. Returns null on failure — the cause is
         * available from [lastError] on the same thread.
         */
        fun start(dataDir: String, identityDir: String? = null): EmbeddedX0x? {
            require(dataDir.isNotBlank()) { "dataDir must not be blank" }
            val handle = X0xBridge.nativeStart(dataDir, identityDir?.takeIf { it.isNotBlank() })
            if (handle == 0L) return null
            return EmbeddedX0x(handle)
        }

        /** Last error recorded on this thread, or null. */
        fun lastError(): String? = X0xBridge.nativeLastError()
    }
}
