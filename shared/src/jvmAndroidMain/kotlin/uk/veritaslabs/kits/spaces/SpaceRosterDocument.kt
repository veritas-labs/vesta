package uk.veritaslabs.kits.spaces

import kotlin.jvm.optionals.getOrNull
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.Read
import uk.veritaslabs.kits.docs.spaceDocumentId

private const val RosterFeature = "roster"
private const val MemberPrefix = "member:"
private const val DevicePrefix = "device:"
private const val DeviceNamePrefix = "devicename:"
private const val NameField = "name"
private const val AvatarField = "avatar"
private const val RoleField = "role"
private const val JoinedAtField = "joinedAt"
private const val RemovedField = "removed"
private const val GroupKey = "group"
private const val HubKey = "hub"
private const val HubPrefix = "hub:"
private const val GonePrefix = "gone:"
private const val InvitePrefix = "invite:"
private const val CreatedByField = "createdBy"
private const val ExpiresAtField = "expiresAt"
private const val MaxUsesField = "maxUses"
private const val RevokedField = "revoked"
private const val UsedByPrefix = "usedBy:"

/** ADR-006's `"$spaceId:$feature"` id of [spaceId]'s roster document (the same string the home-era code made). */
fun rosterDocumentId(spaceId: String): String = spaceDocumentId(spaceId, RosterFeature)

/** One member as the roster holds it, with the agents that act for them in this Space. */
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
 * A Space's roster (docs/adr/ADR-014-home-membership.md) is a flat set of scalar keys at the document root:
 *
 *   member:<memberId>:name | avatar | role | joinedAt | removed     one person
 *   device:<agentId>  =  <memberId>                                the agents that act for that person
 *   devicename:<agentId>  =  <name>                                what that device calls itself, written only by that device
 *
 * Nothing is a nested map. Two devices of one person write the same member's keys concurrently, and a nested
 * `member:<id>` map created by both would conflict on merge and lose one device's entries; scalars written with the
 * same value merge to exactly that value. So any device may write its own person's keys, and a device writes only its
 * own `device:` key.
 *
 * The Space's x0x group (docs/adr/ADR-021-home-x0x-group.md) is one more scalar, `group = <groupId>`. Its always-on hubs
 * (docs/adr/ADR-020, agents in the group that replicate the mailbox; a hub may also be a member's device) are one key each,
 * `hub:<agentId> = true`, so two admins adding different hubs at once merge to both. Before several hubs were allowed the
 * roster held one scalar, `hub = <agentId>`: it is still read as a hub, and removing that agent deletes it.
 *
 * A device the user unpaired (docs/plans/unpaired-device-gap-plan.md) gets one more key, `gone:<agentId> = <memberId>`, beside its unchanged
 * `device:` claim. A device a `gone:` key names is left out of its member's `devices` when the claim names the same member, so one
 * member cannot make another's device disappear. The claim is kept because the unpaired device can write it again after it has merged a
 * release, and that later write survives; the tombstone is what stays. Only an explicit re-pairing deletes it.
 *
 * Invites (docs/adr/ADR-018-invite-links.md) live in the same document, as flat keys too:
 *
 *   invite:<secretHash>:createdBy | expiresAt | maxUses | revoked   one invite link
 *   invite:<secretHash>:usedBy:<memberId>  =  true                  one admission it was spent on
 *
 * Uses are one key per member rather than a counter: a counter incremented by two admin devices at once would lose an
 * increment on merge, while one key per member merges to both.
 */

/** Every member of the roster, tombstones included. */
fun Read.readRoster(): List<RosterMember> {
    val entries = mapEntries(ObjectId.ROOT).getOrNull().orEmpty()
    val fields = mutableMapOf<String, MutableMap<String, AmValue>>()
    val claims = mutableListOf<Pair<String, String>>() // agent to member
    val gone = mutableMapOf<String, String>() // agent to the member who unpaired it
    for (entry in entries) {
        when {
            entry.key.startsWith(MemberPrefix) -> {
                val rest = entry.key.removePrefix(MemberPrefix)
                val split = rest.lastIndexOf(':')
                if (split > 0) fields.getOrPut(rest.substring(0, split)) { mutableMapOf() }[rest.substring(split + 1)] = entry.value
            }
            entry.key.startsWith(DevicePrefix) -> {
                val memberId = (entry.value as? AmValue.Str)?.value ?: continue
                claims += entry.key.removePrefix(DevicePrefix) to memberId
            }
            entry.key.startsWith(GonePrefix) -> {
                val memberId = (entry.value as? AmValue.Str)?.value ?: continue
                gone[entry.key.removePrefix(GonePrefix)] = memberId
            }
        }
    }
    // A tombstone only counts against a claim that names the member who wrote it.
    val devices = mutableMapOf<String, MutableSet<String>>()
    for ((agent, member) in claims) {
        if (gone[agent] != member) devices.getOrPut(member) { mutableSetOf() } += agent
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

/** What each device calls itself, by agent id: every `devicename:` key. A device that never wrote one (an older version) has none. */
fun Read.readRosterDeviceNames(): Map<String, String> =
    mapEntries(ObjectId.ROOT).getOrNull().orEmpty()
        .filter { it.key.startsWith(DeviceNamePrefix) }
        .mapNotNull { entry -> (entry.value as? AmValue.Str)?.value?.takeIf { it.isNotBlank() }?.let { entry.key.removePrefix(DeviceNamePrefix) to it } }
        .toMap()

/** Records that [agentId] calls itself [name]. Returns whether anything changed. */
fun setRosterDeviceName(doc: Document, agentId: String, name: String): Boolean {
    val tx = doc.startTransaction()
    val key = "$DeviceNamePrefix$agentId"
    if ((tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Str)?.value == name) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, key, name)
    tx.commit()
    return true
}

/** The devices the roster marks as unpaired, by agent id, with the member who unpaired each: `gone:` keys, whether or not a claim still names them. */
fun Read.readRosterGone(): Map<String, String> =
    mapEntries(ObjectId.ROOT).getOrNull().orEmpty()
        .filter { it.key.startsWith(GonePrefix) }
        .mapNotNull { entry -> (entry.value as? AmValue.Str)?.value?.let { entry.key.removePrefix(GonePrefix) to it } }
        .toMap()

/** The agents to take out of the Space's group: those marked gone whose claim still names the member who marked them (a tombstone for anyone else's device is ignored). */
fun Read.readRosterGoneDevices(): Set<String> =
    readRosterGone().filter { (agent, member) -> (get(ObjectId.ROOT, "$DevicePrefix$agent").getOrNull() as? AmValue.Str)?.value == member }.keys

/**
 * Marks [agentId], a device [memberId] has claimed, as unpaired by them. Does nothing unless the roster has that claim, so a member
 * can only mark their own devices. Returns whether anything changed.
 */
fun markRosterGone(doc: Document, agentId: String, memberId: String): Boolean {
    val tx = doc.startTransaction()
    val claimedBy = (tx.get(ObjectId.ROOT, "$DevicePrefix$agentId").getOrNull() as? AmValue.Str)?.value
    val key = "$GonePrefix$agentId"
    if (claimedBy != memberId || (tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Str)?.value == memberId) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, key, memberId)
    tx.commit()
    return true
}

/** Forgets that [agentId] was unpaired, as pairing it again does. Returns whether anything changed. */
fun clearRosterGone(doc: Document, agentId: String): Boolean {
    val tx = doc.startTransaction()
    val key = "$GonePrefix$agentId"
    if (!tx.get(ObjectId.ROOT, key).isPresent) {
        tx.rollback()
        return false
    }
    tx.delete(ObjectId.ROOT, key)
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

/** Why an invite can't be used, or that it can. */
enum class InviteStatus { Valid, Expired, Revoked, Used }

/** One invite link as the roster holds it, identified by the hash of its secret. */
data class RosterInvite(
    val secretHash: String,
    val createdBy: String,
    val expiresAt: Long,
    val maxUses: Int,
    val revoked: Boolean,
    val usedBy: Set<String>,
) {
    /** False until every field of the invite has arrived. */
    val isComplete: Boolean get() = createdBy.isNotEmpty() && expiresAt > 0 && maxUses > 0

    /**
     * Whether [memberId] can be admitted with this invite at [nowMillis]. Someone who already spent it stays valid for
     * themselves, so a lost answer can be repeated, and doesn't use it up twice.
     */
    fun statusFor(memberId: String, nowMillis: Long): InviteStatus = when {
        revoked -> InviteStatus.Revoked
        nowMillis >= expiresAt -> InviteStatus.Expired
        memberId in usedBy -> InviteStatus.Valid
        usedBy.size >= maxUses -> InviteStatus.Used
        else -> InviteStatus.Valid
    }
}

/** Every invite of the roster, revoked and spent ones included. */
fun Read.readInvites(): List<RosterInvite> {
    val entries = mapEntries(ObjectId.ROOT).getOrNull().orEmpty()
    val fields = mutableMapOf<String, MutableMap<String, AmValue>>()
    for (entry in entries) {
        if (!entry.key.startsWith(InvitePrefix)) continue
        val rest = entry.key.removePrefix(InvitePrefix)
        val split = rest.indexOf(':')
        if (split > 0) fields.getOrPut(rest.substring(0, split)) { mutableMapOf() }[rest.substring(split + 1)] = entry.value
    }
    return fields.map { (hash, values) ->
        RosterInvite(
            secretHash = hash,
            createdBy = (values[CreatedByField] as? AmValue.Str)?.value.orEmpty(),
            expiresAt = (values[ExpiresAtField] as? AmValue.Str)?.value?.toLongOrNull() ?: 0L,
            maxUses = (values[MaxUsesField] as? AmValue.Str)?.value?.toIntOrNull() ?: 0,
            revoked = (values[RevokedField] as? AmValue.Bool)?.value == true,
            usedBy = values.filter { (field, value) -> field.startsWith(UsedByPrefix) && (value as? AmValue.Bool)?.value == true }
                .keys.map { it.removePrefix(UsedByPrefix) }.toSet(),
        )
    }
}

/** Records a new invite. Does nothing if one with this [secretHash] is already there. Returns whether anything changed. */
fun putRosterInvite(doc: Document, secretHash: String, createdBy: String, expiresAt: Long, maxUses: Int): Boolean {
    val tx = doc.startTransaction()
    val createdByKey = "$InvitePrefix$secretHash:$CreatedByField"
    if (tx.get(ObjectId.ROOT, createdByKey).isPresent) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, createdByKey, createdBy)
    tx.set(ObjectId.ROOT, "$InvitePrefix$secretHash:$ExpiresAtField", expiresAt.toString())
    tx.set(ObjectId.ROOT, "$InvitePrefix$secretHash:$MaxUsesField", maxUses.toString())
    tx.commit()
    return true
}

/** Revokes an invite, if the roster has it and it isn't already. Returns whether anything changed. */
fun revokeRosterInvite(doc: Document, secretHash: String): Boolean {
    val tx = doc.startTransaction()
    val known = tx.get(ObjectId.ROOT, "$InvitePrefix$secretHash:$CreatedByField").isPresent
    val revokedKey = "$InvitePrefix$secretHash:$RevokedField"
    if (!known || (tx.get(ObjectId.ROOT, revokedKey).getOrNull() as? AmValue.Bool)?.value == true) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, revokedKey, true)
    tx.commit()
    return true
}

/** Records that the invite was spent admitting [memberId], if the roster has it. Returns whether anything changed. */
fun useRosterInvite(doc: Document, secretHash: String, memberId: String): Boolean {
    val tx = doc.startTransaction()
    val known = tx.get(ObjectId.ROOT, "$InvitePrefix$secretHash:$CreatedByField").isPresent
    val usedKey = "$InvitePrefix$secretHash:$UsedByPrefix$memberId"
    if (!known || (tx.get(ObjectId.ROOT, usedKey).getOrNull() as? AmValue.Bool)?.value == true) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, usedKey, true)
    tx.commit()
    return true
}

/** The id of the Space's x0x group, or null while the roster has none. */
fun Read.readRosterGroup(): String? = (get(ObjectId.ROOT, GroupKey).getOrNull() as? AmValue.Str)?.value

/** Names [groupId] as the Space's x0x group, replacing any other. Returns whether anything changed. */
fun setRosterGroup(doc: Document, groupId: String): Boolean {
    val tx = doc.startTransaction()
    if ((tx.get(ObjectId.ROOT, GroupKey).getOrNull() as? AmValue.Str)?.value == groupId) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, GroupKey, groupId)
    tx.commit()
    return true
}

/** The agents recorded as the Space's always-on hubs: every `hub:<agentId>` key, and the older single `hub` value. */
fun Read.readRosterHubs(): Set<String> {
    val hubs = mutableSetOf<String>()
    (get(ObjectId.ROOT, HubKey).getOrNull() as? AmValue.Str)?.value?.takeIf { it.isNotEmpty() }?.let { hubs += it }
    for (entry in mapEntries(ObjectId.ROOT).getOrNull().orEmpty()) {
        if (entry.key.startsWith(HubPrefix) && (entry.value as? AmValue.Bool)?.value == true) hubs += entry.key.removePrefix(HubPrefix)
    }
    return hubs
}

/** Records [agentId] as one of the Space's hubs, beside any others. Returns whether anything changed. */
fun addRosterHub(doc: Document, agentId: String): Boolean {
    val tx = doc.startTransaction()
    val key = "$HubPrefix$agentId"
    if ((tx.get(ObjectId.ROOT, key).getOrNull() as? AmValue.Bool)?.value == true) {
        tx.rollback()
        return false
    }
    tx.set(ObjectId.ROOT, key, true)
    tx.commit()
    return true
}

/** Forgets [agentId] as a hub, whichever way it was recorded, leaving the others. Returns whether anything changed. */
fun removeRosterHub(doc: Document, agentId: String): Boolean {
    val tx = doc.startTransaction()
    var changed = false
    val key = "$HubPrefix$agentId"
    if (tx.get(ObjectId.ROOT, key).isPresent) {
        tx.delete(ObjectId.ROOT, key)
        changed = true
    }
    if ((tx.get(ObjectId.ROOT, HubKey).getOrNull() as? AmValue.Str)?.value == agentId) {
        tx.delete(ObjectId.ROOT, HubKey)
        changed = true
    }
    if (changed) tx.commit() else tx.rollback()
    return changed
}
