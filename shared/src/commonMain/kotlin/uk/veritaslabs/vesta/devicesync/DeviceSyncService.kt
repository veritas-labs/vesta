package uk.veritaslabs.vesta.devicesync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.veritaslabs.vesta.db.VestaDatabase
import uk.veritaslabs.vesta.devicepairing.PairingLink
import uk.veritaslabs.vesta.x0x.X0xApiException
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage
import uk.veritaslabs.vesta.x0x.X0xEventHub
import uk.veritaslabs.vesta.x0x.X0xGroupMember
import uk.veritaslabs.vesta.getDeviceName

private const val GroupName = "DeviceSync"
private const val ApiKeyPackagePending = "member_key_package_pending"

/**
 * Keeps a user's own devices together over x0x (docs/adr/ADR-012-device-sync-over-x0x-group.md): every device is a
 * member, and an admin, of one private `DeviceSync` group.
 *
 * - **Pairing.** The new device shows a [PairingLink] naming its agent id. A device already in the group reads it
 *   ([pairWith]), invites that agent and sends the invite as an x0x direct message — end-to-end encrypted, so only
 *   the new device can use it. The new device ([handle]) joins, and the owner promotes it to admin.
 * - **Roster.** [reconcile] mirrors the group's members into [SyncedDeviceRepository], greets members it has just
 *   seen so devices learn each other's names, promotes plain members to admin, and notices when this device has
 *   been removed.
 * - **Removal.** [removeDevice] removes a member, which rotates the group's keys so it can't read what follows. The
 *   group's creator can't be removed by another admin (x0x lacks its key package), so removing it instead replaces
 *   the group: the remaining devices are invited to a fresh one and the old one is abandoned.
 *
 * Removal stops *future* sync only; data already on a removed device stays there.
 */
class DeviceSyncService(
    private val client: X0xClient,
    private val database: VestaDatabase,
    private val devices: SyncedDeviceRepository,
    private val deviceName: () -> String = ::getDeviceName,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val pollInterval: Duration = 2.seconds,
    private val joinTimeout: Duration = 90.seconds,
    private val ownViewTimeout: Duration = 10.seconds,
    private val resendInterval: Duration = 10.seconds,
    private val reconcileInterval: Duration = 30.seconds,
    private val removeRetryDelay: Duration = 3.seconds,
    private val removeAttempts: Int = 5,
) {
    // Pairing, message handling, reconciling and removal all read-modify-write the same group, so run one at a time.
    private val lock = Mutex()

    private val _joined = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the name of the device that added this one, once this device has joined its group. */
    val joined: SharedFlow<String> = _joined.asSharedFlow()

    /**
     * Runs for as long as it is collected: handles device-sync direct messages as they arrive and reconciles the
     * roster every [reconcileInterval]. Never returns normally.
     */
    suspend fun run() = coroutineScope {
        launch {
            while (true) {
                attempt { reconcile() }
                delay(reconcileInterval)
            }
        }
        X0xEventHub(client).directMessages.collect { message ->
            launch { attempt { handle(message) } }
        }
    }

    /**
     * Owner side of pairing: adds the device that showed [link] to this device's group (creating the group if this
     * is the first device to pair), waits for it to be admitted and makes it an admin.
     */
    suspend fun pairWith(link: PairingLink, onProgress: PairingProgressListener = { _, _ -> }): Result<Unit> = lock.withLock {
        attempt {
            var current = PairingStep.PrepareGroup
            fun begin(step: PairingStep) {
                current = step
                onProgress(step, PairingStepStatus.Running)
            }
            fun finish(step: PairingStep) = onProgress(step, PairingStepStatus.Done)
            try {
                begin(PairingStep.PrepareGroup)
                val self = selfId()
                require(link.agentId != self) { "that is this device's own pairing link" }
                val groupId = currentGroup() ?: client.createGroup(GroupName).also { setGroup(it) }
                finish(PairingStep.PrepareGroup)

                begin(PairingStep.CreateInvite)
                val invite = client.createGroupInvite(groupId)
                val message = DeviceSyncMessage.encode(DeviceSyncMessage.Invite(invite, deviceName()))
                finish(PairingStep.CreateInvite)

                begin(PairingStep.SendInvite)
                var delivered = false
                awaitActive(groupId, link.agentId) {
                    send(link.agentId, message)
                    if (!delivered) {
                        delivered = true
                        finish(PairingStep.SendInvite)
                        begin(PairingStep.AwaitJoin)
                    }
                }
                if (!delivered) finish(PairingStep.SendInvite) // joined before any send was confirmed
                finish(PairingStep.AwaitJoin)

                begin(PairingStep.GrantAdmin)
                attempt { client.setGroupMemberRole(groupId, link.agentId, "admin") }
                finish(PairingStep.GrantAdmin)

                begin(PairingStep.SaveDevice)
                devices.add(link.deviceName ?: shortName(link.agentId), link.agentId)
                finish(PairingStep.SaveDevice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onProgress(current, PairingStepStatus.Failed)
                throw e
            }
        }
    }

    /** Handles one direct message from x0x; anything that isn't a device-sync message from a verified sender is ignored. */
    suspend fun handle(message: X0xDirectMessage) = lock.withLock {
        if (message.verified != true) return@withLock
        val sender = message.sender ?: return@withLock
        when (val decoded = DeviceSyncMessage.decode(message.payload) ?: return@withLock) {
            is DeviceSyncMessage.Invite -> acceptInvite(sender, decoded)
            is DeviceSyncMessage.Hello -> acceptHello(sender, decoded)
            is DeviceSyncMessage.Rotate -> acceptRotate(sender, decoded)
        }
    }

    /** Brings the stored roster, and this device's own membership, in line with the group. */
    suspend fun reconcile() = lock.withLock {
        val groupId = currentGroup() ?: return@withLock
        val members = try {
            client.getGroupMembers(groupId)
        } catch (e: X0xApiException) {
            if (e.status == 404) leftGroup()
            return@withLock
        }
        val self = selfId()
        // A device that has been removed gets a 404 above. Not being listed yet just means our admission is still
        // being committed, so carry on with what we can see.
        val me = members.firstOrNull { it.agentId == self }
        val others = members.filter { it.agentId != self && it.state == "active" }
        val known = devices.agentIds()
        others.filter { it.agentId !in known }.forEach { member ->
            devices.add(shortName(member.agentId), member.agentId)
            attempt { send(member.agentId, DeviceSyncMessage.encode(DeviceSyncMessage.Hello(deviceName()))) }
        }
        (known - others.map { it.agentId }.toSet()).forEach { devices.remove(it) }
        if (me?.role == "admin") {
            others.filter { it.role != "admin" }.forEach { attempt { client.setGroupMemberRole(groupId, it.agentId, "admin") } }
        }
    }

    /** Removes the device with [agentId] from the group and the list; see the class comment for the creator case. */
    suspend fun removeDevice(agentId: String): Result<Unit> = lock.withLock {
        attempt {
            val groupId = currentGroup()
            if (groupId == null) {
                devices.remove(agentId)
                return@attempt
            }
            if (client.getGroupCreator(groupId) == agentId) {
                replaceGroupWithout(groupId, agentId)
            } else {
                removeMember(groupId, agentId)
            }
            devices.remove(agentId)
        }
    }

    // --- Receiving ---

    private suspend fun acceptInvite(sender: String, message: DeviceSyncMessage.Invite) {
        // Only while this device is showing a live pairing link, and only if it isn't already in a group.
        if (currentGroup() != null) return
        database.devicePairingLinkQueries.selectActive(nowMillis()).awaitAsOneOrNull() ?: return
        val self = selfId()
        val groupId = client.joinGroup(message.invite)
        // Remember the group at once: the inviter commits our admission, and our own view of the roster can lag it.
        setGroup(groupId)
        attempt { awaitActive(groupId, self, timeout = ownViewTimeout) }
        database.devicePairingLinkQueries.selectActive(nowMillis()).awaitAsOneOrNull()
            ?.let { database.devicePairingLinkQueries.markConsumed(it.id) }
        devices.add(message.name, sender)
        attempt { send(sender, DeviceSyncMessage.encode(DeviceSyncMessage.Hello(deviceName()))) }
        _joined.tryEmit(message.name)
    }

    private suspend fun acceptHello(sender: String, message: DeviceSyncMessage.Hello) {
        val groupId = currentGroup() ?: return
        if (members(groupId).none { it.agentId == sender && it.state == "active" }) return
        val isNew = sender !in devices.agentIds()
        devices.add(message.name, sender)
        // Whoever greets a device we hadn't met should hear our name back; greeting a known device stops the exchange.
        if (isNew) attempt { send(sender, DeviceSyncMessage.encode(DeviceSyncMessage.Hello(deviceName()))) }
    }

    private suspend fun acceptRotate(sender: String, message: DeviceSyncMessage.Rotate) {
        val oldGroup = currentGroup() ?: return
        if (oldGroup != message.oldGroupId) return
        if (members(oldGroup).none { it.agentId == sender && it.state == "active" }) return
        val newGroup = client.joinGroup(message.invite)
        setGroup(newGroup)
        attempt { awaitActive(newGroup, selfId(), timeout = ownViewTimeout) }
        attempt { client.leaveGroup(oldGroup) }
        devices.removeAll()
        devices.add(message.name, sender)
    }

    // --- Removing ---

    private suspend fun removeMember(groupId: String, agentId: String) {
        var attemptsLeft = removeAttempts
        while (true) {
            try {
                client.removeGroupMember(groupId, agentId)
                return
            } catch (e: X0xApiException) {
                // x0x is still fetching the member's key package: ask again shortly.
                if (e.message != ApiKeyPackagePending || --attemptsLeft <= 0) throw e
                delay(removeRetryDelay)
            }
        }
    }

    /**
     * The creator can't be removed by another admin, so move everyone else to a new group instead. Devices that are
     * offline for it are left behind with the old group and must be paired again.
     */
    private suspend fun replaceGroupWithout(oldGroup: String, removed: String) {
        val self = selfId()
        val remaining = members(oldGroup).filter { it.state == "active" && it.agentId != removed && it.agentId != self }
        val newGroup = client.createGroup(GroupName)
        val invite = client.createGroupInvite(newGroup)
        val message = DeviceSyncMessage.encode(DeviceSyncMessage.Rotate(invite, oldGroup, deviceName()))
        val moved = remaining.filter { member ->
            runCatching { awaitActive(newGroup, member.agentId) { send(member.agentId, message) } }.isSuccess
        }
        moved.forEach { attempt { client.setGroupMemberRole(newGroup, it.agentId, "admin") } }
        setGroup(newGroup)
        attempt { client.leaveGroup(oldGroup) }
        val known = devices.agentIds()
        moved.filter { it.agentId !in known }.forEach { devices.add(shortName(it.agentId), it.agentId) }
        (remaining - moved.toSet()).forEach { devices.remove(it.agentId) }
    }

    // --- Helpers ---

    private suspend fun selfId(): String = checkNotNull(client.agent().agentId) { "x0x did not report this device's agent id" }

    private suspend fun members(groupId: String): List<X0xGroupMember> = client.getGroupMembers(groupId)

    private suspend fun currentGroup(): String? = database.deviceSyncGroupQueries.select().awaitAsOneOrNull()

    private suspend fun setGroup(groupId: String) {
        database.deviceSyncGroupQueries.clear()
        database.deviceSyncGroupQueries.insert(groupId)
    }

    /** This device is no longer in its group (removed from it, or the group is gone): forget it and the roster. */
    private suspend fun leftGroup() {
        database.deviceSyncGroupQueries.clear()
        devices.removeAll()
    }

    private suspend fun send(agentId: String, payload: ByteArray) {
        attempt { client.connectAgent(agentId) }
        client.sendDirect(agentId, payload)
    }

    /**
     * Waits until [agentId] is an active member of [groupId], running [resend] now and then: gossip on a fresh
     * topic drops early messages, so repeating the invite is what makes delivery reliable.
     */
    private suspend fun awaitActive(
        groupId: String,
        agentId: String,
        timeout: Duration = joinTimeout,
        resend: suspend () -> Unit = {},
    ) {
        var waited = Duration.ZERO
        var sinceResend = resendInterval // send straight away
        while (waited < timeout) {
            if (sinceResend >= resendInterval) {
                attempt { resend() }
                sinceResend = Duration.ZERO
            }
            val active = try {
                client.getGroupMembers(groupId).any { it.agentId == agentId && it.state == "active" }
            } catch (e: X0xApiException) {
                false // the group isn't visible to us yet
            }
            if (active) return
            delay(pollInterval)
            waited += pollInterval
            sinceResend += pollInterval
        }
        throw X0xApiException(0, "timed out waiting for the device to join the group")
    }

    private fun shortName(agentId: String) = agentId.take(8)

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
