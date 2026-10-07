package uk.veritaslabs.vesta.x0x

import kotlinx.serialization.json.JsonObject

/**
 * Client for the local x0x REST API (loopback, bearer-token protected). One
 * implementation per platform:
 *
 * * Android / iOS — talks to the daemon embedded in this process
 *   ([EmbeddedX0x]);
 * * Desktop (JVM) — talks to the standalone `x0xd` daemon (port + token read
 *   from the standard x0x data directory).
 *
 * Covers health, agent identity, session minting, gossip pub/sub, direct
 * messaging, replicated KV stores and named groups — the full surface an app
 * built on x0x needs, ported from the Colo project's JVM-only client
 * (`town.colo.x0x.X0xClient`) onto this shared, multiplatform, suspending
 * interface. See [AbstractX0xClient] for the shared implementation of all of
 * this beyond [health] and [endpoint].
 */
interface X0xClient {

    /** What this client talks to, for display, e.g. `x0xd · 127.0.0.1:12700`. */
    val endpoint: String

    /** `GET /health`; null when the daemon is unreachable or the body is unrecognisable. */
    suspend fun health(): X0xHealth?

    // --- Core ---

    suspend fun agent(): X0xAgent

    /** Mints a short-lived session token for WebSocket / query-string auth. */
    suspend fun createSession(): X0xSession

    // --- Gossip pub/sub ---

    suspend fun subscribe(topic: String): JsonObject

    suspend fun publish(topic: String, payload: ByteArray): JsonObject

    /**
     * Opens the gossip SSE stream (`GET /events`). Call [X0xEventStream.close]
     * (or use `use { }`) when finished. Each gossip message is wrapped in a
     * `{ "type": "message", "data": { ... } }` envelope by the daemon. [onClose] fires exactly once
     * when the stream ends (server close, IO error — passed as the argument — or [X0xEventStream.close]).
     */
    suspend fun gossipEvents(onMessage: (X0xGossipMessage) -> Unit, onClose: (Throwable?) -> Unit = {}): X0xEventStream

    // --- Direct messaging ---

    suspend fun connectAgent(agentId: String): JsonObject

    suspend fun sendDirect(agentId: String, payload: ByteArray): JsonObject

    suspend fun listDirectConnections(): JsonObject

    // --- Network peers ---

    /** `GET /peers`: the ids of the peers this daemon is connected to in the gossip mesh. */
    suspend fun listPeers(): List<String>

    /** `GET /peers/:id/health`: how that connection is doing now; null when the daemon has no such connection (404). */
    suspend fun getPeerHealth(peerId: String): X0xPeerHealth?

    /** `GET /diagnostics/connectivity`: per-connection byte counters and how this node sees itself on the network. */
    suspend fun getTransportSnapshot(): X0xTransportSnapshot

    /** `POST /peers/:id/probe`: an active liveness probe, answered with the measured round-trip time in ms; null when the peer doesn't answer in [timeoutMs]. */
    suspend fun probePeer(peerId: String, timeoutMs: Long = 2_000): Long?

    /**
     * Opens the direct-message SSE stream (`GET /direct/events`). Events are
     * flat (no `data` envelope), unlike [gossipEvents]. [onClose] behaves as for [gossipEvents].
     */
    suspend fun directEvents(onMessage: (X0xDirectMessage) -> Unit, onClose: (Throwable?) -> Unit = {}): X0xEventStream

    /**
     * Opens the presence SSE stream (`GET /presence/events`): an agent coming online or going offline, as this
     * daemon sees it. Events are flat. [onClose] behaves as for [gossipEvents].
     */
    suspend fun presenceEvents(onEvent: (X0xPresenceEvent) -> Unit, onClose: (Throwable?) -> Unit = {}): X0xEventStream

    // --- Store (replicated KV CRDT) ---

    suspend fun createStore(name: String, topic: String, policy: String = "signed"): String

    suspend fun listStores(): JsonObject

    suspend fun listStoreKeys(storeId: String): List<X0xStoreKey>

    /** Returns null if the key does not exist (404). */
    suspend fun getValue(storeId: String, key: String): ByteArray?

    suspend fun putValue(storeId: String, key: String, value: ByteArray, contentType: String = "application/octet-stream")

    suspend fun deleteValue(storeId: String, key: String)

    /** Replicates a store another agent created; [expectedOwner] must come from an out-of-band source. */
    suspend fun joinStore(storeId: String, expectedOwner: String)

    /**
     * Opens (creates, or on a repeat call re-opens) the store [name] bound to a group this agent belongs to,
     * and returns its store id. Any active member can read, write and delete in it; the group's key
     * encrypts it. A daemon never opens a group store by itself on joining, so every member calls this
     * once per group. The id contains `/`, which the other store calls encode.
     */
    suspend fun openGroupStore(groupId: String, name: String): String

    // --- Named groups ---

    /** Creates a named group and returns its `group_id`. See `preset` options in the x0x API docs. */
    suspend fun createGroup(name: String, description: String = "", preset: String? = null): String

    /** Named groups this agent created or belongs to. */
    suspend fun listGroups(): List<X0xGroupSummary>

    /**
     * Creates an invite link (`x0x://invite/…`) for a group this agent administers. It lives [expirySecs] seconds (x0x's default is seven days), and only
     * [intendedJoiner], an agent id, can redeem it when that is given (anyone else is refused with `invite_not_addressed_to_me`). A removed device
     * could otherwise get back into a group with any unused invite (docs/adr/ADR-020, Spike 9), so [InviteLifetime] says how long Vesta's last.
     */
    suspend fun createGroupInvite(groupId: String, expirySecs: Long? = null, intendedJoiner: String? = null): String

    /**
     * Redeems an invite link and returns the group id. Admission is committed by the group's authority, so
     * the caller is a member only once [getGroupMembers] lists it as `active`.
     */
    suspend fun joinGroup(invite: String): String

    /** The group's roster, from this agent's view. Throws [X0xApiException] (404) once this agent is no longer in it. */
    suspend fun getGroupMembers(groupId: String): List<X0xGroupMember>

    /** The agent that created the group (its signed genesis); only it cannot be removed by another admin. */
    suspend fun getGroupCreator(groupId: String): String?

    /** Sets a member's role (`"admin"` or `"member"`); needs the caller to be an admin. */
    suspend fun setGroupMemberRole(groupId: String, agentId: String, role: String)

    /**
     * Removes a member and rotates the group's keys, so the removed agent can't read anything sent afterwards.
     * Throws [X0xApiException] while the daemon still lacks the member's key package (`retry` — try again shortly).
     */
    suspend fun removeGroupMember(groupId: String, agentId: String)

    /** Leaves (or, for the last member, deletes) the group. */
    suspend fun leaveGroup(groupId: String)

    /** Publishes a signed message to a `SignedPublic` group's public channel; `kind` is `"chat"` or `"announcement"`. */
    suspend fun sendGroupMessage(groupId: String, body: String, kind: String = "chat"): JsonObject

    /**
     * Reads a `SignedPublic` group's cached public messages. This is the reliable way to see a
     * group's announcements: delivery to members rides direct messages under the hood, not the
     * `x0x.groups.public.{groupId}` gossip topic, so a plain [subscribe] to that topic can silently
     * miss messages — poll this instead.
     */
    suspend fun getGroupMessages(groupId: String): List<X0xGroupPublicMessage>
}

expect fun createX0xClient(): X0xClient

/** Closeable handle for an open SSE subscription. Safe to close more than once. */
fun interface X0xEventStream : AutoCloseable {
    override fun close()
}

/** Result of a raw HTTP call to the daemon: the status code and the (possibly empty) response body. */
internal data class X0xHttpResult(val status: Int, val body: String)

/**
 * Performs a blocking-free HTTP call and returns its status and body. Never throws for a non-2xx
 * status — callers (see [AbstractX0xClient]) interpret the status themselves. Throws only when the
 * daemon can't be reached at all (connection failure, timeout).
 */
internal expect suspend fun httpRequest(method: String, url: String, bearerToken: String?, jsonBody: String?): X0xHttpResult

/**
 * Opens `url` as an SSE stream and invokes [onEvent] with each event's `data:` payload (lines
 * joined by `\n` when an event spans several `data:` lines) until the returned [X0xEventStream] is
 * closed or the connection drops. [onClose] is invoked exactly once when the stream ends: with the
 * error if the connection failed, or null if the server ended it or [X0xEventStream.close] was called.
 */
internal expect suspend fun openSseStream(
    url: String,
    bearerToken: String?,
    onEvent: (String) -> Unit,
    onClose: (Throwable?) -> Unit,
): X0xEventStream
