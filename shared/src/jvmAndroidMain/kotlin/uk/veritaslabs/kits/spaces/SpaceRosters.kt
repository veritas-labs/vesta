@file:OptIn(uk.veritaslabs.kits.KitsInternalApi::class)

package uk.veritaslabs.kits.spaces

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.automerge.Document
import org.automerge.SyncState
import uk.veritaslabs.kits.store.KitsDatabase
import uk.veritaslabs.kits.docs.SpaceDocument
import uk.veritaslabs.kits.docs.changesSince
import uk.veritaslabs.kits.docs.encodeHeads
private const val RosterFeature = "roster"

/**
 * Each Space's roster as an Automerge document (docs/adr/ADR-014-home-membership.md) that syncs between the user's
 * devices. The `HomeMember` table stays what the app reads: [publish] writes this device's own member and agent into
 * the document, and [project] copies the document's members into the table. Every document belongs to an existing
 * Space, and [generate] and [receive] ignore a Space this device doesn't have.
 */
internal class SpaceRosters(
    private val database: KitsDatabase,
    private val profiles: ProfileRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val deviceName: () -> String,
) {

    private val lock = Mutex()
    private val documents = mutableMapOf<String, Document>()
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Space each time its roster is changed on this device, so it can be pushed to the other devices. */
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    /**
     * Writes the current user's member of [spaceId], and [agentId] (if known) as one of their devices, with the name this device
     * gives itself, into that Space's roster. A device with no member row for the user yet (a Space that has just arrived) does nothing until
     * the roster has brought one. Once [agentId] is known, any other device the roster lists for the user that isn't
     * in [pairedAgents] (the devices this one is paired with) is released: it has been removed from the user's
     * devices, so it no longer acts for them. The member stays, with fewer devices (ADR-014 §4).
     */
    suspend fun publish(spaceId: String, agentId: String?, pairedAgents: Set<String>) {
        val me = profiles.current().id
        val row = database.homeMemberQueries.selectByHome(spaceId).awaitAsList().firstOrNull { it.memberId == me } ?: return
        val changed = lock.withLock {
            val doc = open(spaceId) ?: return
            val member = putRosterMember(doc, row.memberId, row.name, row.avatarResourceId, row.role, row.joinedAt)
            val device = agentId?.let { claimRosterDevice(doc, it, row.memberId) } ?: false
            val named = agentId?.let { nameOwnDevice(doc, it) } ?: false
            val released = agentId != null && doc.readRoster().firstOrNull { it.memberId == row.memberId }?.devices.orEmpty()
                .filter { it != agentId && it !in pairedAgents }
                .map { releaseRosterDevice(doc, it, row.memberId) }
                .any { it }
            (member || device || named || released).also { if (it) persist(spaceId, doc) }
        }
        if (changed) _changes.tryEmit(spaceId)
    }

    /**
     * Admits [memberId] to [spaceId]'s roster with [role] and [agentId] as their device, and copies them into the
     * `HomeMember` table. A member who is already there keeps their role and join time.
     */
    suspend fun admit(spaceId: String, memberId: String, name: String, role: String, agentId: String) {
        val changed = lock.withLock {
            val doc = open(spaceId) ?: return
            val member = putRosterMember(doc, memberId, name, null, role, nowMillis())
            val device = claimRosterDevice(doc, agentId, memberId)
            (member || device).also { if (it) persist(spaceId, doc) }
        }
        project(spaceId)
        if (changed) _changes.tryEmit(spaceId)
    }

    /** Sets [memberId]'s role in [spaceId]'s roster to [role], an admin's decision, which overwrites the role the roster had. */
    suspend fun setRole(spaceId: String, memberId: String, role: String) {
        val changed = lock.withLock {
            val doc = open(spaceId) ?: return
            setRosterRole(doc, memberId, role).also { if (it) persist(spaceId, doc) }
        }
        if (changed) _changes.tryEmit(spaceId)
    }

    /** Marks [memberId] removed from [spaceId]: they leave the `HomeMember` table, and their devices stop being peers. */
    suspend fun remove(spaceId: String, memberId: String) {
        val changed = lock.withLock {
            val doc = open(spaceId) ?: return
            retireRosterMember(doc, memberId).also { if (it) persist(spaceId, doc) }
        }
        project(spaceId)
        if (changed) _changes.tryEmit(spaceId)
    }

    /** Marks [memberId] removed in every Space that has them: that Profile id has been replaced by the person's shared one. */
    suspend fun retire(memberId: String) {
        val changed = lock.withLock {
            database.homeQueries.selectAll().awaitAsList().mapNotNull { space ->
                val doc = open(space.id) ?: return@mapNotNull null
                space.id.takeIf { retireRosterMember(doc, memberId).also { changed -> if (changed) persist(space.id, doc) } }
            }
        }
        changed.forEach { _changes.tryEmit(it) }
    }

    /**
     * Marks [agentId], one of this user's devices, as unpaired in every Space whose roster lists it as theirs
     * (docs/plans/unpaired-device-gap-plan.md): it stops being a peer, and an admin device takes it out of the Space's group. Called when
     * the user unpairs it, not when a pairing list merely lacks it. A Space that does not list it as this user's is left as it is.
     */
    suspend fun markGone(agentId: String) {
        val me = profiles.current().id
        val changed = lock.withLock {
            database.homeQueries.selectAll().awaitAsList().mapNotNull { space ->
                val doc = open(space.id) ?: return@mapNotNull null
                space.id.takeIf { markRosterGone(doc, agentId, me).also { changed -> if (changed) persist(space.id, doc) } }
            }
        }
        changed.forEach { _changes.tryEmit(it) }
    }

    /** Forgets that [agentId] was unpaired, in every Space that says so: the user paired it again. */
    suspend fun clearGone(agentId: String) {
        val changed = lock.withLock {
            database.homeQueries.selectAll().awaitAsList().mapNotNull { space ->
                val doc = open(space.id) ?: return@mapNotNull null
                space.id.takeIf { clearRosterGone(doc, agentId).also { changed -> if (changed) persist(space.id, doc) } }
            }
        }
        changed.forEach { _changes.tryEmit(it) }
    }

    /**
     * Makes an invite link for [spaceId] on behalf of [memberId], who must be an admin the roster still lists
     * (docs/adr/ADR-018-invite-links.md), and records it in the roster. The link names the devices of the Space's admins,
     * [memberId]'s first, up to [InviteLink.MaxAdminAgents]. With [ownAgentId], this device's agent, [memberId] (this
     * user) and that device are first written into the roster from the `HomeMember` table: a Space just made hasn't
     * published them yet, and the device is an admin device by definition. Null when [memberId] isn't an admin of this
     * Space or no admin device is known.
     */
    suspend fun createInvite(
        spaceId: String,
        memberId: String,
        ttlMillis: Long,
        maxUses: Int = 1,
        ownAgentId: String? = null,
    ): InviteLink? {
        val link = lock.withLock {
            val doc = open(spaceId) ?: return null
            ownAgentId?.let { agent ->
                // As [publish] would, so a Space made a moment ago doesn't have to wait for the next sync round.
                database.homeMemberQueries.selectByHome(spaceId).awaitAsList().firstOrNull { it.memberId == memberId }?.let { row ->
                    putRosterMember(doc, row.memberId, row.name, row.avatarResourceId, row.role, row.joinedAt)
                    claimRosterDevice(doc, agent, memberId)
                    nameOwnDevice(doc, agent)
                }
            }
            if (doc.readRoster().none { it.memberId == memberId && it.role == MemberRole.Admin.storageValue && !it.removed }) return null
            val admins = doc.readRoster().filter { it.role == MemberRole.Admin.storageValue && !it.removed }
            val agents = admins.sortedBy { it.memberId != memberId }.flatMap { it.devices.sorted() }
                .distinct().take(InviteLink.MaxAdminAgents).takeIf { it.isNotEmpty() } ?: return null
            val spaceName = database.homeQueries.selectById(spaceId).awaitAsOneOrNull()?.name ?: return null
            val secret = newInviteSecret()
            val expiresAt = nowMillis() + ttlMillis
            putRosterInvite(doc, inviteSecretHash(secret), memberId, expiresAt, maxUses)
            persist(spaceId, doc)
            InviteLink(spaceId, secret, expiresAt, agents, spaceName)
        }
        _changes.tryEmit(spaceId)
        return link
    }

    /** Every invite recorded for [spaceId], for an admin to see and revoke. */
    suspend fun invites(spaceId: String): List<RosterInvite> =
        lock.withLock { open(spaceId)?.readInvites()?.filter { it.isComplete }.orEmpty() }

    /**
     * Whether [secret] can admit [memberId] to [spaceId] at the current time; null when this Space has no such invite (or
     * it hasn't fully arrived), which callers answer silently so a stranger learns nothing.
     */
    suspend fun checkInvite(spaceId: String, secret: String, memberId: String): InviteStatus? =
        inviteStatus(spaceId, inviteSecretHash(secret), memberId)

    /** As [checkInvite], for an invite known only by the hash of its secret. */
    suspend fun inviteStatus(spaceId: String, secretHash: String, memberId: String): InviteStatus? = lock.withLock {
        open(spaceId)?.readInvites()?.firstOrNull { it.secretHash == secretHash && it.isComplete }
            ?.statusFor(memberId, nowMillis())
    }

    /** Records that [secret] was spent admitting [memberId] to [spaceId]. */
    suspend fun useInvite(spaceId: String, secret: String, memberId: String) =
        useInviteHash(spaceId, inviteSecretHash(secret), memberId)

    /** As [useInvite], for an invite known only by the hash of its secret. */
    suspend fun useInviteHash(spaceId: String, secretHash: String, memberId: String) {
        val changed = lock.withLock {
            val doc = open(spaceId) ?: return
            useRosterInvite(doc, secretHash, memberId).also { if (it) persist(spaceId, doc) }
        }
        if (changed) _changes.tryEmit(spaceId)
    }

    /** Revokes the invite with this [secretHash] in [spaceId]: no new request with it will be accepted. */
    suspend fun revokeInvite(spaceId: String, secretHash: String) {
        val changed = lock.withLock {
            val doc = open(spaceId) ?: return
            revokeRosterInvite(doc, secretHash).also { if (it) persist(spaceId, doc) }
        }
        if (changed) _changes.tryEmit(spaceId)
    }

    suspend fun generate(spaceId: String, state: SyncState): ByteArray? =
        lock.withLock { open(spaceId)?.generateSyncMessage(state)?.orElse(null) }

    /** True once the peer whose progress is [state] has replied with its heads and has everything [spaceId]'s roster has; false when this device has no such Space. */
    suspend fun isInSync(spaceId: String, state: SyncState): Boolean =
        lock.withLock { open(spaceId)?.let { state.isInSync(it) } ?: false }

    /** Applies a sync message to [spaceId]'s roster; false when this device has no such Space. */
    suspend fun receive(spaceId: String, state: SyncState, message: ByteArray): Boolean = lock.withLock {
        val doc = open(spaceId) ?: return@withLock false
        val before = doc.heads
        doc.receiveSyncMessage(state, message)
        if (!doc.heads.contentEquals(before)) persist(spaceId, doc)
        true
    }

    /** [spaceId]'s roster heads, saved as `encodeHeads` writes them (for the mailbox, ADR-020); null when this device has no such Space. */
    suspend fun heads(spaceId: String): ByteArray? = lock.withLock { open(spaceId)?.let { encodeHeads(it.heads) } }

    /** The roster changes since the heads [saved] by [heads] (everything, for none), for another device's [applyChanges]. */
    suspend fun changesSince(spaceId: String, saved: ByteArray): ByteArray? = lock.withLock { open(spaceId)?.changesSince(saved) }

    /** Applies changes from [changesSince] to [spaceId]'s roster and projects them, as [receive] does a sync message. */
    suspend fun applyChanges(spaceId: String, changes: ByteArray): Boolean {
        val applied = lock.withLock {
            val doc = open(spaceId) ?: return false
            val before = doc.heads
            doc.applyEncodedChanges(changes)
            if (!doc.heads.contentEquals(before)) persist(spaceId, doc)
            true
        }
        project(spaceId)
        return applied
    }

    /**
     * [spaceId]'s roster as a [SpaceDocument], for the registry; null when this device has no roster for it yet (no such
     * Space), as the mailbox has always treated it.
     */
    suspend fun spaceDocument(spaceId: String): SpaceDocument? {
        if (heads(spaceId) == null) return null
        return object : SpaceDocument {
            override suspend fun generateSync(state: SyncState) = generate(spaceId, state)
            override suspend fun receiveSync(state: SyncState, message: ByteArray) { receive(spaceId, state, message) }
            override suspend fun isInSync(state: SyncState) = this@SpaceRosters.isInSync(spaceId, state)
            override suspend fun heads() = this@SpaceRosters.heads(spaceId) ?: ByteArray(0)
            override suspend fun changesSince(saved: ByteArray) = this@SpaceRosters.changesSince(spaceId, saved) ?: ByteArray(0)
            override suspend fun applyChanges(changes: ByteArray) { this@SpaceRosters.applyChanges(spaceId, changes) }
        }
    }

    /** Brings [spaceId]'s `HomeMember` rows in line with its roster: adds and updates members, and drops the removed. */
    suspend fun project(spaceId: String) = lock.withLock {
        val doc = open(spaceId) ?: return@withLock
        doc.readRoster().forEach { member ->
            when {
                member.removed -> database.homeMemberQueries.deleteMember(spaceId, member.memberId)
                // A member whose fields haven't all arrived yet is skipped; a later message completes it.
                !member.isComplete -> Unit
                else -> database.homeMemberQueries.upsert(
                    spaceId, member.memberId, member.name, member.avatar, member.role, member.joinedAt,
                )
            }
        }
    }

    private fun nameOwnDevice(doc: Document, agentId: String): Boolean =
        deviceName().trim().takeIf { it.isNotEmpty() }?.let { setRosterDeviceName(doc, agentId, it) } ?: false

    /** What each device of [spaceId]'s roster calls itself, by agent id; a device that has not said (an older version, or not yet synced) is left out. */
    suspend fun deviceNames(spaceId: String): Map<String, String> = lock.withLock { open(spaceId)?.readRosterDeviceNames().orEmpty() }

    /** The agents that act for each member of [spaceId], by member id. */
    suspend fun devices(spaceId: String): Map<String, Set<String>> = lock.withLock {
        open(spaceId)?.readRoster()?.filterNot { it.removed }?.associate { it.memberId to it.devices }.orEmpty()
    }

    /** Every member of [spaceId]'s roster, those marked removed included. */
    suspend fun members(spaceId: String): List<RosterMember> = lock.withLock { open(spaceId)?.readRoster().orEmpty() }

    /** The devices [spaceId]'s roster marks as unpaired by the member they were claimed for (docs/plans/unpaired-device-gap-plan.md). */
    suspend fun goneDevices(spaceId: String): Set<String> = lock.withLock { open(spaceId)?.readRosterGoneDevices().orEmpty() }

    /** The id of [spaceId]'s x0x group as its roster names it (docs/adr/ADR-021-home-x0x-group.md), or null. */
    suspend fun groupId(spaceId: String): String? = lock.withLock { open(spaceId)?.readRosterGroup() }

    /** Names [groupId] as [spaceId]'s x0x group, for every device of the Space to learn. */
    suspend fun setGroup(spaceId: String, groupId: String) {
        val changed = lock.withLock {
            val doc = open(spaceId) ?: return
            setRosterGroup(doc, groupId).also { if (it) persist(spaceId, doc) }
        }
        if (changed) _changes.tryEmit(spaceId)
    }

    /** The agents [spaceId]'s roster records as its always-on hubs (docs/adr/ADR-020). */
    suspend fun hubs(spaceId: String): Set<String> = lock.withLock { open(spaceId)?.readRosterHubs().orEmpty() }

    /** Records [agentId] as one of [spaceId]'s hubs, for every device of the Space to learn. */
    suspend fun addHub(spaceId: String, agentId: String) {
        val changed = lock.withLock {
            val doc = open(spaceId) ?: return
            addRosterHub(doc, agentId).also { if (it) persist(spaceId, doc) }
        }
        if (changed) _changes.tryEmit(spaceId)
    }

    /** Forgets [agentId] as one of [spaceId]'s hubs. */
    suspend fun removeHub(spaceId: String, agentId: String) {
        val changed = lock.withLock {
            val doc = open(spaceId) ?: return
            removeRosterHub(doc, agentId).also { if (it) persist(spaceId, doc) }
        }
        if (changed) _changes.tryEmit(spaceId)
    }

    private suspend fun open(spaceId: String): Document? {
        if (database.homeQueries.selectById(spaceId).awaitAsOneOrNull() == null) {
            documents.remove(spaceId)
            return null
        }
        documents[spaceId]?.let { return it }
        val stored = database.automergeDocumentQueries.selectById(rosterDocumentId(spaceId)).awaitAsOneOrNull()
        return (stored?.let { Document.load(it.bytes) } ?: Document()).also { documents[spaceId] = it }
    }

    private suspend fun persist(spaceId: String, doc: Document) {
        database.automergeDocumentQueries.upsert(rosterDocumentId(spaceId), spaceId, RosterFeature, doc.save(), nowMillis())
    }
}
