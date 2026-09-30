package uk.veritaslabs.vesta.agent

/**
 * The v1 allowlist (ADR-009 decision 2): comma-separated x0x agent ids in the `VESTA_AGENT_ALLOWLIST`
 * environment variable, read once at construction. Unset or empty accepts nothing.
 */
class EnvAgentAllowlist(getenv: (String) -> String? = System::getenv) : AgentAllowlist {
    private val delegate = FixedAgentAllowlist.parse(getenv(VariableName))

    override fun allows(agentId: String): Boolean = delegate.allows(agentId)

    companion object {
        const val VariableName = "VESTA_AGENT_ALLOWLIST"
    }
}
