package uk.veritaslabs.vesta.devicesync

import uk.veritaslabs.vesta.home.HomeChangeListener
import uk.veritaslabs.vesta.home.RosterChangeListener
import uk.veritaslabs.vesta.profile.ProfileChangeListener
import uk.veritaslabs.vesta.homegroup.HomeHub
import uk.veritaslabs.vesta.homejoin.HomeJoining

/**
 * What is known about one x0x agent. Each field is null when unknown: [machineId] and [lastSeenMsAgo] come from x0x's discovery
 * cache, [lastSyncedMsAgo] is the time since a sync message last passed between the agent and this device (in either direction).
 */
data class AgentDetails(
    val machineId: String? = null,
    val lastSeenMsAgo: Long? = null,
    val lastSyncedMsAgo: Long? = null,
)

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

    /** A Home's always-on hub: enrolling one, and removing it (docs/adr/ADR-020-syncing-without-overlapping-online-time.md). */
    val hubs: HomeHub

    /**
     * The user unpaired [agentId]: every Home that lists it as theirs marks it, and an admin device then takes it out of the Home's group
     * (docs/plans/unpaired-device-gap-plan.md).
     */
    suspend fun deviceUnpaired(agentId: String)

    /** The user paired [agentId] (again): it is no longer marked unpaired in any Home, so it can join their groups. */
    suspend fun devicePaired(agentId: String)

    /** The x0x agent ids that act for [memberId] in [homeId], as the Home's roster lists them: one per device they have, sorted. */
    suspend fun memberAgents(homeId: String, memberId: String): List<String>

    /** What this device knows about [agentId]'s device: its machine, when x0x last saw it and when Vesta last synced with it. */
    suspend fun agentDetails(agentId: String): AgentDetails

    /** Asks for a sync round now, e.g. because the app came to the foreground. Returns at once; does nothing until [run] is running. */
    fun syncNow()

    /** Syncs for as long as it runs: handles incoming sync messages and keeps every device up to date. Never returns normally. */
    suspend fun run()
}
