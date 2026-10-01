package uk.veritaslabs.vesta.devicepairing

import kotlin.io.encoding.Base64

/**
 * The payload encoded into a device pairing link/QR code
 * (`docs/adr/ADR-012-device-sync-over-x0x-group.md`): the new device's own x0x
 * agent id (where the group invite is sent) and its [deviceName], plus an expiry
 * so a stale, unused link can't be redeemed indefinitely. Carries no secret: the
 * invite that grants access travels afterwards, in an end-to-end encrypted x0x
 * direct message that only the holder of that agent can read.
 *
 * Encoded as `vesta://pair/<token>`, where `<token>` is this payload,
 * base64url-encoded — opaque to anything that isn't decoding it, rather than
 * a plain query string exposing `agentId`/`expiresAt` directly.
 *
 * This is deliberately not a cr-sqlite CRDT model — see
 * `shared/src/commonMain/sqldelight/uk/veritaslabs/vesta/db/DevicePairingLink.sq`,
 * its local-only backing table, for why (a pairing link only ever matters to
 * the single device that generated it, before that device has joined any
 * DeviceSync group to replicate to).
 */
data class PairingLink(
    val agentId: String,
    val expiresAt: Long,
    val deviceName: String? = null,
) {

    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAt

    fun encode(): String {
        val body = listOfNotNull(agentId, expiresAt.toString(), deviceName).joinToString(Separator)
        return "$Scheme/${Base64.UrlSafe.encode(body.encodeToByteArray())}"
    }

    companion object {
        private const val Scheme = "vesta://pair"
        private const val Separator = ":"

        fun decode(raw: String): PairingLink? {
            val token = raw.takeIf { it.startsWith("$Scheme/") }?.substringAfter("$Scheme/") ?: return null
            val decoded = runCatching { Base64.UrlSafe.decode(token) }.getOrNull()?.decodeToString() ?: return null

            // The name goes last and may itself contain the separator.
            val parts = decoded.split(Separator, limit = 3)
            if (parts.size < 2) return null
            val agentId = parts[0].takeIf { it.isNotBlank() } ?: return null
            val expiresAt = parts[1].toLongOrNull() ?: return null
            return PairingLink(agentId, expiresAt, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
        }
    }
}
