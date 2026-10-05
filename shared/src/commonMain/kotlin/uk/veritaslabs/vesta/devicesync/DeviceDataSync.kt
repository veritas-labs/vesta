package uk.veritaslabs.vesta.devicesync

import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.home.RosterChangeListener
import uk.veritaslabs.vesta.profile.ProfileChangeListener
import uk.veritaslabs.vesta.homejoin.HomeJoining

/**
 * Keeps the user's data in step across their own paired devices (docs/adr/ADR-013-device-data-sync.md). Implemented
 * where the Automerge document layer exists; the app only needs to feed it Home changes and run it.
 */
interface DeviceDataSync {
    /** Pass to the [uk.veritaslabs.vesta.home.HomeRepository] so Home changes made here reach the other devices. */
    val homeChanges: HomeChangeListener

    /** Pass to the [uk.veritaslabs.vesta.home.MemberRepository] so role changes made here reach the Home's other members. */
    val rosterChanges: RosterChangeListener

    /** Pass to the [uk.veritaslabs.vesta.profile.ProfileRepository] so a name change made here reaches the other devices and Homes. */
    val profileChanges: ProfileChangeListener

    /** Joining a Home with an invite link, and inviting someone to one (docs/adr/ADR-018-invite-links.md). */
    val joining: HomeJoining

    /** Asks for a sync round now, e.g. because the app came to the foreground. Returns at once; does nothing until [run] is running. */
    fun syncNow()

    /** Syncs for as long as it runs: handles incoming sync messages and keeps every device up to date. Never returns normally. */
    suspend fun run()
}
