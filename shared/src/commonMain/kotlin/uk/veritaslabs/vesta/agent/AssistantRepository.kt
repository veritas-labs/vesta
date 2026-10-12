package uk.veritaslabs.vesta.agent

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import uk.veritaslabs.vesta.db.VestaDatabase

/** The newest this many activity rows are kept per assistant. */
const val AssistantActivityLimit = 500L

/**
 * [AssistantAccess] backed by the local `Assistant`, `AssistantGrant` and `AssistantActivity` tables (ADR-027),
 * plus [preApproved] agents that are always active with every capability on every Home, policy auto (the
 * `VESTA_AGENT_ALLOWLIST` environment variable, for headless setups) without any row.
 *
 * The x0x daemon's trust decision is a separate, earlier gate: only agents the daemon already verified and
 * trusts ever get here. This is the app-specific permission to actually change Vesta's data.
 */
class AssistantRepository(
    private val database: VestaDatabase,
    private val preApproved: AgentAllowlist = FixedAgentAllowlist(),
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AssistantAccess {

    private val queries get() = database.assistantQueries

    private val held = MutableStateFlow<List<PendingCommand>>(emptyList())
    private val allowedOnce = MutableStateFlow<Set<Pair<String, String>>>(emptySet())

    /** Every assistant, active and pending, oldest first, re-emitted whenever the table changes. */
    val assistants: Flow<List<Assistant>> = queries.selectAll().asFlow().map { query ->
        query.awaitAsList().map { Assistant(it.agentId, it.name, it.status.toStatus(), it.addedAt) }
    }

    /** Assistants awaiting review, oldest first. */
    val pending: Flow<List<Assistant>> = assistants.map { all -> all.filter { it.status == AssistantStatus.Pending } }

    /** Commands waiting for Allow once / Deny, oldest first. Not persisted: the agent re-sends after a restart. */
    val pendingCommands: StateFlow<List<PendingCommand>> get() = held

    /** [agentId]'s grants, re-emitted whenever they change. */
    fun grants(agentId: String): Flow<List<AssistantGrant>> = queries.selectGrantsFor(agentId).asFlow().map { query ->
        query.awaitAsList().mapNotNull { row ->
            row.capability.toCapability()?.let { AssistantGrant(row.homeId, it, row.policy.toPolicy()) }
        }
    }

    /** [agentId]'s activity, newest first. */
    fun activity(agentId: String): Flow<List<AssistantActivityEntry>> = queries.selectActivity(agentId).asFlow().map { query ->
        query.awaitAsList().mapNotNull { row ->
            row.capability.toCapability()?.let {
                AssistantActivityEntry(
                    row.id, row.homeId, it, row.commandId,
                    if (row.outcome == APPLIED) ActivityOutcome.Applied else ActivityOutcome.Rejected,
                    row.summary, row.at,
                )
            }
        }
    }

    /** When [agentId] last sent a command we recorded, or null. */
    suspend fun lastActiveAt(agentId: String): Long? = queries.selectLastActivityAt(agentId).awaitAsOneOrNull()?.MAX

    override suspend fun isActive(agentId: String): Boolean =
        preApproved.allows(agentId) || queries.selectOne(agentId).awaitAsOneOrNull()?.status == ACTIVE

    override suspend fun requestApproval(agentId: String) {
        queries.insertPending(agentId, nowMillis())
    }

    override suspend fun visibleHomes(agentId: String): Set<String>? =
        if (preApproved.allows(agentId)) null
        else queries.selectGrantedHomeIds(agentId, AssistantCapability.ListHomes.name).awaitAsList().toSet()

    override suspend fun decide(
        agentId: String,
        homeId: String,
        capability: AssistantCapability,
        commandId: String,
    ): AccessDecision {
        if (preApproved.allows(agentId)) return AccessDecision.Allow
        val policy = queries.selectGrant(agentId, homeId, capability.name).awaitAsOneOrNull()?.toPolicy()
            ?: return AccessDecision.NotPermitted
        if (policy == GrantPolicy.Auto) return AccessDecision.Allow
        val key = agentId to commandId
        var used = false
        allowedOnce.update { if (key in it) it.also { used = true } - key else it }
        return if (used) AccessDecision.Allow else AccessDecision.Ask
    }

    override suspend fun hold(agentId: String, homeId: String, capability: AssistantCapability, commandId: String, summary: String) {
        val command = PendingCommand(agentId, homeId, capability, commandId, summary, nowMillis())
        held.update { list -> if (list.any { it.agentId == agentId && it.commandId == commandId }) list else list + command }
    }

    override suspend fun recordActivity(
        agentId: String,
        homeId: String?,
        capability: AssistantCapability,
        commandId: String,
        outcome: ActivityOutcome,
        summary: String,
    ) {
        if (queries.selectOne(agentId).awaitAsOneOrNull() == null) return // pre-approved agents have no row
        queries.insertActivity(
            agentId, homeId, capability.name, commandId,
            if (outcome == ActivityOutcome.Applied) APPLIED else REJECTED, summary, nowMillis(),
        )
        queries.trimActivity(agentId, agentId, AssistantActivityLimit)
    }

    /** The user's Allow once: the next copy of [command] from its agent is applied. */
    fun allowOnce(command: PendingCommand) {
        allowedOnce.update { it + (command.agentId to command.commandId) }
        held.update { list -> list - command }
    }

    /** The user's Deny for a held [command]; the agent is asked about again if it re-sends. */
    fun denyCommand(command: PendingCommand) {
        held.update { list -> list - command }
    }

    /** One assistant, or null. */
    suspend fun assistant(agentId: String): Assistant? =
        queries.selectOne(agentId).awaitAsOneOrNull()?.let { Assistant(it.agentId, it.name, it.status.toStatus(), it.addedAt) }

    /**
     * Makes [agentId] an active assistant called [name] holding exactly [grants]. Every Home with a grant also
     * gets [AssistantCapability.ListHomes] (policy auto), so an assistant never holds a Home it cannot see.
     * Creates the assistant, or reviews a pending one.
     */
    suspend fun save(agentId: String, name: String, grants: Collection<AssistantGrant>) {
        val now = nowMillis()
        queries.insertActive(agentId, name.trim(), now)
        queries.activate(name.trim(), agentId)
        queries.deleteGrantsFor(agentId)
        val withVisibility = grants.filter { it.capability != AssistantCapability.ListHomes } +
            grants.map { it.homeId }.distinct().map { AssistantGrant(it, AssistantCapability.ListHomes, GrantPolicy.Auto) }
        withVisibility.distinct().forEach {
            queries.upsertGrant(agentId, it.homeId, it.capability.name, it.policy.name, now)
        }
    }

    suspend fun rename(agentId: String, name: String) {
        queries.rename(name.trim(), agentId)
    }

    /** Removes [agentId] and, with it, its grants and activity. Held commands from it are dropped too. */
    suspend fun remove(agentId: String) {
        queries.deleteAssistant(agentId)
        held.update { list -> list.filterNot { it.agentId == agentId } }
        allowedOnce.update { set -> set.filterNot { it.first == agentId }.toSet() }
    }

    /** Dismisses [agentId]'s pending review; it is asked about again if it sends another command. */
    suspend fun deny(agentId: String) {
        queries.deletePending(agentId)
    }

    private companion object {
        const val ACTIVE = "active"
        const val APPLIED = "applied"
        const val REJECTED = "rejected"

        fun String.toStatus() = if (this == ACTIVE) AssistantStatus.Active else AssistantStatus.Pending
        fun String.toPolicy() = if (equals(GrantPolicy.Auto.name, ignoreCase = true)) GrantPolicy.Auto else GrantPolicy.Ask
        fun String.toCapability() = AssistantCapability.entries.firstOrNull { it.name == this }
    }
}
