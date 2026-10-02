package uk.veritaslabs.vesta.homejoin

import kotlinx.coroutines.flow.SharedFlow

/** A Home this device has just joined. */
data class JoinedHome(val homeId: String, val name: String)

/** Why an admin's invitation didn't result in a new member. */
enum class InviteFailure { NotAnAdmin, NoSuchHome, LinkExpired, AlreadyAMember, NoResponse }

/** Raised by [HomeJoining.invite] when the invitation fails. */
class InviteException(val reason: InviteFailure) : Exception(reason.name)

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
}
