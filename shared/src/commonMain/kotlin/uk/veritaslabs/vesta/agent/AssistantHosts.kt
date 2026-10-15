package uk.veritaslabs.vesta.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.veritaslabs.vesta.ui.components.abbreviateAgentId
import uk.veritaslabs.vesta.ui.screens.ActivityRow
import uk.veritaslabs.vesta.ui.screens.AssistantEditScreen
import uk.veritaslabs.vesta.ui.screens.AssistantListItem
import uk.veritaslabs.vesta.ui.screens.AssistantsScreen
import uk.veritaslabs.vesta.ui.screens.HomeAccess
import uk.veritaslabs.vesta.home.HomeListItem

/** The Assistants list (ADR-027), fed from [assistants]. */
@Composable
fun AssistantsListHost(
    assistants: AssistantRepository,
    onAdd: () -> Unit,
    onOpen: (agentId: String) -> Unit,
    onBack: () -> Unit,
) {
    val list by assistants.assistants.collectAsStateWithLifecycle(emptyList())
    val items by produceState(emptyList<AssistantListItem>(), list) {
        value = list.map { assistant ->
            val homeCount = assistants.grants(assistant.agentId).first().filter { it.capability != AssistantCapability.ListHomes }
                .map { it.homeId }.distinct().size
            AssistantListItem(assistant.agentId, assistant.displayName(), assistant.status == AssistantStatus.Pending, homeCount)
        }
    }
    AssistantsScreen(items, onAdd = onAdd, onOpen = onOpen, onBack = onBack)
}

/**
 * Adds ([agentId] null) or edits an assistant: what it may do in each of [homes], its activity, and removal. Saving
 * or removing calls [onDone], as does going back.
 */
@Composable
fun AssistantEditHost(
    assistants: AssistantRepository,
    agentId: String?,
    homes: List<HomeListItem>,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val loaded by produceState<Triple<String, List<AssistantGrant>, Boolean>?>(null, agentId) {
        value = if (agentId == null) {
            Triple("", emptyList(), true)
        } else {
            val name = assistants.assistant(agentId)?.name.orEmpty()
            Triple(name, assistants.grants(agentId).first(), true)
        }
    }
    val activity by (agentId?.let { assistants.activity(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsStateWithLifecycle(emptyList())
    val (name, grants, _) = loaded ?: return
    val access = remember(homes, grants) {
        homes.map { home ->
            val pantry = grants.firstOrNull { it.homeId == home.id && it.capability == AssistantCapability.PantryAdd }
            HomeAccess(home.id, home.name, pantryAdd = pantry != null, ask = pantry?.policy != GrantPolicy.Auto)
        }
    }
    AssistantEditScreen(
        agentId = agentId,
        initialName = name,
        homes = access,
        activity = activity.map { entry ->
            ActivityRow(entry.summary, homes.firstOrNull { it.id == entry.homeId }?.name, entry.outcome == ActivityOutcome.Applied)
        },
        onSave = { id, newName, edited ->
            scope.launch {
                assistants.save(
                    id, newName,
                    edited.filter { it.pantryAdd }.map {
                        AssistantGrant(it.homeId, AssistantCapability.PantryAdd, if (it.ask) GrantPolicy.Ask else GrantPolicy.Auto)
                    },
                )
                onDone()
            }
        },
        onRemove = agentId?.let { id -> { scope.launch { assistants.remove(id); onDone() } } },
        onBack = onDone,
    )
}
