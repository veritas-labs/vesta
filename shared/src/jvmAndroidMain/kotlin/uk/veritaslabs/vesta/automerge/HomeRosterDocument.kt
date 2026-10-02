package uk.veritaslabs.vesta.automerge

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.Read

private const val RosterFeature = "roster"
private const val MemberPrefix = "member:"
private const val DevicePrefix = "device:"
private const val NameField = "name"
private const val AvatarField = "avatar"
private const val RoleField = "role"
private const val JoinedAtField = "joinedAt"
private const val RemovedField = "removed"

/** ADR-006's `"$homeId:$feature"` id of [homeId]'s roster document. */
fun rosterDocumentId(homeId: String): String = "$homeId:$RosterFeature"

/** One member as the roster holds it, with the agents that act for them in this Home. */
data class RosterMember(
    val memberId: String,
    val name: String,
    val avatar: String?,
    val role: String,
    val joinedAt: Long,
    val removed: Boolean,
    val devices: Set<String>,
) {
    /** False until every field of the member has arrived. */
    val isComplete: Boolean get() = name.isNotEmpty() && role.isNotEmpty() && joinedAt > 0
}

/*
 * A Home's roster (docs/adr/ADR-014-home-membership.md) is a flat set of scalar keys at the document root:
 *
 *   member:<memberId>:name | avatar | role | joinedAt | removed     one person
 *   device:<agentId>  =  <memberId>                                the agents that act for that person
 *
 * Nothing is a nested map. Two devices of one person write the same member's keys concurrently, and a nested
 * `member:<id>` map created by both would conflict on merge and lose one device's entries; scalars written with the
 * same value merge to exactly that value. So any device may write its own person's keys, and a device writes only its
 * own `device:` key.
 */

/** Every member of the roster, tombstones included. */
fun Read.readRoster(): List<RosterMember> {
    val entries = mapEntries(ObjectId.ROOT).getOrNull().orEmpty()
    val fields = mutableMapOf<String, MutableMap<String, AmValue>>()
    val devices = mutableMapOf<String, MutableSet<String>>()
    for (entry in entries) {
        when {
            entry.key.startsWith(MemberPrefix) -> {
                val rest = entry.key.removePrefix(MemberPrefix)
                val split = rest.lastIndexOf(':')
                if (split > 0) fields.getOrPut(rest.substring(0, split)) { mutableMapOf() }[rest.substring(split + 1)] = entry.value
            }
            entry.key.startsWith(DevicePrefix) -> {
                val memberId = (entry.value as? AmValue.Str)?.value ?: continue
                devices.getOrPut(memberId) { mutableSetOf() } += entry.key.removePrefix(DevicePrefix)
            }
        }
    }
    return fields.map { (memberId, values) ->
        RosterMember(
            memberId = memberId,
            name = (values[NameField] as? AmValue.Str)?.value.orEmpty(),
            avatar = (values[AvatarField] as? AmValue.Str)?.value,
            role = (values[RoleField] as? AmValue.Str)?.value.orEmpty(),
            joinedAt = (values[JoinedAtField] as? AmValue.Str)?.value?.toLongOrNull() ?: 0L,
            removed = (values[RemovedField] as? AmValue.Bool)?.value == true,
            devices = devices[memberId].orEmpty(),
        )
    }
}

/**
 * Writes a member: [name] and [avatar] always, so a rename reaches everyone; [role] and [joinedAt] only when the
 * roster has none yet, so a device never overwrites what an admin decided. Returns whether anything changed.
 */
fun putRosterMember(doc: Document, memberId: String, name: String, avatar: String?, role: String, joinedAt: Long): Boolean {
    val tx = doc.startTransaction()
    var changed = false
    fun set(field: String, value: String, onlyIfAbsent: Boolean = false) {
        val key = "$MemberPrefix$memberId:$field"
        val current = (tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Str)?.value
        if (current == value || (onlyIfAbsent && current != null)) return
        tx.set(ObjectId.ROOT, key, value)
        changed = true
    }
    set(NameField, name)
    avatar?.let { set(AvatarField, it) }
    set(RoleField, role, onlyIfAbsent = true)
    set(JoinedAtField, joinedAt.toString(), onlyIfAbsent = true)
    if (changed) tx.commit() else tx.rollback()
    return changed
}

/** Records that [agentId] acts for [memberId]. Returns whether anything changed. */
fun claimRosterDevice(doc: Document, agentId: String, memberId: String): Boolean {
    val tx = doc.startTransaction()
    val key = "$DevicePrefix$agentId"
    if ((tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Str)?.value == memberId) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, key, memberId)
    tx.commit()
    return true
}

/**
 * Sets [memberId]'s role outright, unlike [putRosterMember], which never overwrites one: this is an admin's decision.
 * Does nothing for a member the roster doesn't have. Returns whether anything changed.
 */
fun setRosterRole(doc: Document, memberId: String, role: String): Boolean {
    val tx = doc.startTransaction()
    val key = "$MemberPrefix$memberId:$RoleField"
    val known = tx.get(ObjectId.ROOT, "$MemberPrefix$memberId:$NameField").isPresent
    if (!known || (tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Str)?.value == role) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, key, role)
    tx.commit()
    return true
}

/** Removes the claim that [agentId] acts for [memberId], if that is what the roster says. Returns whether anything changed. */
fun releaseRosterDevice(doc: Document, agentId: String, memberId: String): Boolean {
    val tx = doc.startTransaction()
    val key = "$DevicePrefix$agentId"
    if ((tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Str)?.value != memberId) {
        tx.rollback()
        return false
    }
    tx.delete(ObjectId.ROOT, key)
    tx.commit()
    return true
}

/** Marks [memberId] removed, if the roster has them and they aren't already. Returns whether anything changed. */
fun retireRosterMember(doc: Document, memberId: String): Boolean {
    val tx = doc.startTransaction()
    val known = tx.get(ObjectId.ROOT, "$MemberPrefix$memberId:$NameField").isPresent
    val removedKey = "$MemberPrefix$memberId:$RemovedField"
    if (!known || (tx.get(ObjectId.ROOT, removedKey).getOrNull() as? AmValue.Bool)?.value == true) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, removedKey, true)
    tx.commit()
    return true
}
