package uk.veritaslabs.vesta.agent

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.screens.HomeListItem
import uk.veritaslabs.vesta.ui.components.AgentApprovalCard
import uk.veritaslabs.vesta.ui.components.abbreviateAgentId
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.assistant_command_allow_once
import vesta.shared.generated.resources.assistant_command_body
import vesta.shared.generated.resources.assistant_command_title
import vesta.shared.generated.resources.assistant_request_body
import vesta.shared.generated.resources.assistant_request_review
import vesta.shared.generated.resources.assistant_request_title

/** What to call [assistant] on screen: the user's name for it, else its shortened agent id. */
fun Assistant.displayName(): String = name.ifBlank { abbreviateAgentId(agentId) }

/**
 * Shows one [AgentApprovalCard] at a time over the current screen, and records the user's decision (ADR-027):
 * first an agent waiting for review (**Review…** calls [onReview], **Deny** dismisses it), else a command held
 * until **Allow once** or **Deny**. Draws nothing when nobody is waiting. [homes] names the Home a held command
 * is about. Place it over the app's content, aligned to where the card should sit.
 */
@Composable
fun AgentApprovalHost(
    assistants: AssistantRepository,
    onReview: (agentId: String) -> Unit,
    modifier: Modifier = Modifier,
    homes: Flow<List<HomeListItem>> = flowOf(emptyList()),
) {
    val pending by assistants.pending.collectAsStateWithLifecycle(emptyList())
    val held by assistants.pendingCommands.collectAsStateWithLifecycle()
    val everyone by assistants.assistants.collectAsStateWithLifecycle(emptyList())
    val homeList by homes.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val cardModifier = modifier.safeDrawingPadding().padding(16.dp)
    val agent = pending.firstOrNull()
    val command = held.firstOrNull()
    if (agent != null) {
        AgentApprovalCard(
            title = stringResource(Res.string.assistant_request_title, agent.displayName()),
            body = stringResource(Res.string.assistant_request_body),
            confirmLabel = stringResource(Res.string.assistant_request_review),
            modifier = cardModifier,
            onAllow = { onReview(agent.agentId) },
            onDeny = { scope.launch { assistants.deny(agent.agentId) } },
        )
    } else if (command != null) {
        val who = everyone.firstOrNull { it.agentId == command.agentId }?.displayName() ?: abbreviateAgentId(command.agentId)
        AgentApprovalCard(
            title = stringResource(Res.string.assistant_command_title, who),
            body = stringResource(Res.string.assistant_command_body, command.summary, homeList.firstOrNull { it.id == command.homeId }?.name ?: command.homeId),
            confirmLabel = stringResource(Res.string.assistant_command_allow_once),
            modifier = cardModifier,
            onAllow = { assistants.allowOnce(command) },
            onDeny = { assistants.denyCommand(command) },
        )
    }
}
