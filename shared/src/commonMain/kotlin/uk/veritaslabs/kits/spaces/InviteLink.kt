package uk.veritaslabs.kits.spaces

import kotlin.io.encoding.Base64
/**
 * What an admin shares to let someone join a Space later (docs/adr/ADR-018-invite-links.md): the [spaceId], a random
 * [secret] only the holder of the link knows, an expiry, and the x0x agent ids of the Space's admin devices when the link
 * was made ([adminAgentIds], at most [MaxAdminAgents]), where the joiner sends its request, and the Space's [spaceName]
 * so the joiner can tell which Space it is waiting on. Unlike a [JoinLink] it
 * carries a secret, so it is a bearer token: an admin still has to approve each request.
 *
 * Encoded as `vesta://invite/<token>`, the payload base64url-encoded.
 */
data class InviteLink(
    val spaceId: String,
    val secret: String,
    val expiresAt: Long,
    val adminAgentIds: List<String>,
    val spaceName: String,
) {

    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAt

    fun encode(scheme: String = SpacesConfig.DefaultInviteScheme): String {
        val body = listOf(spaceId, secret, expiresAt.toString(), adminAgentIds.joinToString(AgentSeparator), spaceName)
            .joinToString(Separator)
        return "$scheme/${Base64.UrlSafe.encode(body.encodeToByteArray())}"
    }

    companion object {
        const val MaxAdminAgents = 5

        private const val Separator = ":"
        private const val AgentSeparator = ","

        fun decode(raw: String, scheme: String = SpacesConfig.DefaultInviteScheme): InviteLink? {
            val token = raw.trim().takeIf { it.startsWith("$scheme/") }?.substringAfter("$scheme/") ?: return null
            val decoded = runCatching { Base64.UrlSafe.decode(token) }.getOrNull()?.decodeToString() ?: return null
            // The name goes last and may itself contain the separator.
            val parts = decoded.split(Separator, limit = 5)
            if (parts.size != 5) return null
            val spaceId = parts[0].takeIf { it.isNotBlank() } ?: return null
            val secret = parts[1].takeIf { it.isNotBlank() } ?: return null
            val expiresAt = parts[2].toLongOrNull() ?: return null
            val agents = parts[3].split(AgentSeparator).filter { it.isNotBlank() }
            if (agents.isEmpty() || agents.size > MaxAdminAgents) return null
            return InviteLink(spaceId, secret, expiresAt, agents, spaceName = parts[4])
        }
    }
}
