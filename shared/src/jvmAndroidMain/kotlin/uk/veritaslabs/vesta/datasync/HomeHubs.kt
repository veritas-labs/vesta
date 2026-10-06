package uk.veritaslabs.vesta.datasync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.homegroup.HomeHub
import uk.veritaslabs.vesta.homegroup.HubEnrolment
import uk.veritaslabs.vesta.homegroup.HubResult
import uk.veritaslabs.vesta.homegroup.HubState
import uk.veritaslabs.vesta.profile.ProfileRepository
import uk.veritaslabs.vesta.ui.screens.MemberRole
import uk.veritaslabs.vesta.x0x.X0xApiException
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xGroupMember

private const val AdminRole = "admin"
private const val ActiveState = "active"
private const val MailboxStore = "mailbox"
private const val KeyPackagePending = "member_key_package_pending"

/**
 * [HomeHub] over the Home's x0x group ([HomeGroups]) and its roster (docs/adr/ADR-020, slice 6). The hub is recorded in the
 * roster, so every member's device learns it, and [HomeGroups] keeps it an x0x admin. It is never one of the roster's device
 * claims: it is an agent in the group that acts for nobody.
 */
internal class HomeHubs(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val rosters: HomeRosters,
    private val profiles: ProfileRepository,
    private val removeRetryDelay: Duration = 3.seconds,
    private val removeAttempts: Int = 5,
) : HomeHub {

    override suspend fun enrolment(homeId: String): HubEnrolment? {
        val group = readyGroup(homeId) ?: return null
        if (!isAdminOfHome(homeId) || !isAdminOfGroup(group)) return null
        val invite = client.createGroupInvite(group)
        return HubEnrolment(group, "x0x group join $invite\nx0x group store create $group $MailboxStore")
    }

    override suspend fun state(homeId: String): HubState {
        val hub = rosters.hub(homeId) ?: return HubState(null, inGroup = false)
        val inGroup = localGroup(homeId)?.let { group ->
            runCatching { client.getGroupMembers(group) }.getOrNull()?.any { it.agentId == hub && it.state == ActiveState } == true
        } == true
        return HubState(hub, inGroup)
    }

    override suspend fun candidates(homeId: String): List<String> {
        val group = localGroup(homeId) ?: return emptyList()
        val members = runCatching { client.getGroupMembers(group) }.getOrNull() ?: return emptyList()
        val known = rosters.members(homeId).flatMap { it.devices }.toSet() + setOfNotNull(rosters.hub(homeId), selfId())
        return members.filter { it.state == ActiveState && it.agentId !in known }.map { it.agentId }
    }

    override suspend fun useAsHub(homeId: String, agentId: String): HubResult {
        val group = localGroup(homeId) ?: return HubResult.Failed
        if (!isAdminOfHome(homeId)) return HubResult.NotAnAdmin
        if (rosters.members(homeId).any { agentId in it.devices }) return HubResult.ActsForAMember
        val active = try {
            client.getGroupMembers(group).any { it.agentId == agentId && it.state == ActiveState }
        } catch (e: X0xApiException) {
            return HubResult.Failed
        }
        if (!active) return HubResult.NotInGroup
        rosters.setHub(homeId, agentId)
        // Any x0x admin keeps the hub an admin each round; doing it now just saves the wait.
        attempt { client.setGroupMemberRole(group, agentId, AdminRole) }
        return HubResult.Done
    }

    override suspend fun removeHub(homeId: String): HubResult {
        val hub = rosters.hub(homeId) ?: return HubResult.Done
        if (!isAdminOfHome(homeId)) return HubResult.NotAnAdmin
        val group = localGroup(homeId)
        if (group != null) {
            val members = try {
                client.getGroupMembers(group)
            } catch (e: X0xApiException) {
                return HubResult.Failed
            }
            if (members.any { it.agentId == hub && it.state == ActiveState }) {
                if (!removeFromGroup(group, hub)) return HubResult.Failed
            }
        }
        rosters.clearHub(homeId)
        return HubResult.Done
    }

    /** Removes [agentId], retrying while x0x is still fetching its key package; false when it could not be removed. */
    private suspend fun removeFromGroup(group: String, agentId: String): Boolean {
        var attemptsLeft = removeAttempts
        while (true) {
            try {
                client.removeGroupMember(group, agentId)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: X0xApiException) {
                if (e.message != KeyPackagePending || --attemptsLeft <= 0) return false
                delay(removeRetryDelay)
            }
        }
    }

    private suspend fun localGroup(homeId: String): String? = database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.groupId

    /** The Home's group, once this device is in it and has the mailbox open. */
    private suspend fun readyGroup(homeId: String): String? =
        database.homeGroupQueries.selectByHome(homeId).awaitAsOneOrNull()?.takeIf { it.storeId != null }?.groupId

    private suspend fun isAdminOfHome(homeId: String): Boolean {
        val me = profiles.current().id
        return rosters.members(homeId).any { it.memberId == me && !it.removed && it.role == MemberRole.Admin.storageValue }
    }

    private suspend fun isAdminOfGroup(group: String): Boolean {
        val self = selfId() ?: return false
        val members: List<X0xGroupMember> = runCatching { client.getGroupMembers(group) }.getOrNull() ?: return false
        return members.firstOrNull { it.agentId == self }?.role == AdminRole
    }

    private suspend fun selfId(): String? = runCatching { client.agent().agentId }.getOrNull()

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
