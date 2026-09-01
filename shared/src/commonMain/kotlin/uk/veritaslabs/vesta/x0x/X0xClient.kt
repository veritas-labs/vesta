package uk.veritaslabs.vesta.x0x

/**
 * Minimal client for the local x0x REST API (loopback, bearer-token
 * protected). One implementation per platform:
 *
 * * Android / iOS — talks to the daemon embedded in this process
 *   ([EmbeddedX0x]);
 * * Desktop (JVM) — talks to the standalone `x0xd` daemon (port + token read
 *   from the standard x0x data directory).
 */
interface X0xClient {

    /** What this client talks to, for display, e.g. `x0xd · 127.0.0.1:12700`. */
    val endpoint: String

    /** `GET /health`; null when the daemon is unreachable or the body is unrecognisable. */
    suspend fun health(): X0xHealth?
}

expect fun createX0xClient(): X0xClient

/**
 * Performs a blocking-free HTTP GET and returns the response body, or null
 * on connection failure, non-200 status or timeout.
 */
internal expect suspend fun httpGet(url: String, bearerToken: String?): String?
