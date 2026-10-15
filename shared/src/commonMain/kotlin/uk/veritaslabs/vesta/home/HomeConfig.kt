package uk.veritaslabs.vesta.home

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * What an app chooses about the way its Homes are shared: the invite link scheme, the name of the x0x group a Home gets,
 * and how long the invites and hub commands it makes stay valid. The defaults are the values Vesta has always used;
 * [inviteScheme], [groupName] and [mailboxStoreName] are visible to other devices or stored on them, so an existing
 * install must keep them. The lifetimes are policy and may change freely.
 */
data class HomeConfig(
    /** Where an invite link starts (`<scheme>/<token>`). Links already shared use this one. */
    val inviteScheme: String = DefaultInviteScheme,
    /** The name given to the x0x group created for a Home. Groups are never looked up by it. */
    val groupName: String = "Vesta Home",
    /** How long an invite link an admin shares stays usable. */
    val inviteLinkLifetime: Duration = 7.days,
    /** How long the x0x group invite sent to a member asking to join stays valid; the request is repeated every round if it lapses. */
    val joinInviteLifetime: Duration = 1.hours,
    /** How long the group invite in a hub's enrolment commands stays valid. */
    val hubEnrolmentLifetime: Duration = 1.hours,
    /** How often the hub screen makes fresh enrolment commands, so the ones shown are never close to expiry. */
    val hubCommandsRefresh: Duration = 30.minutes,
    /** The name of the Home group's mailbox store. It is stored in the x0x group, so it must not change for existing Homes. */
    val mailboxStoreName: String = "mailbox",
) {
    companion object {
        const val DefaultInviteScheme = "vesta://invite"
    }
}
