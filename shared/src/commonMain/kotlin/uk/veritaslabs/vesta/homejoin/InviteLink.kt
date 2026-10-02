package uk.veritaslabs.vesta.homejoin

import kotlin.io.encoding.Base64

/**
 * What an admin shares to let someone join a Home later (docs/adr/ADR-018-invite-links.md): the [homeId], a random
 * [secret] only the holder of the link knows, an expiry, and the x0x agent ids of the Home's admin devices when the link
 * was made ([adminAgentIds], at most [MaxAdminAgents]), where the joiner sends its request, and the Home's [homeName]
 * so the joiner can tell which Home it is waiting on. Unlike a [JoinLink] it
 * carries a secret, so it is a bearer token: an admin still has to approve each request.
 *
 * Encoded as `vesta://invite/<token>`, the payload base64url-encoded.
 */
data class InviteLink(
    val homeId: String,
    val secret: String,
    val expiresAt: Long,
    val adminAgentIds: List<String>,
    val homeName: String,
) {

    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAt

    fun encode(): String {
        val body = listOf(homeId, secret, expiresAt.toString(), adminAgentIds.joinToString(AgentSeparator), homeName)
            .joinToString(Separator)
        return "$Scheme/${Base64.UrlSafe.encode(body.encodeToByteArray())}"
    }

    companion object {
        const val MaxAdminAgents = 5

        private const val Scheme = "vesta://invite"
        private const val Separator = ":"
        private const val AgentSeparator = ","

        fun decode(raw: String): InviteLink? {
            val token = raw.trim().takeIf { it.startsWith("$Scheme/") }?.substringAfter("$Scheme/") ?: return null
            val decoded = runCatching { Base64.UrlSafe.decode(token) }.getOrNull()?.decodeToString() ?: return null
            // The name goes last and may itself contain the separator.
            val parts = decoded.split(Separator, limit = 5)
            if (parts.size != 5) return null
            val homeId = parts[0].takeIf { it.isNotBlank() } ?: return null
            val secret = parts[1].takeIf { it.isNotBlank() } ?: return null
            val expiresAt = parts[2].toLongOrNull() ?: return null
            val agents = parts[3].split(AgentSeparator).filter { it.isNotBlank() }
            if (agents.isEmpty() || agents.size > MaxAdminAgents) return null
            return InviteLink(homeId, secret, expiresAt, agents, homeName = parts[4])
        }
    }
}
