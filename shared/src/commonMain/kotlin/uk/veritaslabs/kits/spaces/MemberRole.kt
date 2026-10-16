package uk.veritaslabs.kits.spaces

/** The role a member has in a Space; [storageValue] is the `HomeMember.role` column's value. */
enum class MemberRole(val storageValue: String) {
    Admin("admin"),
    Member("member");

    companion object {
        /** The role stored as [value]; anything unrecognised is an ordinary [Member]. */
        fun fromStorageValue(value: String): MemberRole = entries.firstOrNull { it.storageValue == value } ?: Member
    }
}

/** One person in a Space. A person is one row however many devices they have; [isYou] marks the current user. */
data class MemberListItem(
    val memberId: String,
    val name: String,
    val role: MemberRole,
    val isYou: Boolean = false,
)
