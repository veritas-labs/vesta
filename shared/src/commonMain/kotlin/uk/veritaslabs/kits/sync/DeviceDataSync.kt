package uk.veritaslabs.kits.sync

import uk.veritaslabs.kits.spaces.SpaceChangeListener
import uk.veritaslabs.kits.spaces.RosterChangeListener
import uk.veritaslabs.kits.spaces.ProfileChangeListener
import uk.veritaslabs.kits.spaces.SpaceHub
import uk.veritaslabs.kits.spaces.SpaceJoining

/**
 * What is known about one x0x agent. Each field is null when unknown: [name] is what the device calls itself, as the Space's roster says; [machineId] and [lastSeenMsAgo] come from x0x's discovery
 * cache, [lastSyncedMsAgo] is the time since a sync message last passed between the agent and this device (in either direction).
 */
data class AgentDetails(
    val name: String? = null,
    val machineId: String? = null,
    val lastSeenMsAgo: Long? = null,
    val lastSyncedMsAgo: Long? = null,
)

/**
 * Keeps the user's data in step across their own paired devices (docs/adr/ADR-013-device-data-sync.md). Implemented
 * where the Automerge document layer exists; the app only needs to feed it Space changes and run it.
 */
interface DeviceDataSync {
    /** Pass to the [uk.veritaslabs.kits.spaces.SpaceRepository] so Space changes made here reach the other devices. */
    val spaceChanges: SpaceChangeListener

    /** Pass to the [uk.veritaslabs.kits.spaces.MemberRepository] so role changes made here reach the Space's other members. */
    val rosterChanges: RosterChangeListener

    /** Pass to the [uk.veritaslabs.kits.spaces.ProfileRepository] so a name change made here reaches the other devices and Spaces. */
    val profileChanges: ProfileChangeListener

    /** Joining a Space with an invite link, and inviting someone to one (docs/adr/ADR-018-invite-links.md). */
    val joining: SpaceJoining

    /** A Space's always-on hub: enrolling one, and removing it (docs/adr/ADR-020-syncing-without-overlapping-online-time.md). */
    val hubs: SpaceHub

    /**
     * The user unpaired [agentId]: every Space that lists it as theirs marks it, and an admin device then takes it out of the Space's group
     * (docs/plans/unpaired-device-gap-plan.md).
     */
    suspend fun deviceUnpaired(agentId: String)

    /** The user paired [agentId] (again): it is no longer marked unpaired in any Space, so it can join their groups. */
    suspend fun devicePaired(agentId: String)

    /** The x0x agent ids that act for [memberId] in [spaceId], as the Space's roster lists them: one per device they have, sorted. */
    suspend fun memberAgents(spaceId: String, memberId: String): List<String>

    /** What this device knows about [agentId]'s device in [spaceId]: its name, its machine, when x0x last saw it and when Vesta last synced with it. */
    suspend fun agentDetails(spaceId: String, agentId: String): AgentDetails

    /** The devices [spaceId]'s photos (blobs, ADR-023) move between: its roster's devices and the user's own, this one not among them. Empty where there is no document layer. */
    suspend fun blobPeers(spaceId: String): Set<String> = emptySet()

    /** What the Sync activity screen shows (docs/adr/ADR-033-sync-activity.md). The stand-in where there is no sync service. */
    val syncActivity: SyncActivityRepository get() = UnavailableSyncActivityRepository

    /** Asks for a sync round now, e.g. because the app came to the foreground. Returns at once; does nothing until [run] is running. */
    fun syncNow()

    /** Syncs for as long as it runs: handles incoming sync messages and keeps every device up to date. Never returns normally. */
    suspend fun run()
}
