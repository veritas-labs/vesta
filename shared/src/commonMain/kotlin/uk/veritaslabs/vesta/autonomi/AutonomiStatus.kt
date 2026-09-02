package uk.veritaslabs.vesta.autonomi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class AutonomiHealthDto(
    val status: String? = null,
    val network: String? = null,
    val version: String? = null,
    @SerialName("uptime_seconds") val uptimeSeconds: Long? = null,
)

/**
 * Connection status of this app's Autonomi client.
 *
 * The embedded Android client has no `/health` endpoint to poll — [connected]
 * there just reflects whether the in-process client reached the network,
 * with [version] and [uptimeSecs] left null. Desktop drives a standalone
 * `antd` daemon and parses its `GET /health` response, which carries all
 * four fields.
 */
data class AutonomiStatus(
    val connected: Boolean,
    val network: String,
    val version: String? = null,
    val uptimeSecs: Long? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses an `antd` `/health` body. Null when unrecognisable. */
        fun parse(body: String): AutonomiStatus? {
            val dto = runCatching { json.decodeFromString<AutonomiHealthDto>(body) }.getOrNull()
                ?: return null
            return AutonomiStatus(
                connected = dto.status == "ok",
                network = dto.network ?: "unknown",
                version = dto.version,
                uptimeSecs = dto.uptimeSeconds,
            )
        }
    }
}
