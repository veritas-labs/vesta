package uk.veritaslabs.vesta.datasync

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
import uk.veritaslabs.vesta.automerge.InviteStatus
import uk.veritaslabs.vesta.automerge.RosterInvite
import uk.veritaslabs.vesta.automerge.addRosterHub
import uk.veritaslabs.vesta.automerge.claimRosterDevice
import uk.veritaslabs.vesta.automerge.clearRosterGone
import uk.veritaslabs.vesta.automerge.markRosterGone
import uk.veritaslabs.vesta.automerge.inviteSecretHash
import uk.veritaslabs.vesta.automerge.newInviteSecret
import uk.veritaslabs.vesta.automerge.putRosterInvite
import uk.veritaslabs.vesta.automerge.readInvites
import uk.veritaslabs.vesta.automerge.revokeRosterInvite
import uk.veritaslabs.vesta.automerge.useRosterInvite
import uk.veritaslabs.vesta.homejoin.InviteLink
import uk.veritaslabs.vesta.automerge.RosterMember
import uk.veritaslabs.vesta.automerge.putRosterMember
import uk.veritaslabs.vesta.automerge.readRosterGroup
import uk.veritaslabs.vesta.automerge.readRosterHubs
import uk.veritaslabs.vesta.automerge.removeRosterHub
import uk.veritaslabs.vesta.automerge.setRosterGroup
import uk.veritaslabs.vesta.automerge.readRoster
import uk.veritaslabs.vesta.automerge.releaseRosterDevice
import uk.veritaslabs.vesta.automerge.retireRosterMember
import uk.veritaslabs.vesta.automerge.rosterDocumentId
import uk.veritaslabs.vesta.automerge.setRosterRole
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.mailbox.changesSince
import uk.veritaslabs.vesta.mailbox.encodeHeads
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.MemberRole

private const val RosterFeature = "roster"

/**
 * Each Home's roster as an Automerge document (docs/adr/ADR-014-home-membership.md) that syncs between the user's
 * devices. The `HomeMember` table stays what the app reads: [publish] writes this device's own member and agent into
 * the document, and [project] copies the document's members into the table. Every document belongs to an existing
 * Home, and [generate] and [receive] ignore a Home this device doesn't have.
 */
internal class HomeRosters(
    private val database: VestaDatabase,
    private val profiles: ProfileRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val lock = Mutex()
    private val documents = mutableMapOf<String, Document>()
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** The id of a Home each time its roster is changed on this device, so it can be pushed to the other devices. */
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    /**
     * Writes the current user's member of [homeId], and [agentId] (if known) as one of their devices, into that
     * Home's roster. A device with no member row for the user yet (a Home that has just arrived) does nothing until
     * the roster has brought one. Once [agentId] is known, any other device the roster lists for the user that isn't
     * in [pairedAgents] (the devices this one is paired with) is released: it has been removed from the user's
     * devices, so it no longer acts for them. The member stays, with fewer devices (ADR-014 §4).
     */
    suspend fun publish(homeId: String, agentId: String?, pairedAgents: Set<String>) {
        val me = profiles.current().id
        val row = database.homeMemberQueries.selectByHome(homeId).awaitAsList().firstOrNull { it.memberId == me } ?: return
        val changed = lock.withLock {
            val doc = open(homeId) ?: return
            val member = putRosterMember(doc, row.memberId, row.name, row.avatarResourceId, row.role, row.joinedAt)
            val device = agentId?.let { claimRosterDevice(doc, it, row.memberId) } ?: false
            val released = agentId != null && doc.readRoster().firstOrNull { it.memberId == row.memberId }?.devices.orEmpty()
                .filter { it != agentId && it !in pairedAgents }
                .map { releaseRosterDevice(doc, it, row.memberId) }
                .any { it }
            (member || device || released).also { if (it) persist(homeId, doc) }
        }
        if (changed) _changes.tryEmit(homeId)
    }

    /**
     * Admits [memberId] to [homeId]'s roster with [role] and [agentId] as their device, and copies them into the
     * `HomeMember` table. A member who is already there keeps their role and join time.
     */
    suspend fun admit(homeId: String, memberId: String, name: String, role: String, agentId: String) {
        val changed = lock.withLock {
            val doc = open(homeId) ?: return
            val member = putRosterMember(doc, memberId, name, null, role, nowMillis())
            val device = claimRosterDevice(doc, agentId, memberId)
            (member || device).also { if (it) persist(homeId, doc) }
        }
        project(homeId)
        if (changed) _changes.tryEmit(homeId)
    }

    /** Sets [memberId]'s role in [homeId]'s roster to [role], an admin's decision, which overwrites the role the roster had. */
    suspend fun setRole(homeId: String, memberId: String, role: String) {
        val changed = lock.withLock {
            val doc = open(homeId) ?: return
            setRosterRole(doc, memberId, role).also { if (it) persist(homeId, doc) }
        }
        if (changed) _changes.tryEmit(homeId)
    }

    /** Marks [memberId] removed from [homeId]: they leave the `HomeMember` table, and their devices stop being peers. */
    suspend fun remove(homeId: String, memberId: String) {
        val changed = lock.withLock {
            val doc = open(homeId) ?: return
            retireRosterMember(doc, memberId).also { if (it) persist(homeId, doc) }
        }
        project(homeId)
        if (changed) _changes.tryEmit(homeId)
    }

    /** Marks [memberId] removed in every Home that has them: that Profile id has been replaced by the person's shared one. */
    suspend fun retire(memberId: String) {
        val changed = lock.withLock {
            database.homeQueries.selectAll().awaitAsList().mapNotNull { home ->
                val doc = open(home.id) ?: return@mapNotNull null
                home.id.takeIf { retireRosterMember(doc, memberId).also { changed -> if (changed) persist(home.id, doc) } }
            }
        }
        changed.forEach { _changes.tryEmit(it) }
    }

    /**
     * Marks [agentId], one of this user's devices, as unpaired in every Home whose roster lists it as theirs
     * (docs/plans/unpaired-device-gap-plan.md): it stops being a peer, and an admin device takes it out of the Home's group. Called when
     * the user unpairs it, not when a pairing list merely lacks it. A Home that does not list it as this user's is left as it is.
     */
    suspend fun markGone(agentId: String) {
        val me = profiles.current().id
        val changed = lock.withLock {
            database.homeQueries.selectAll().awaitAsList().mapNotNull { home ->
                val doc = open(home.id) ?: return@mapNotNull null
                home.id.takeIf { markRosterGone(doc, agentId, me).also { changed -> if (changed) persist(home.id, doc) } }
            }
        }
        changed.forEach { _changes.tryEmit(it) }
    }

    /** Forgets that [agentId] was unpaired, in every Home that says so: the user paired it again. */
    suspend fun clearGone(agentId: String) {
        val changed = lock.withLock {
            database.homeQueries.selectAll().awaitAsList().mapNotNull { home ->
                val doc = open(home.id) ?: return@mapNotNull null
                home.id.takeIf { clearRosterGone(doc, agentId).also { changed -> if (changed) persist(home.id, doc) } }
            }
        }
        changed.forEach { _changes.tryEmit(it) }
    }

    /**
     * Makes an invite link for [homeId] on behalf of [memberId], who must be an admin the roster still lists
     * (docs/adr/ADR-018-invite-links.md), and records it in the roster. The link names the devices of the Home's admins,
     * [memberId]'s first, up to [InviteLink.MaxAdminAgents]. With [ownAgentId], this device's agent, [memberId] (this
     * user) and that device are first written into the roster from the `HomeMember` table: a Home just made hasn't
     * published them yet, and the device is an admin device by definition. Null when [memberId] isn't an admin of this
     * Home or no admin device is known.
     */
    suspend fun createInvite(
        homeId: String,
        memberId: String,
        ttlMillis: Long,
        maxUses: Int = 1,
        ownAgentId: String? = null,
    ): InviteLink? {
        val link = lock.withLock {
            val doc = open(homeId) ?: return null
            ownAgentId?.let { agent ->
                // As [publish] would, so a Home made a moment ago doesn't have to wait for the next sync round.
                database.homeMemberQueries.selectByHome(homeId).awaitAsList().firstOrNull { it.memberId == memberId }?.let { row ->
                    putRosterMember(doc, row.memberId, row.name, row.avatarResourceId, row.role, row.joinedAt)
                    claimRosterDevice(doc, agent, memberId)
                }
            }
            if (doc.readRoster().none { it.memberId == memberId && it.role == MemberRole.Admin.storageValue && !it.removed }) return null
            val admins = doc.readRoster().filter { it.role == MemberRole.Admin.storageValue && !it.removed }
            val agents = admins.sortedBy { it.memberId != memberId }.flatMap { it.devices.sorted() }
                .distinct().take(InviteLink.MaxAdminAgents).takeIf { it.isNotEmpty() } ?: return null
            val homeName = database.homeQueries.selectById(homeId).awaitAsOneOrNull()?.name ?: return null
            val secret = newInviteSecret()
            val expiresAt = nowMillis() + ttlMillis
            putRosterInvite(doc, inviteSecretHash(secret), memberId, expiresAt, maxUses)
            persist(homeId, doc)
            InviteLink(homeId, secret, expiresAt, agents, homeName)
        }
        _changes.tryEmit(homeId)
        return link
    }

    /** Every invite recorded for [homeId], for an admin to see and revoke. */
    suspend fun invites(homeId: String): List<RosterInvite> =
        lock.withLock { open(homeId)?.readInvites()?.filter { it.isComplete }.orEmpty() }

    /**
     * Whether [secret] can admit [memberId] to [homeId] at the current time; null when this Home has no such invite (or
     * it hasn't fully arrived), which callers answer silently so a stranger learns nothing.
     */
    suspend fun checkInvite(homeId: String, secret: String, memberId: String): InviteStatus? =
        inviteStatus(homeId, inviteSecretHash(secret), memberId)

    /** As [checkInvite], for an invite known only by the hash of its secret. */
    suspend fun inviteStatus(homeId: String, secretHash: String, memberId: String): InviteStatus? = lock.withLock {
        open(homeId)?.readInvites()?.firstOrNull { it.secretHash == secretHash && it.isComplete }
            ?.statusFor(memberId, nowMillis())
    }

    /** Records that [secret] was spent admitting [memberId] to [homeId]. */
    suspend fun useInvite(homeId: String, secret: String, memberId: String) =
        useInviteHash(homeId, inviteSecretHash(secret), memberId)

    /** As [useInvite], for an invite known only by the hash of its secret. */
    suspend fun useInviteHash(homeId: String, secretHash: String, memberId: String) {
        val changed = lock.withLock {
            val doc = open(homeId) ?: return
            useRosterInvite(doc, secretHash, memberId).also { if (it) persist(homeId, doc) }
        }
        if (changed) _changes.tryEmit(homeId)
    }

    /** Revokes the invite with this [secretHash] in [homeId]: no new request with it will be accepted. */
    suspend fun revokeInvite(homeId: String, secretHash: String) {
        val changed = lock.withLock {
            val doc = open(homeId) ?: return
            revokeRosterInvite(doc, secretHash).also { if (it) persist(homeId, doc) }
        }
        if (changed) _changes.tryEmit(homeId)
    }

    suspend fun generate(homeId: String, state: SyncState): ByteArray? =
        lock.withLock { open(homeId)?.generateSyncMessage(state)?.orElse(null) }

    /** Applies a sync message to [homeId]'s roster; false when this device has no such Home. */
    suspend fun receive(homeId: String, state: SyncState, message: ByteArray): Boolean = lock.withLock {
        val doc = open(homeId) ?: return@withLock false
        val before = doc.heads
        doc.receiveSyncMessage(state, message)
        if (!doc.heads.contentEquals(before)) persist(homeId, doc)
        true
    }

    /** [homeId]'s roster heads, saved as `encodeHeads` writes them (for the mailbox, ADR-020); null when this device has no such Home. */
    suspend fun heads(homeId: String): ByteArray? = lock.withLock { open(homeId)?.let { encodeHeads(it.heads) } }

    /** The roster changes since the heads [saved] by [heads] (everything, for none), for another device's [applyChanges]. */
    suspend fun changesSince(homeId: String, saved: ByteArray): ByteArray? = lock.withLock { open(homeId)?.changesSince(saved) }

    /** Applies changes from [changesSince] to [homeId]'s roster and projects them, as [receive] does a sync message. */
    suspend fun applyChanges(homeId: String, changes: ByteArray): Boolean {
        val applied = lock.withLock {
            val doc = open(homeId) ?: return false
            val before = doc.heads
            doc.applyEncodedChanges(changes)
            if (!doc.heads.contentEquals(before)) persist(homeId, doc)
            true
        }
        project(homeId)
        return applied
    }

    /** Brings [homeId]'s `HomeMember` rows in line with its roster: adds and updates members, and drops the removed. */
    suspend fun project(homeId: String) = lock.withLock {
        val doc = open(homeId) ?: return@withLock
        doc.readRoster().forEach { member ->
            when {
                member.removed -> database.homeMemberQueries.deleteMember(homeId, member.memberId)
                // A member whose fields haven't all arrived yet is skipped; a later message completes it.
                !member.isComplete -> Unit
                else -> database.homeMemberQueries.upsert(
                    homeId, member.memberId, member.name, member.avatar, member.role, member.joinedAt,
                )
            }
        }
    }

    /** The agents that act for each member of [homeId], by member id. */
    suspend fun devices(homeId: String): Map<String, Set<String>> = lock.withLock {
        open(homeId)?.readRoster()?.filterNot { it.removed }?.associate { it.memberId to it.devices }.orEmpty()
    }

    /** Every member of [homeId]'s roster, those marked removed included. */
    suspend fun members(homeId: String): List<RosterMember> = lock.withLock { open(homeId)?.readRoster().orEmpty() }

    /** The id of [homeId]'s x0x group as its roster names it (docs/adr/ADR-021-home-x0x-group.md), or null. */
    suspend fun groupId(homeId: String): String? = lock.withLock { open(homeId)?.readRosterGroup() }

    /** Names [groupId] as [homeId]'s x0x group, for every device of the Home to learn. */
    suspend fun setGroup(homeId: String, groupId: String) {
        val changed = lock.withLock {
            val doc = open(homeId) ?: return
            setRosterGroup(doc, groupId).also { if (it) persist(homeId, doc) }
        }
        if (changed) _changes.tryEmit(homeId)
    }

    /** The agents [homeId]'s roster records as its always-on hubs (docs/adr/ADR-020). */
    suspend fun hubs(homeId: String): Set<String> = lock.withLock { open(homeId)?.readRosterHubs().orEmpty() }

    /** Records [agentId] as one of [homeId]'s hubs, for every device of the Home to learn. */
    suspend fun addHub(homeId: String, agentId: String) {
        val changed = lock.withLock {
            val doc = open(homeId) ?: return
            addRosterHub(doc, agentId).also { if (it) persist(homeId, doc) }
        }
        if (changed) _changes.tryEmit(homeId)
    }

    /** Forgets [agentId] as one of [homeId]'s hubs. */
    suspend fun removeHub(homeId: String, agentId: String) {
        val changed = lock.withLock {
            val doc = open(homeId) ?: return
            removeRosterHub(doc, agentId).also { if (it) persist(homeId, doc) }
        }
        if (changed) _changes.tryEmit(homeId)
    }

    private suspend fun open(homeId: String): Document? {
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) {
            documents.remove(homeId)
            return null
        }
        documents[homeId]?.let { return it }
        val stored = database.automergeDocumentQueries.selectById(rosterDocumentId(homeId)).awaitAsOneOrNull()
        return (stored?.let { Document.load(it.bytes) } ?: Document()).also { documents[homeId] = it }
    }

    private suspend fun persist(homeId: String, doc: Document) {
        database.automergeDocumentQueries.upsert(rosterDocumentId(homeId), homeId, RosterFeature, doc.save(), nowMillis())
    }
}
