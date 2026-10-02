package uk.veritaslabs.vesta.home

/**
 * Told by [MemberRepository] and [HomeRepository] about each change to a Home's members made here, so it reaches the
 * Home's other members (docs/adr/ADR-014-home-membership.md). Not called for changes that arrive from another device.
 */
interface RosterChangeListener {
    /** [memberId]'s role in [homeId] was changed to [role] (a `HomeMember.role` value). */
    suspend fun roleChanged(homeId: String, memberId: String, role: String)

    /** An admin removed [memberId] from [homeId]: stop syncing with their devices, and tell them. */
    suspend fun memberRemoved(homeId: String, memberId: String)

    /** The current user, [memberId], is leaving [homeId], which is about to be deleted here: tell the other members first. */
    suspend fun memberLeft(homeId: String, memberId: String)
}
