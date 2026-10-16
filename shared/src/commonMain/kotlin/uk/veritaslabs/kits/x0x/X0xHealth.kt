package uk.veritaslabs.kits.x0x

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class X0xHealthDto(
    val ok: Boolean? = null,
    val status: String? = null,
    val version: String? = null,
    val peers: Int? = null,
    val send_ready_peers: Int? = null,
    val uptime_secs: Long? = null,
)

/**
 * The response of `GET /health` on an x0x daemon — the REST API the embedded
 * (mobile) and standalone (desktop) daemons both serve.
 */
data class X0xHealth(
    val ok: Boolean,
    val status: String,
    val version: String,
    val peers: Int,
    val sendReadyPeers: Int,
    val uptimeSecs: Long,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses a `/health` body. Returns null when the payload is not a
         * recognisable health response (so callers can treat it as
         * "unreachable"), and defaults fields that are merely absent.
         */
        fun parse(body: String): X0xHealth? {
            val dto = runCatching { json.decodeFromString<X0xHealthDto>(body) }.getOrNull()
                ?: return null
            val ok = dto.ok ?: return null
            return X0xHealth(
                ok = ok,
                status = dto.status ?: "unknown",
                version = dto.version ?: "?",
                peers = dto.peers ?: 0,
                sendReadyPeers = dto.send_ready_peers ?: 0,
                uptimeSecs = dto.uptime_secs ?: 0,
            )
        }
    }
}
