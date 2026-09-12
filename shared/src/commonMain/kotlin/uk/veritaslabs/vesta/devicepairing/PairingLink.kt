package uk.veritaslabs.vesta.devicepairing

import kotlin.io.encoding.Base64

/**
 * The payload encoded into a device pairing link/QR code
 * (`docs/adr/ADR-003-device-sync.md` "Device enrollment"): the new device's
 * own agent public key, plus an expiry so a stale, unscanned link can't be
 * redeemed indefinitely. Carries no private key material — per ADR-003, the
 * private key never leaves the device that generated it.
 *
 * Encoded as `vesta://pair/<token>`, where `<token>` is this payload,
 * base64url-encoded — opaque to anything that isn't decoding it, rather than
 * a plain query string exposing `publicKeyHex`/`expiresAt` directly.
 *
 * This is deliberately not a cr-sqlite CRDT model — see
 * `shared/src/commonMain/sqldelight/uk/veritaslabs/vesta/db/DevicePairingLink.sq`,
 * its local-only backing table, for why (a pairing link only ever matters to
 * the single device that generated it, before that device has joined any
 * DeviceSync group to replicate to).
 */
data class PairingLink(
    val publicKeyHex: String,
    val expiresAt: Long,
) {

    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAt

    fun encode(): String = "$Scheme/${Base64.UrlSafe.encode("$publicKeyHex$Separator$expiresAt".encodeToByteArray())}"

    companion object {
        private const val Scheme = "vesta://pair"
        private const val Separator = ":"

        fun decode(raw: String): PairingLink? {
            val token = raw.takeIf { it.startsWith("$Scheme/") }?.substringAfter("$Scheme/") ?: return null
            val decoded = runCatching { Base64.UrlSafe.decode(token) }.getOrNull()?.decodeToString() ?: return null

            val parts = decoded.split(Separator, limit = 2)
            if (parts.size != 2) return null
            val publicKeyHex = parts[0].takeIf { it.isNotBlank() } ?: return null
            val expiresAt = parts[1].toLongOrNull() ?: return null
            return PairingLink(publicKeyHex, expiresAt)
        }
    }
}
