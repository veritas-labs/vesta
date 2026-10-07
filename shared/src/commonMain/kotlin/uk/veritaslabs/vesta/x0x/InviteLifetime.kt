package uk.veritaslabs.vesta.x0x

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * How long the group invites Vesta makes stay valid (docs/plans/unpaired-device-gap-plan.md, slice 2b). x0x's default is seven days, and a
 * device that was removed from a group can come back with any invite that is still unused (docs/adr/ADR-020, Spike 9), so each is as short as its use
 * allows, and made for the one agent that will redeem it wherever Vesta knows who that is.
 */
object InviteLifetime {
    /** Pairing a device: the invite is sent at once and the pairing waits for it. */
    val Pairing = 15.minutes

    /** A device of a member asking to join a Home's group: it asks again every round if this one lapses before it arrives. */
    val HomeJoin = 1.hours

    /** The commands shown to enrol a hub, run by a person on another machine. The screen makes new ones after [HubCommandsRefresh]. */
    val HubEnrolment = 1.hours

    /** The hub screen drops commands this old and makes fresh ones, so what it shows is never close to expiry. */
    val HubCommandsRefresh = 30.minutes
}
