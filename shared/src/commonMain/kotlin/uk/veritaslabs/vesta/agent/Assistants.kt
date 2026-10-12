package uk.veritaslabs.vesta.agent

/**
 * Something an assistant can be allowed to do in one Home (ADR-027). One per command family; a new command
 * family adds a capability, so no existing grant ever covers it.
 */
enum class AssistantCapability {
    /** See the Home in a [ListHomes] answer. Implied by every other capability. */
    ListHomes,

    /** Add to the Home's Pantry ([AddPantryItem]). */
    PantryAdd,
}

/** Whether a granted capability runs at once ([Auto]) or waits for the user's tap on every command ([Ask]). */
enum class GrantPolicy { Ask, Auto }

enum class AssistantStatus {
    /** A trusted agent sent a command and is waiting for the user to review it. */
    Pending,
    Active,
}

/** An external agent the user has added, or is being asked about. [name] is blank until the user names it. */
data class Assistant(val agentId: String, val name: String, val status: AssistantStatus, val addedAt: Long)

/** One capability on one Home for one assistant. */
data class AssistantGrant(val homeId: String, val capability: AssistantCapability, val policy: GrantPolicy)

enum class ActivityOutcome { Applied, Rejected }

/** One line of an assistant's activity list. [homeId] is null for commands not about one Home. */
data class AssistantActivityEntry(
    val id: Long,
    val homeId: String?,
    val capability: AssistantCapability,
    val commandId: String,
    val outcome: ActivityOutcome,
    val summary: String,
    val at: Long,
)

/** A command held until the user taps Allow once or Deny, because its grant's policy is [GrantPolicy.Ask]. */
data class PendingCommand(
    val agentId: String,
    val homeId: String,
    val capability: AssistantCapability,
    val commandId: String,
    val summary: String,
    val at: Long,
)

/** What the handler does with a command from a known assistant. */
enum class AccessDecision {
    /** Apply it. */
    Allow,

    /** Hold it and ask the user. */
    Ask,

    /** The assistant has no grant for this capability on this Home. */
    NotPermitted,
}

/** What command handling needs to know about who may command the app, and for what (ADR-027). */
interface AssistantAccess {
    /** Whether [agentId] is an active assistant (it may still lack a grant for what it asks). */
    suspend fun isActive(agentId: String): Boolean

    /** Notes that [agentId] wants to command the app and is waiting for the user to review it. Idempotent. */
    suspend fun requestApproval(agentId: String)

    /** The Homes [agentId] may see in a [ListHomes] answer, or null for every Home. */
    suspend fun visibleHomes(agentId: String): Set<String>?

    /**
     * Decides [commandId], a [capability] command on [homeId]. An [AccessDecision.Allow] that came from the
     * user's Allow once is used up by this call.
     */
    suspend fun decide(agentId: String, homeId: String, capability: AssistantCapability, commandId: String): AccessDecision

    /** Parks [commandId] for the user's decision; see [AssistantRepository.pendingCommands]. Idempotent. */
    suspend fun hold(agentId: String, homeId: String, capability: AssistantCapability, commandId: String, summary: String)

    suspend fun recordActivity(
        agentId: String,
        homeId: String?,
        capability: AssistantCapability,
        commandId: String,
        outcome: ActivityOutcome,
        summary: String,
    )
}
