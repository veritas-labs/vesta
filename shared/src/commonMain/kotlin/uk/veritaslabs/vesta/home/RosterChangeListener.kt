package uk.veritaslabs.vesta.home

/**
 * Told by [MemberRepository] about each change an admin makes to a Home's members, so it reaches the Home's other
 * members (docs/adr/ADR-014-home-membership.md). Not called for changes that arrive from another device.
 */
interface RosterChangeListener {
    /** [memberId]'s role in [homeId] was changed to [role] (a `HomeMember.role` value). */
    suspend fun roleChanged(homeId: String, memberId: String, role: String)
}
