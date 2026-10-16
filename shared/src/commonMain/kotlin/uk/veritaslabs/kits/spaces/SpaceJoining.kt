package uk.veritaslabs.kits.spaces

import kotlinx.coroutines.flow.Flow

/** A Space this device has just joined. */
data class JoinedSpace(val spaceId: String, val name: String)

/** Someone who opened an invite link (docs/adr/ADR-018-invite-links.md) and is waiting for an admin to approve them. */
data class JoinRequest(
    val spaceId: String,
    val memberId: String,
    val name: String,
    val createdAt: Long,
    /** True once an admin approved it and the Space is on its way to them. */
    val approved: Boolean,
)

/** An invite link this user opened that hasn't been answered yet; [denial] is the reason once an admin refused it. */
data class PendingJoin(val spaceId: String, val spaceName: String, val expiresAt: Long, val denial: String?)

/** An invite link an admin made that can still be used; [id] says which one to revoke. */
data class ActiveInvite(val id: String, val expiresAt: Long, val uses: Int, val maxUses: Int)

/** What came of opening an invite link. */
enum class RedeemResult { Started, Expired, AlreadyInSpace }

/** What came of an admin approving or declining a [JoinRequest]. */
enum class RequestResult { Done, NotAnAdmin, NoSuchRequest, InviteNoLongerValid }

/**
 * Joining a Space with an invite link (docs/adr/ADR-018-invite-links.md, ADR-019). An admin makes a link
 * ([createInviteLink]) and approves the requests that come of it ([approve], [decline]); the person joining opens it
 * ([redeemInvite]) and waits.
 */
interface SpaceJoining {
    /** The requests to join [spaceId] that an admin hasn't declined, oldest first. */
    suspend fun joinRequests(spaceId: String): List<JoinRequest>

    /**
     * Approves [memberId]'s request to join [spaceId]: the Space is sent to them, repeatedly if need be, and they are
     * added to the roster once they answer. Only an admin of the Space can, and only while the invite is still valid.
     */
    suspend fun approve(spaceId: String, memberId: String): RequestResult

    /** Declines [memberId]'s request to join [spaceId], and tells them so. Only an admin of the Space can. */
    suspend fun decline(spaceId: String, memberId: String): RequestResult

    /**
     * Opens an invite link: asks the admins it names to let this user into the Space, again every sync round until one
     * answers. Fails if the link has expired or this device already has the Space. The Space arrives on [joined].
     */
    suspend fun redeemInvite(link: InviteLink): RedeemResult

    /** The invite links this user opened that still wait for an answer, or were refused. */
    suspend fun pendingJoins(): List<PendingJoin>

    /** Stops waiting on [spaceId]'s invite link, or forgets that it was refused. */
    suspend fun cancelPendingJoin(spaceId: String)

    /** The requests to join [spaceId] that an admin hasn't declined, re-emitted whenever they change. */
    fun observeJoinRequests(spaceId: String): Flow<List<JoinRequest>>

    /** The invite links this user opened that wait for an answer or were refused, re-emitted whenever they change. */
    fun observePendingJoins(): Flow<List<PendingJoin>>

    /** Makes an invite link for [spaceId] (docs/adr/ADR-018-invite-links.md); null unless this user is an admin of it. */
    suspend fun createInviteLink(spaceId: String): InviteLink?

    /** The invite links of [spaceId] that can still be used; empty unless this user is an admin of it. */
    suspend fun inviteLinks(spaceId: String): List<ActiveInvite>

    /** Revokes the invite link [id] of [spaceId], so nobody new can ask with it. Only an admin of the Space can. */
    suspend fun revokeInviteLink(spaceId: String, id: String)
}
