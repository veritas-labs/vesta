package uk.veritaslabs.vesta.agent

/** The x0x agent ids allowed to command this app (ADR-009 behaviour rule 1). */
fun interface AgentAllowlist {
    fun allows(agentId: String): Boolean
}

/** Allows exactly [agentIds]; the default (empty) allows nothing. */
class FixedAgentAllowlist(agentIds: Collection<String> = emptyList()) : AgentAllowlist {
    private val ids = agentIds.toSet()

    override fun allows(agentId: String): Boolean = agentId in ids

    companion object {
        /** Parses a comma-separated list, ignoring blanks and surrounding whitespace. */
        fun parse(commaSeparated: String?): FixedAgentAllowlist =
            FixedAgentAllowlist(commaSeparated.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }
}
