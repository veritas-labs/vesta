package uk.veritaslabs.kits.spaces

/** What an admin runs on the always-on machine to enrol it as a Space's hub: [commands], for the Space's group [groupId]. */
data class HubEnrolment(val groupId: String, val commands: String)

/**
 * One of the Space's hubs as the roster records it: whether it is an active member of the Space's group, and whether it is also
 * one of a member's devices ([isDevice]: a computer that runs Vesta and is used as a hub too).
 */
data class HubState(val agentId: String, val inGroup: Boolean, val isDevice: Boolean = false)

/** What came of choosing or removing a hub. */
enum class HubResult {
    Done,

    /** Only an admin of the Space can. */
    NotAnAdmin,

    /** The agent is not (yet) an active member of the Space's group. */
    NotInGroup,

    /** The agent is a device of a member who is not an admin of the Space, and making it an x0x admin would hand that member the power. */
    NotAnAdminDevice,

    /** The Space's group isn't ready on this device, or x0x refused. Try again. */
    Failed,
}

/**
 * The always-on hubs of a Space (docs/adr/ADR-020-syncing-without-overlapping-online-time.md): x0x agents that are admin members
 * of the Space's group and so replicate its mailbox while the phones are away. Usually a plain x0x daemon with no Vesta on it,
 * enrolled by running [enrolment]'s commands on that machine and then picked out of the group's [candidates] ([useAsHub]); it may
 * also be a device of an admin member, such as a desktop that runs Vesta ([ownDeviceCandidates]). There can be several.
 */
interface SpaceHub {
    /** The commands that enrol a hub, with a fresh invite to the Space's group; null unless this user is an admin and the group's mailbox is ready. */
    suspend fun enrolment(spaceId: String): HubEnrolment?

    /** The recorded hubs, each with whether it is in the group. */
    suspend fun hubs(spaceId: String): List<HubState>

    /** The agents that are active in the Space's group but act for nobody on the roster and are not hubs: what an enrolled machine looks like. */
    suspend fun candidates(spaceId: String): List<String>

    /** This user's own devices that are active in the Space's group and not hubs yet, this device included. */
    suspend fun ownDeviceCandidates(spaceId: String): List<String>

    /** Records [agentId] as one of the Space's hubs, which every member's device then learns, and makes it an admin of the group. */
    suspend fun useAsHub(spaceId: String, agentId: String): HubResult

    /**
     * Forgets [agentId] as a hub. A machine that is no member's device also leaves the Space's group, which changes its keys so it
     * can't read what follows; a device stays in the group, as it is still its member's.
     */
    suspend fun removeHub(spaceId: String, agentId: String): HubResult
}
