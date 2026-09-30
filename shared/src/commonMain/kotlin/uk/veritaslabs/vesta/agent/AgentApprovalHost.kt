package uk.veritaslabs.vesta.agent

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.ui.components.AgentApprovalCard

/**
 * Shows an [AgentApprovalCard] for the oldest agent waiting in [agentAccess] (one at a time), and records
 * the user's decision. Draws nothing when nobody is waiting. Place it over the app's content, aligned to
 * where the card should sit.
 */
@Composable
fun AgentApprovalHost(agentAccess: AgentAccessRepository, modifier: Modifier = Modifier) {
    val pending by agentAccess.pending.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    pending.firstOrNull()?.let { agent ->
        AgentApprovalCard(
            agentId = agent.agentId,
            modifier = modifier.safeDrawingPadding().padding(16.dp),
            onAllow = { scope.launch { agentAccess.allow(agent.agentId) } },
            onDeny = { scope.launch { agentAccess.deny(agent.agentId) } },
        )
    }
}
