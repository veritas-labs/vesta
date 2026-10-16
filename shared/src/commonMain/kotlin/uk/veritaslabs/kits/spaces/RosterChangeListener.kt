package uk.veritaslabs.kits.spaces

/**
 * Told by [MemberRepository] and [SpaceRepository] about each change to a Space's members made here, so it reaches the
 * Space's other members (docs/adr/ADR-014-home-membership.md). Not called for changes that arrive from another device.
 */
interface RosterChangeListener {
    /** [memberId]'s role in [spaceId] was changed to [role] (a `HomeMember.role` value). */
    suspend fun roleChanged(spaceId: String, memberId: String, role: String)

    /** An admin removed [memberId] from [spaceId]: stop syncing with their devices, and tell them. */
    suspend fun memberRemoved(spaceId: String, memberId: String)

    /** The current user, [memberId], is leaving [spaceId], which is about to be deleted here: tell the other members first. */
    suspend fun memberLeft(spaceId: String, memberId: String)
}
