package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.ui.components.FabClearance
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.EmptyState
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.assistants_add
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.assistants_empty_subtitle
import vesta.shared.generated.resources.assistants_empty_title
import vesta.shared.generated.resources.assistants_pending
import vesta.shared.generated.resources.assistants_summary_homes
import vesta.shared.generated.resources.assistants_summary_none
import vesta.shared.generated.resources.assistants_title
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_plus

private val AssistantsTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or assistant names. */
internal object AssistantsTestTags {
    const val BackButton = "assistants_back_button"
    const val AddButton = "assistants_add_button"
    const val EmptyState = "assistants_empty_state"

    fun row(agentId: String) = "assistant_row_$agentId"
}

/** One row of the Assistants list. [title] is the user's name for it, else its shortened agent id. */
data class AssistantListItem(val agentId: String, val title: String, val pending: Boolean, val homeCount: Int)

/**
 * The external AI agents the user has added or is being asked about (ADR-027): each row shows the assistant's
 * name and whether it is waiting for review or how many Homes it may act in, and opens it through [onOpen].
 * The floating button calls [onAdd]; the back arrow calls [onBack]. UI only — the caller owns [assistants].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantsScreen(
    assistants: List<AssistantListItem>,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit = {},
    onOpen: (agentId: String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= AssistantsTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.assistants_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(AssistantsTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_left),
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            if (assistants.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding)
                        .testTag(AssistantsTestTags.EmptyState),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        EmptyStateArt.Assistants,
                        title = stringResource(Res.string.assistants_empty_title),
                        body = stringResource(Res.string.assistants_empty_subtitle),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding),
                    contentPadding = PaddingValues(top = 4.dp, bottom = FabClearance),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(assistants, key = { it.agentId }) { assistant ->
                        AssistantRow(assistant, onClick = { onOpen(assistant.agentId) })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(contentPadding)
                .testTag(AssistantsTestTags.AddButton),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.assistants_add))
        }
    }
}

@Composable
private fun AssistantRow(assistant: AssistantListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(AssistantsTestTags.row(assistant.agentId)),
    ) {
        Text(
            text = assistant.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = when {
                assistant.pending -> stringResource(Res.string.assistants_pending)
                assistant.homeCount == 0 -> stringResource(Res.string.assistants_summary_none)
                else -> stringResource(Res.string.assistants_summary_homes, assistant.homeCount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val AssistantsPreviewItems = listOf(
    AssistantListItem("a1", "Grok Bot", pending = false, homeCount = 2),
    AssistantListItem("a2", "63ab3172…d930", pending = true, homeCount = 0),
    AssistantListItem("a3", "Shopping helper", pending = false, homeCount = 0),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AssistantsScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AssistantsScreen(AssistantsPreviewItems) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AssistantsScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AssistantsScreen(AssistantsPreviewItems) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AssistantsScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { AssistantsScreen(AssistantsPreviewItems) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AssistantsScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AssistantsScreen(AssistantsPreviewItems) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AssistantsScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AssistantsScreen(emptyList()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AssistantsScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AssistantsScreen(emptyList()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AssistantsScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { AssistantsScreen(emptyList()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AssistantsScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AssistantsScreen(emptyList()) }
}
