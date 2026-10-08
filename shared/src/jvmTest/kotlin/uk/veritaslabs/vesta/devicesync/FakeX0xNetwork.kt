package uk.veritaslabs.vesta.devicesync

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import uk.veritaslabs.vesta.x0x.FakeX0xClient
import uk.veritaslabs.vesta.x0x.X0xAgent
import uk.veritaslabs.vesta.x0x.X0xApiException
import uk.veritaslabs.vesta.x0x.X0xClient
import uk.veritaslabs.vesta.x0x.X0xDirectMessage
import uk.veritaslabs.vesta.x0x.X0xEventStream
import uk.veritaslabs.vesta.x0x.X0xFileEvent
import uk.veritaslabs.vesta.x0x.X0xFileTransfer
import uk.veritaslabs.vesta.x0x.X0xGroupMember
import uk.veritaslabs.vesta.x0x.X0xPresenceEvent
import uk.veritaslabs.vesta.x0x.X0xStoreKey

/**
 * A tiny in-memory x0x mesh: nodes can create groups, invite and join, send each other direct messages, and
 * open a group's stores.
 *
 * A group store is replicated on every member that has opened it, as on a real daemon. Each replica is
 * local: reads see only what has reached it. A write reaches every online replica at once; an [offline]
 * node's writes stay local until it [comeOnline]s, and a node coming online (or opening the store) catches
 * up from the replicas that are online, so the writer being offline never stops anyone reading.
 *
 * File transfers mirror x0xd (ADR-023, Spikes 1 and 4): an offer reaches only an online node, and nothing moves until the receiver accepts;
 * the daemon reads the sender's `path` in place, checks the sha256 on arrival and lands the file as `<first 8 of id>_<filename>` in the receiver's
 * [Node.transfersDir]. A mismatch leaves the receiver `Failed` with no file, the sender still `Complete`, and no event; an offer to an offline node
 * stays `Pending` for ever and never arrives.
 */
class FakeX0xNetwork {

    class Group(val creator: String) {
        val members = linkedMapOf<String, X0xGroupMember>()

        /** Store name to its replicas by agent id. Only members that have opened the store hold one. */
        val stores = mutableMapOf<String, MutableMap<String, Replica>>()
    }

    /** A value or, when [value] is null, a deletion marker; the highest [version] of a key wins. */
    class Entry(val value: ByteArray?, val version: Long, val contentType: String)

    class Replica {
        val entries = mutableMapOf<String, Entry>()
    }

    /** x0x refuses a store value larger than this. */
    val maxStoreValueBytes = 65_536

    private var clock = 0L

    val groups = mutableMapOf<String, Group>()
    private val nodes = mutableMapOf<String, Node>()
    private var nextGroup = 0
    private companion object {
        const val JoinerMarker = "|for="
    }

    /** Agents whose direct messages are silently dropped, as if offline. */
    val offline = mutableSetOf<String>()

    /**
     * Agents that never answer, as a daemon whose path to them has gone stale: a direct message to one waits [hangFor] and then fails,
     * as x0xd's does after about 24 s.
     */
    val hanging = mutableSetOf<String>()
    var hangFor: Duration = 3.seconds

    /** An invite made through a node: for which group, with what lifetime and joiner, and by whom. */
    data class MintedInvite(val groupId: String, val expirySecs: Long?, val intendedJoiner: String?, val mintedBy: String)

    /** Every invite made so far, in order. */
    val minted = mutableListOf<MintedInvite>()

    /** Agents that don't yet see themselves in a group's roster, as while their admission is being committed. */
    val lagging = mutableSetOf<String>()

    /** Takes [agentId] online: its direct messages arrive again, and every other node sees it come online. */
    fun comeOnline(agentId: String) {
        offline -= agentId
        groups.values.forEach { group ->
            group.stores.values.forEach { replicas ->
                replicas[agentId]?.let { mine ->
                    replicas.filter { (other, _) -> other != agentId && other !in offline }.values.forEach { merge(mine, it); merge(it, mine) }
                }
            }
        }
        nodes.values.filter { it.agentId != agentId }.forEach { it.onPresence(X0xPresenceEvent("online", agentId)) }
    }

    private fun merge(into: Replica, from: Replica) {
        from.entries.forEach { (key, entry) ->
            if ((into.entries[key]?.version ?: -1) < entry.version) into.entries[key] = entry
        }
    }

    /** One file transfer, with the state each end sees. */
    class Transfer(
        val id: String,
        val from: String,
        val to: String,
        val filename: String,
        val size: Long,
        val sha256: String,
        val sourcePath: String,
    ) {
        var senderState = "Pending"
        var receiverState = "Pending"
        var error: String? = null
        var outputPath: String? = null
    }

    /** Every transfer made so far, by id. */
    val transfers = linkedMapOf<String, Transfer>()
    private var nextTransfer = 0

    private fun storeId(groupId: String, name: String) = "x0x/group/$groupId/kv/$name"

    /**
     * A node on this mesh. With [directStream] its [X0xClient.directEvents] delivers what the node is sent, as a real daemon's stream does; without it
     * (the default, what most tests wire by hand through [Node.onDirect]) that stream stays silent.
     */
    fun node(agentId: String, directStream: Boolean = false): Node = Node(agentId, directStream).also { nodes[agentId] = it }

    inner class Node(val agentId: String, private val directStream: Boolean = false) : X0xClient by FakeX0xClient() {

        /** Called for every direct message delivered to this node. */
        var onDirect: (X0xDirectMessage) -> Unit = {}

        val sentTo = mutableListOf<String>()

        /** Where this node's "daemon" lands received files. */
        val transfersDir: File = Files.createTempDirectory("fake-transfers-$agentId").toFile().also { it.deleteOnExit() }

        /** Called for every file event while this node's event stream is open. */
        var onFile: (X0xFileEvent) -> Unit = {}

        override suspend fun fileEvents(onEvent: (X0xFileEvent) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream {
            onFile = onEvent
            return X0xEventStream { onFile = {}; onClose(null) }
        }

        /** Opens a direct-message stream beside any already open (a real daemon serves several), delivering what [onDirect] gets. */
        override suspend fun directEvents(onMessage: (X0xDirectMessage) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream {
            if (!directStream) return X0xEventStream { onClose(null) }
            val previous = onDirect
            onDirect = { previous(it); onMessage(it) }
            return X0xEventStream { onDirect = previous; onClose(null) }
        }

        override suspend fun sendFile(agentId: String, filename: String, size: Long, sha256: String, path: String): String {
            val id = "%08d-fake-transfer".format(nextTransfer++)
            val transfer = Transfer(id, this.agentId, agentId, filename, size, sha256, path)
            transfers[id] = transfer
            val target = nodes[agentId]
            if (target != null && agentId !in offline) target.onFile(X0xFileEvent.Offer(id, filename, size, this.agentId))
            return id
        }

        private fun view(transfer: Transfer): X0xFileTransfer {
            val receiving = transfer.to == agentId
            return X0xFileTransfer(
                transferId = transfer.id,
                filename = transfer.filename,
                size = transfer.size,
                sha256 = transfer.sha256,
                state = if (receiving) transfer.receiverState else transfer.senderState,
                error = transfer.error.takeIf { receiving },
                outputPath = transfer.outputPath.takeIf { receiving },
                sourcePath = transfer.sourcePath.takeIf { !receiving },
            )
        }

        override suspend fun listFileTransfers(): List<X0xFileTransfer> =
            transfers.values.filter { it.from == agentId || it.to == agentId }.map(::view)

        override suspend fun getFileTransfer(transferId: String): X0xFileTransfer? =
            transfers[transferId]?.takeIf { it.from == agentId || it.to == agentId }?.let(::view)

        override suspend fun acceptFile(transferId: String) {
            val transfer = transfers[transferId]?.takeIf { it.to == agentId } ?: throw X0xApiException(404, "transfer not found")
            if (transfer.receiverState != "Pending") throw X0xApiException(409, "transfer is ${transfer.receiverState}")
            val source = File(transfer.sourcePath)
            val bytes = source.takeIf { it.isFile }?.readBytes()
            transfer.senderState = "Complete"
            if (bytes == null) {
                transfer.receiverState = "Failed"
                transfer.error = "source unreadable: ${transfer.sourcePath}"
                return
            }
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if (actual != transfer.sha256) {
                transfer.receiverState = "Failed"
                transfer.error = "SHA-256 mismatch: expected ${transfer.sha256} got $actual"
                return
            }
            val landed = File(transfersDir, "${transfer.id.take(8)}_${transfer.filename}").also { it.writeBytes(bytes) }
            transfer.receiverState = "Complete"
            transfer.outputPath = landed.path
            onFile(X0xFileEvent.Complete(transfer.id, transfer.filename, actual, landed.path))
        }

        override suspend fun rejectFile(transferId: String) {
            val transfer = transfers[transferId]?.takeIf { it.to == agentId } ?: throw X0xApiException(404, "transfer not found")
            if (transfer.receiverState != "Pending") throw X0xApiException(409, "transfer is ${transfer.receiverState}")
            transfer.receiverState = "Rejected"
            transfer.senderState = "Rejected"
            transfer.error = "rejected by user"
        }

        /** Called for every presence event while this node's presence stream is open. */
        var onPresence: (X0xPresenceEvent) -> Unit = {}

        override suspend fun presenceEvents(onEvent: (X0xPresenceEvent) -> Unit, onClose: (Throwable?) -> Unit): X0xEventStream {
            onPresence = onEvent
            return X0xEventStream { onPresence = {}; onClose(null) }
        }

        override suspend fun agent() = X0xAgent(agentId = agentId)

        override suspend fun connectAgent(agentId: String) = JsonObject(emptyMap())

        override suspend fun sendDirect(agentId: String, payload: ByteArray): JsonObject {
            sentTo += agentId
            if (agentId in hanging) {
                delay(hangFor)
                error("timed out sending to $agentId")
            }
            val target = nodes[agentId]
            if (target != null && agentId !in offline) {
                target.onDirect(
                    X0xDirectMessage(sender = this.agentId, payload = Base64.Default.encode(payload), verified = true, trustDecision = "unknown"),
                )
            }
            return JsonObject(emptyMap())
        }

        override suspend fun createGroup(name: String, description: String, preset: String?): String {
            val id = "group-${nextGroup++}"
            groups[id] = Group(agentId).apply { members[agentId] = X0xGroupMember(agentId, role = "admin", state = "active") }
            return id
        }

        override suspend fun createGroupInvite(groupId: String, expirySecs: Long?, intendedJoiner: String?): String {
            requireAdmin(groupId)
            minted += MintedInvite(groupId, expirySecs, intendedJoiner, mintedBy = agentId)
            return "invite:$groupId" + (intendedJoiner?.let { "$JoinerMarker$it" }.orEmpty())
        }

        override suspend fun joinGroup(invite: String): String {
            val id = invite.removePrefix("invite:").substringBefore(JoinerMarker)
            // As x0x does: an invite made for one agent is refused to every other
            invite.substringAfter(JoinerMarker, missingDelimiterValue = "").takeIf { it.isNotEmpty() && it != agentId }
                ?.let { throw X0xApiException(409, "invite_not_addressed_to_me") }
            val group = groups[id] ?: throw X0xApiException(404, "group not found")
            group.members[agentId] = X0xGroupMember(agentId, role = "member", state = "active")
            return id
        }

        override suspend fun openGroupStore(groupId: String, name: String): String {
            val group = groups[groupId]?.takeIf { it.members[agentId]?.state == "active" } ?: throw X0xApiException(404, "group not found")
            val replicas = group.stores.getOrPut(name) { mutableMapOf() }
            if (agentId !in replicas) {
                val mine = Replica()
                replicas[agentId] = mine
                if (agentId !in offline) replicas.filter { (other, _) -> other !in offline }.values.forEach { if (it !== mine) merge(mine, it) }
            }
            return storeId(groupId, name)
        }

        /** The replica this node holds of [storeId], or a 404 as for a store it never opened or may no longer read. */
        private fun replicaOf(storeId: String): Pair<MutableMap<String, Replica>, Replica> {
            val parts = storeId.removePrefix("x0x/group/").split("/kv/")
            val group = groups[parts[0]]?.takeIf { it.members[agentId]?.state == "active" }
            val replicas = group?.stores?.get(parts.getOrNull(1))
            val mine = replicas?.get(agentId) ?: throw X0xApiException(404, "store not found")
            return replicas to mine
        }

        private fun write(storeId: String, key: String, value: ByteArray?, contentType: String) {
            val (replicas, mine) = replicaOf(storeId)
            val entry = Entry(value, ++clock, contentType)
            mine.entries[key] = entry
            if (agentId !in offline) replicas.filter { (other, _) -> other !in offline }.values.forEach { it.entries[key] = entry }
        }

        override suspend fun listStoreKeys(storeId: String): List<X0xStoreKey> =
            replicaOf(storeId).second.entries.filterValues { it.value != null }
                .map { (key, entry) -> X0xStoreKey(key, entry.value!!.size.toLong(), entry.contentType) }

        override suspend fun getValue(storeId: String, key: String): ByteArray? = replicaOf(storeId).second.entries[key]?.value

        override suspend fun putValue(storeId: String, key: String, value: ByteArray, contentType: String) {
            if (value.size > maxStoreValueBytes) {
                throw X0xApiException(413, "value too large: ${value.size} exceeds maximum $maxStoreValueBytes bytes")
            }
            write(storeId, key, value, contentType)
        }

        override suspend fun deleteValue(storeId: String, key: String) = write(storeId, key, null, "")

        override suspend fun getGroupMembers(groupId: String): List<X0xGroupMember> {
            val group = groups[groupId]?.takeIf { agentId in it.members } ?: throw X0xApiException(404, "group not found")
            return group.members.values.filter { !(it.agentId == agentId && agentId in lagging) }
        }

        override suspend fun getGroupCreator(groupId: String): String? = groups[groupId]?.creator

        override suspend fun setGroupMemberRole(groupId: String, agentId: String, role: String) {
            requireAdmin(groupId)
            val group = groups.getValue(groupId)
            group.members[agentId] = group.members.getValue(agentId).copy(role = role)
        }

        override suspend fun removeGroupMember(groupId: String, agentId: String) {
            requireAdmin(groupId)
            val group = groups.getValue(groupId)
            // As the real daemon: only the creator itself can take the creator out.
            if (agentId == group.creator && this.agentId != group.creator) {
                throw X0xApiException(409, "member_key_package_pending")
            }
            group.members.remove(agentId)
        }

        override suspend fun leaveGroup(groupId: String) {
            groups[groupId]?.members?.remove(agentId)
        }

        private fun requireAdmin(groupId: String) {
            val me = groups[groupId]?.members?.get(agentId)
            if (me?.role != "admin") throw X0xApiException(403, "admin role required")
        }
    }
}
