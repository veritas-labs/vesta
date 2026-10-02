package uk.veritaslabs.vesta.homejoin

import kotlin.io.encoding.Base64

/**
 * What someone who wants to join a Home shows to an admin of it (docs/adr/ADR-015-joining-a-home.md): their device's
 * x0x [agentId] (where the invitation is sent), their [memberId] (their Profile id, which is who they are in a Home,
 * ADR-014) and [name], and an expiry so a stale link can't be used indefinitely. Like a pairing link it carries no
 * secret: the invitation only goes to that agent, in an end-to-end encrypted direct message.
 *
 * Encoded as `vesta://join/<token>`, the payload base64url-encoded.
 */
data class JoinLink(
    val agentId: String,
    val memberId: String,
    val expiresAt: Long,
    val name: String,
) {

    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAt

    fun encode(): String {
        val body = listOf(agentId, memberId, expiresAt.toString(), name).joinToString(Separator)
        return "$Scheme/${Base64.UrlSafe.encode(body.encodeToByteArray())}"
    }

    companion object {
        private const val Scheme = "vesta://join"
        private const val Separator = ":"

        fun decode(raw: String): JoinLink? {
            val token = raw.trim().takeIf { it.startsWith("$Scheme/") }?.substringAfter("$Scheme/") ?: return null
            val decoded = runCatching { Base64.UrlSafe.decode(token) }.getOrNull()?.decodeToString() ?: return null
            // The name goes last and may itself contain the separator.
            val parts = decoded.split(Separator, limit = 4)
            if (parts.size < 4) return null
            val agentId = parts[0].takeIf { it.isNotBlank() } ?: return null
            val memberId = parts[1].takeIf { it.isNotBlank() } ?: return null
            val expiresAt = parts[2].toLongOrNull() ?: return null
            return JoinLink(agentId, memberId, expiresAt, parts[3])
        }
    }
}
