package uk.veritaslabs.vesta.devicesync

import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.homejoin.HomeJoining

/**
 * Keeps the user's data in step across their own paired devices (docs/adr/ADR-013-device-data-sync.md). Implemented
 * where the Automerge document layer exists; the app only needs to feed it Home changes and run it.
 */
interface DeviceDataSync {
    /** Pass to the [uk.veritaslabs.vesta.home.HomeRepository] so Home changes made here reach the other devices. */
    val homeChanges: HomeChangeListener

    /** Joining a Home with a link, and inviting someone to one (docs/adr/ADR-015-joining-a-home.md). */
    val joining: HomeJoining

    /** Syncs for as long as it runs: handles incoming sync messages and keeps every device up to date. Never returns normally. */
    suspend fun run()
}
