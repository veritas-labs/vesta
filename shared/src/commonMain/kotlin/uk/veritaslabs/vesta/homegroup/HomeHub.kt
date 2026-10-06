package uk.veritaslabs.vesta.homegroup

/** What an admin runs on the always-on machine to enrol it as a Home's hub: [commands], for the Home's group [groupId]. */
data class HubEnrolment(val groupId: String, val commands: String)

/** The Home's hub as the roster records it ([agentId], null for none) and whether it is an active member of the Home's group. */
data class HubState(val agentId: String?, val inGroup: Boolean)

/** What came of choosing or removing a hub. */
enum class HubResult {
    Done,

    /** Only an admin of the Home can. */
    NotAnAdmin,

    /** The agent is not (yet) an active member of the Home's group. */
    NotInGroup,

    /** The agent acts for a member of the Home, so it can't be its hub. */
    ActsForAMember,

    /** The Home's group isn't ready on this device, or x0x refused. Try again. */
    Failed,
}

/**
 * The always-on hub of a Home (docs/adr/ADR-020-syncing-without-overlapping-online-time.md): a plain x0x daemon, with no Vesta
 * on it, that is an admin member of the Home's group and so replicates its mailbox while the phones are away. The admin enrols
 * it by running [enrolment]'s commands on that machine, then picks it out of the group's [candidates] ([useAsHub]).
 */
interface HomeHub {
    /** The commands that enrol a hub, with a fresh invite to the Home's group; null unless this user is an admin and the group's mailbox is ready. */
    suspend fun enrolment(homeId: String): HubEnrolment?

    /** The recorded hub, and whether it is in the group. */
    suspend fun state(homeId: String): HubState

    /** The agents that are active in the Home's group but act for nobody on the roster and are not the hub: what the enrolled machine looks like. */
    suspend fun candidates(homeId: String): List<String>

    /** Records [agentId] as the Home's hub, which every member's device then learns, and makes it an admin of the group. */
    suspend fun useAsHub(homeId: String, agentId: String): HubResult

    /** Removes the hub from the Home's group, which changes its keys so it can't read what follows, and forgets it. */
    suspend fun removeHub(homeId: String): HubResult
}
