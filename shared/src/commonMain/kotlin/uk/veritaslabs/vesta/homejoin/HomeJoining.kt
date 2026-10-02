package uk.veritaslabs.vesta.homejoin

import kotlinx.coroutines.flow.SharedFlow

/** A Home this device has just joined. */
data class JoinedHome(val homeId: String, val name: String)

/** Why an admin's invitation didn't result in a new member. */
enum class InviteFailure { NotAnAdmin, NoSuchHome, LinkExpired, AlreadyAMember, NoResponse }

/** Raised by [HomeJoining.invite] when the invitation fails. */
class InviteException(val reason: InviteFailure) : Exception(reason.name)

/** Someone who opened an invite link (docs/adr/ADR-018-invite-links.md) and is waiting for an admin to approve them. */
data class JoinRequest(
    val homeId: String,
    val memberId: String,
    val name: String,
    val createdAt: Long,
    /** True once an admin approved it and the Home is on its way to them. */
    val approved: Boolean,
)

/** An invite link this user opened that hasn't been answered yet; [denial] is the reason once an admin refused it. */
data class PendingJoin(val homeId: String, val expiresAt: Long, val denial: String?)

/** What came of opening an invite link. */
enum class RedeemResult { Started, Expired, AlreadyInHome }

/** What came of an admin approving or declining a [JoinRequest]. */
enum class RequestResult { Done, NotAnAdmin, NoSuchRequest, InviteNoLongerValid }

/**
 * Joining a Home with a link (docs/adr/ADR-015-joining-a-home.md). The person joining [openLink]s and shows the link;
 * an admin of the Home [invite]s the person who showed it.
 */
interface HomeJoining {
    /** Emits each time this device joins a Home. */
    val joined: SharedFlow<JoinedHome>

    /** Starts waiting for an invitation and returns the link to show the admin. Replaces any link already showing. */
    suspend fun openLink(): JoinLink

    /** Stops waiting for an invitation. */
    fun cancelLink()

    /**
     * Invites the person who showed [link] to [homeId], repeating the invitation until they accept it. Fails with an
     * [InviteException] if this user isn't an admin of the Home, the link has expired or they are already a member, or
     * nobody accepted in time.
     */
    suspend fun invite(homeId: String, link: JoinLink): Result<Unit>

    /** The requests to join [homeId] that an admin hasn't declined, oldest first. */
    suspend fun joinRequests(homeId: String): List<JoinRequest>

    /**
     * Approves [memberId]'s request to join [homeId]: the Home is sent to them, repeatedly if need be, and they are
     * added to the roster once they answer. Only an admin of the Home can, and only while the invite is still valid.
     */
    suspend fun approve(homeId: String, memberId: String): RequestResult

    /** Declines [memberId]'s request to join [homeId], and tells them so. Only an admin of the Home can. */
    suspend fun decline(homeId: String, memberId: String): RequestResult

    /**
     * Opens an invite link: asks the admins it names to let this user into the Home, again every sync round until one
     * answers. Fails if the link has expired or this device already has the Home. The Home arrives on [joined].
     */
    suspend fun redeemInvite(link: InviteLink): RedeemResult

    /** The invite links this user opened that still wait for an answer, or were refused. */
    suspend fun pendingJoins(): List<PendingJoin>

    /** Stops waiting on [homeId]'s invite link, or forgets that it was refused. */
    suspend fun cancelPendingJoin(homeId: String)
}
