package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.automerge.RosterMember
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.homegroup.HomeGroupMessage
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.MemberRole
import uk.veritaslabs.vesta.x0x.X0xApiException
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage
import uk.veritaslabs.vesta.x0x.X0xGroupMember

private const val GroupName = "Vesta Home"
private const val MailboxStore = "mailbox"
private const val AdminRole = "admin"
private const val MemberRoleName = "member"
private const val ActiveState = "active"

/**
 * Each Home's x0x group (docs/adr/ADR-021-home-x0x-group.md), which carries the `mailbox` store of ADR-020. The Home's
 * roster names the group; this keeps this device's side of it, in the `HomeGroup` table:
 *
 * - **Create.** The founder's device (the earliest-joined admin member's) creates the group, writes its id to the roster
 *   and opens the store.
 * - **Join.** Any other device that has the Home but no group asks the admins' devices for an invite every round
 *   ([reconcile]); an admin device that is an x0x admin answers ([handle]); the asker joins and opens the store.
 * - **Roles and removal.** An x0x admin makes the devices of Vesta admins x0x admins, demotes those of members who stopped
 *   being admins (the hubs the roster names are x0x admins too), and removes the devices of members who left or were removed. Removing the group's creator replaces
 *   the group, which x0x needs because it won't let another admin remove it.
 * - **Leave.** A Home this device no longer has is left.
 *
 * Everything runs from [reconcile], once per sync round, and is safe to repeat. A daemon that is down or too old to open group
 * stores only makes a round do nothing.
 */
internal class HomeGroups(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val rosters: HomeRosters,
    private val profiles: ProfileRepository,
    private val pollInterval: Duration = 1.seconds,
    private val joinTimeout: Duration = 10.seconds,
) {

    // Reconciling, answering and replacing all read-modify-write the same group, so run one at a time.
    private val lock = Mutex()

    /** The id of [homeId]'s `mailbox` store once this device has opened it, otherwise null. */
    suspend fun mailboxStoreId(homeId: String): String? = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.storeId

    /** Brings every Home's group in line with its roster; one Home failing doesn't stop the rest. */
    suspend fun reconcile() = lock.withLock {
        database.homeGroupQueries.selectAll().awaitAsList().forEach { row ->
            if (database.homeQueries.selectById(row.homeId).awaitAsOneOrNull() == null) {
                attempt { client.leaveGroup(row.groupId) }
                database.homeGroupQueries.delete(row.homeId)
            }
        }
        database.homeQueries.selectAll().awaitAsList().forEach { home -> attempt { reconcileHome(home.id) } }
    }

    /** Handles a direct message if it is a Home-group message; returns whether it was one, so the caller can stop there. */
    suspend fun handle(message: X0xDirectMessage): Boolean {
        val decoded = HomeGroupMessage.decode(message.payload) ?: return false
        if (message.verified != true) return true
        val sender = message.sender ?: return true
        lock.withLock {
            attempt {
                when (decoded) {
                    is HomeGroupMessage.InviteRequest -> answerRequest(sender, decoded.homeId)
                    is HomeGroupMessage.Invite -> acceptInvite(sender, decoded)
                }
            }
        }
        return true
    }

    private suspend fun reconcileHome(homeId: String) {
        val self = selfId()
        val rosterGroup = rosters.groupId(homeId)
        var local = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()

        if (local != null && rosterGroup != null && rosterGroup != local.groupId) {
            // Another device's group won a race, or the creator was replaced: leave ours and ask for an invite.
            attempt { client.leaveGroup(local!!.groupId) }
            database.homeGroupQueries.delete(homeId)
            local = null
        }
        if (local == null) {
            when {
                rosterGroup != null -> requestInvite(homeId, rosterGroup, self)
                isFounder(homeId, self) -> create(homeId)
            }
            return
        }
        if (rosterGroup == null) rosters.setGroup(homeId, local.groupId)

        val members = try {
            client.getGroupMembers(local.groupId)
        } catch (e: X0xApiException) {
            // Removed from the group (or it is gone): forget it, and ask for an invite next round.
            if (e.status == 404) database.homeGroupQueries.delete(homeId)
            return
        }
        val me = members.firstOrNull { it.agentId == self }?.takeIf { it.state == ActiveState } ?: return // admission still committing
        if (local.storeId == null) openStore(homeId, local.groupId)
        if (me.role == AdminRole) {
            val roster = rosters.members(homeId)
            val departed = departedDevices(roster)
            // A hub that is the device of a member who left goes with them (docs/plans/ADR-020-several-hubs-plan.md, decision 2):
            // no longer a hub, so it is not kept an admin, and it is removed from the group below like their other devices.
            val hubs = rosters.hubs(homeId)
            hubs.filter { it in departed }.forEach { rosters.removeHub(homeId, it) }
            adjustRoles(local.groupId, self, members, roster, hubs = hubs - departed)
            removeDeparted(homeId, local.groupId, self, members, departed)
        }
    }

    // --- Creating and joining ---

    /** The device of the earliest-joined admin member, which is the one that creates the group. */
    private suspend fun isFounder(homeId: String, self: String): Boolean {
        val founder = rosters.members(homeId)
            .filter { !it.removed && it.isComplete && it.role == MemberRole.Admin.storageValue }
            .minWithOrNull(compareBy({ it.joinedAt }, { it.memberId })) ?: return false
        return founder.memberId == profiles.current().id && self in founder.devices
    }

    private suspend fun create(homeId: String) {
        val groupId = client.createGroup(GroupName)
        rosters.setGroup(homeId, groupId)
        database.homeGroupQueries.upsert(homeId, groupId, null)
        attempt { openStore(homeId, groupId) }
    }

    private suspend fun requestInvite(homeId: String, groupId: String, self: String) {
        // Already a member, but this device lost its record of it (a restored database): nothing to ask.
        val alreadyIn = runCatching { client.getGroupMembers(groupId) }.getOrNull()
            ?.any { it.agentId == self && it.state == ActiveState } == true
        if (alreadyIn) {
            database.homeGroupQueries.upsert(homeId, groupId, null)
            attempt { openStore(homeId, groupId) }
            return
        }
        val message = HomeGroupMessage.encode(HomeGroupMessage.InviteRequest(homeId))
        adminDevices(rosters.members(homeId)).minus(self).forEach { sendQuietly(it, message) }
    }

    /** An admin device answers a request with an invite, if it is an x0x admin of the group and the asker is on the roster. */
    private suspend fun answerRequest(sender: String, homeId: String) {
        val local = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull() ?: return
        if (sender !in activeDevices(rosters.members(homeId))) return
        val members = client.getGroupMembers(local.groupId)
        if (members.firstOrNull { it.agentId == selfId() }?.role != AdminRole) return
        val invite = client.createGroupInvite(local.groupId)
        sendQuietly(sender, HomeGroupMessage.encode(HomeGroupMessage.Invite(homeId, local.groupId, invite)))
    }

    private suspend fun acceptInvite(sender: String, message: HomeGroupMessage.Invite) {
        val homeId = message.homeId
        if (database.homeQueries.selectById(homeId).awaitAsOneOrNull() == null) return
        if (database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull() != null) return
        if (rosters.groupId(homeId) != message.groupId) return
        if (sender !in adminDevices(rosters.members(homeId))) return
        val self = selfId()
        val joined = client.joinGroup(message.invite)
        if (joined != message.groupId) {
            attempt { client.leaveGroup(joined) }
            return
        }
        // Our admission is committed by the inviter, so only record the group once we can see ourselves in it.
        if (!awaitActive(joined, self)) return
        database.homeGroupQueries.upsert(homeId, joined, null)
        attempt { openStore(homeId, joined) }
    }

    private suspend fun openStore(homeId: String, groupId: String) {
        val storeId = client.openGroupStore(groupId, MailboxStore)
        database.homeGroupQueries.setStore(storeId, homeId)
    }

    // --- Roles and removal ---

    private suspend fun adjustRoles(
        groupId: String,
        self: String,
        members: List<X0xGroupMember>,
        roster: List<RosterMember>,
        hubs: Set<String>,
    ) {
        // The hubs (docs/adr/ADR-020) are admins so they can admit joiners while the phones are away; a hub may also be a member's device.
        val admins = roster.filter { !it.removed && it.role == MemberRole.Admin.storageValue }.flatMap { it.devices }.toSet() + hubs
        val plain = roster.filter { !it.removed && it.role != MemberRole.Admin.storageValue }.flatMap { it.devices }.toSet() - admins
        val creator = runCatching { client.getGroupCreator(groupId) }.getOrNull()
        members.filter { it.state == ActiveState }.forEach { member ->
            when {
                member.agentId in admins && member.role != AdminRole ->
                    attempt { client.setGroupMemberRole(groupId, member.agentId, AdminRole) }
                member.agentId in plain && member.role == AdminRole && member.agentId != creator && member.agentId != self ->
                    attempt { client.setGroupMemberRole(groupId, member.agentId, MemberRoleName) }
            }
        }
    }

    /** The devices of members who left or were removed, unless another member now claims them. */
    private fun departedDevices(roster: List<RosterMember>): Set<String> =
        roster.filter { it.removed }.flatMap { it.devices }.toSet() - activeDevices(roster)

    /** Removes the [departed] devices; the group's creator can only go by replacing the group. */
    private suspend fun removeDeparted(
        homeId: String,
        groupId: String,
        self: String,
        members: List<X0xGroupMember>,
        departed: Set<String>,
    ) {
        val toRemove = members.filter { it.agentId in departed && it.agentId != self }
        if (toRemove.isEmpty()) return
        val creator = runCatching { client.getGroupCreator(groupId) }.getOrNull()
        if (toRemove.any { it.agentId == creator }) return replaceGroup(homeId, groupId)
        // A removal can fail (x0x still fetching a key package, say): the same diff is tried again next round.
        toRemove.forEach { attempt { client.removeGroupMember(groupId, it.agentId) } }
    }

    /**
     * Moves the Home to a new group created here, without the removed creator. The other devices see the roster name a
     * different group, leave the old one and ask for an invite; those offline do so when they next sync.
     */
    private suspend fun replaceGroup(homeId: String, oldGroup: String) {
        val groupId = client.createGroup(GroupName)
        rosters.setGroup(homeId, groupId)
        database.homeGroupQueries.upsert(homeId, groupId, null)
        attempt { client.leaveGroup(oldGroup) }
        attempt { openStore(homeId, groupId) }
    }

    // --- Helpers ---

    private fun activeDevices(roster: List<RosterMember>): Set<String> = roster.filter { !it.removed }.flatMap { it.devices }.toSet()

    private fun adminDevices(roster: List<RosterMember>): Set<String> =
        roster.filter { !it.removed && it.role == MemberRole.Admin.storageValue }.flatMap { it.devices }.toSet()

    private suspend fun selfId(): String = checkNotNull(client.agent().agentId) { "x0x did not report this device's agent id" }

    private suspend fun awaitActive(groupId: String, agentId: String): Boolean {
        var waited = Duration.ZERO
        while (true) {
            val active = try {
                client.getGroupMembers(groupId).any { it.agentId == agentId && it.state == ActiveState }
            } catch (e: X0xApiException) {
                false // the group isn't visible to us yet
            }
            if (active) return true
            if (waited >= joinTimeout) return false
            delay(pollInterval)
            waited += pollInterval
        }
    }

    private suspend fun sendQuietly(agentId: String, payload: ByteArray) {
        attempt {
            runCatching { client.connectAgent(agentId) }
            client.sendDirect(agentId, payload)
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
