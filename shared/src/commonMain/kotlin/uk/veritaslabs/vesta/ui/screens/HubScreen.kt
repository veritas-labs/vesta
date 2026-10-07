package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.homegroup.HubItem
import uk.veritaslabs.vesta.homegroup.HubUiState
import uk.veritaslabs.vesta.homegroup.HubViewModel
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.device_pairing_back
import vesta.shared.generated.resources.hub_add_another
import vesta.shared.generated.resources.hub_body
import vesta.shared.generated.resources.hub_cancel
import vesta.shared.generated.resources.hub_candidates_body
import vesta.shared.generated.resources.hub_candidates_title
import vesta.shared.generated.resources.hub_connected
import vesta.shared.generated.resources.hub_copy_commands
import vesta.shared.generated.resources.hub_device_note
import vesta.shared.generated.resources.hub_failed
import vesta.shared.generated.resources.hub_not_connected
import vesta.shared.generated.resources.hub_own_body
import vesta.shared.generated.resources.hub_own_title
import vesta.shared.generated.resources.hub_remove
import vesta.shared.generated.resources.hub_remove_confirm_body
import vesta.shared.generated.resources.hub_remove_confirm_title
import vesta.shared.generated.resources.hub_remove_device_confirm_body
import vesta.shared.generated.resources.hub_requirement
import vesta.shared.generated.resources.hub_title
import vesta.shared.generated.resources.hub_trust_note
import vesta.shared.generated.resources.hub_unavailable
import vesta.shared.generated.resources.hub_use_candidate
import vesta.shared.generated.resources.hub_waiting
import vesta.shared.generated.resources.ic_arrow_left

private val HubTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HubTestTags {
    const val BackButton = "hub_back_button"
    const val Progress = "hub_progress"
    const val Unavailable = "hub_unavailable"
    const val Commands = "hub_commands"
    const val CopyButton = "hub_copy_button"
    const val Waiting = "hub_waiting"
    const val CandidateRow = "hub_candidate_row"
    const val UseButton = "hub_use_button"
    const val HubCard = "hub_card"
    const val AddAnother = "hub_add_another"
    const val OwnDeviceRow = "hub_own_device_row"
    const val DeviceNote = "hub_device_note"
    const val Status = "hub_status"
    const val RemoveButton = "hub_remove_button"
    const val ConfirmRemove = "hub_confirm_remove"
    const val Failure = "hub_failure"
}

/** The [HubViewModel]-backed screen: it polls the hub while it is on show. */
@Composable
fun HubScreen(viewModel: HubViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.poll() }
    HubScreen(
        state = state,
        onUseAsHub = viewModel::onUseAsHub,
        onRemove = viewModel::onRemove,
        onAddAnother = viewModel::onAddAnother,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * A Home's always-on hubs (docs/adr/ADR-020-syncing-without-overlapping-online-time.md). Each recorded hub ([HubUiState.hubs]) is
 * shown with whether it is in the group and Remove ([onRemove]) after a confirmation. With none, or after [onAddAnother], the
 * commands to run on the always-on machine ([HubUiState.commands], copyable), the agents that have joined the Home's group
 * ([HubUiState.candidates]) and the user's own devices that can be hubs ([HubUiState.ownDevices]), each with a way to choose it
 * ([onUseAsHub]). [HubUiState.unavailable] and [HubUiState.failed] say why nothing can be shown or done. [onBack] leaves.
 */
@Composable
fun HubScreen(
    state: HubUiState,
    modifier: Modifier = Modifier,
    onUseAsHub: (String) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onAddAnother: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= HubTabletBreakpoint) 24.dp else 16.dp
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(contentPadding).padding(top = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.hub_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(Res.string.hub_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                state.loading -> Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.testTag(HubTestTags.Progress))
                }
                state.hubs.isNotEmpty() -> {
                    state.hubs.forEach { hub -> RecordedHub(hub, onRemove = { onRemove(hub.agentId) }) }
                    Text(
                        text = stringResource(Res.string.hub_trust_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.adding) {
                        Enrolment(state, onUseAsHub)
                    } else {
                        TextButton(
                            onClick = onAddAnother,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth().testTag(HubTestTags.AddAnother),
                        ) {
                            Text(stringResource(Res.string.hub_add_another))
                        }
                    }
                }
                state.unavailable -> Text(
                    text = stringResource(Res.string.hub_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag(HubTestTags.Unavailable),
                )
                else -> Enrolment(state, onUseAsHub)
            }
            if (state.failed) {
                Text(
                    text = stringResource(Res.string.hub_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag(HubTestTags.Failure),
                )
            }
        }
        // Last, so it is drawn over the scrolling column and gets the taps.
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().testTag(HubTestTags.BackButton),
        ) {
            Icon(painter = painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.device_pairing_back))
        }
    }
}

/** The commands to run on the hub machine, and the agents that have joined the group as a result. */
@Composable
private fun Enrolment(state: HubUiState, onUseAsHub: (String) -> Unit) {
    @Suppress("DEPRECATION") // see DevicePairingGeneratedScreen: the synchronous clipboard suits a plain-text copy button
    val clipboard = LocalClipboardManager.current
    val commands = state.commands.orEmpty()
    Text(
        text = stringResource(Res.string.hub_requirement),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = displayCommands(commands),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp).testTag(HubTestTags.Commands),
        )
    }
    PrimaryButton(
        label = stringResource(Res.string.hub_copy_commands),
        modifier = Modifier.fillMaxWidth().testTag(HubTestTags.CopyButton),
        onClick = { clipboard.setText(AnnotatedString(commands)) },
    )
    if (state.hubs.isEmpty()) {
        Text(
            text = stringResource(Res.string.hub_trust_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.candidates.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(Res.string.hub_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(HubTestTags.Waiting),
            )
        }
    } else {
        Text(
            text = stringResource(Res.string.hub_candidates_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.hub_candidates_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.candidates.forEach { agentId -> CandidateRow(agentId, onUse = { onUseAsHub(agentId) }) }
    }
    if (state.ownDevices.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.hub_own_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.hub_own_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.ownDevices.forEach { agentId -> CandidateRow(agentId, onUse = { onUseAsHub(agentId) }, tag = HubTestTags.OwnDeviceRow) }
    }
}

@Composable
private fun CandidateRow(agentId: String, onUse: () -> Unit, tag: String = HubTestTags.CandidateRow) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Row(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = shortAgentId(agentId),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUse, modifier = Modifier.testTag(HubTestTags.UseButton)) { Text(stringResource(Res.string.hub_use_candidate)) }
        }
    }
}

/** One recorded hub, whether it is in the group, and Remove, which asks first. */
@Composable
private fun RecordedHub(hub: HubItem, onRemove: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag(HubTestTags.HubCard),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = shortAgentId(hub.agentId),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(if (hub.inGroup) Res.string.hub_connected else Res.string.hub_not_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = if (hub.inGroup) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(HubTestTags.Status),
            )
            if (hub.isDevice) {
                Text(
                    text = stringResource(Res.string.hub_device_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(HubTestTags.DeviceNote),
                )
            }
            // Inside the card, so it is plain which hub it removes.
            TextButton(
                onClick = { confirming = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.End).testTag(HubTestTags.RemoveButton),
            ) { Text(stringResource(Res.string.hub_remove)) }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.hub_remove_confirm_title, shortAgentId(hub.agentId))) },
            text = { Text(stringResource(if (hub.isDevice) Res.string.hub_remove_device_confirm_body else Res.string.hub_remove_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onRemove()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag(HubTestTags.ConfirmRemove),
                ) { Text(stringResource(Res.string.hub_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(Res.string.hub_cancel)) } },
        )
    }
}

/**
 * The enrolment commands as shown: a line longer than a screen's worth (the group invite is several kilobytes of base64) is cut to its
 * start and end, so the Copy button and the machines that join stay in view. Copying still copies the whole of [commands].
 */
internal fun displayCommands(commands: String): String = commands.lines().joinToString("\n") { line ->
    if (line.length <= CommandLineLimit) line else line.take(CommandLineHead) + "…" + line.takeLast(CommandLineTail)
}

private const val CommandLineLimit = 72
private const val CommandLineHead = 48
private const val CommandLineTail = 12

/** An agent id is 64 hex characters: enough of it to tell machines apart. */
internal fun shortAgentId(agentId: String): String = if (agentId.length <= 16) agentId else agentId.take(8) + "…" + agentId.takeLast(8)

private const val PreviewAgent = "9f2c41d07a3be58c1d6e0f4a8b7c2d3e5f60718293a4b5c6d7e8f90a1b2c3d4e"
private const val PreviewAgent2 = "3b7e90a1c5d2486f1e0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e"
private const val PreviewCommands = "x0x group join x0x://invite/AbCdEf…\nx0x group store create 7c1e…a9 mailbox"

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HubScreen(HubUiState(loading = false, commands = PreviewCommands)) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HubScreen(HubUiState(loading = false, commands = PreviewCommands, candidates = listOf(PreviewAgent))) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubScreenPhoneLightHubPreview() {
    VestaTheme(darkTheme = false) { HubScreen(HubUiState(loading = false, hubs = listOf(HubItem(PreviewAgent, inGroup = true)))) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubScreenPhoneDarkHubPreview() {
    VestaTheme(darkTheme = true) { HubScreen(HubUiState(loading = false, hubs = listOf(HubItem(PreviewAgent, inGroup = false)), failed = true)) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubScreenPhoneLightTwoHubsPreview() {
    VestaTheme(darkTheme = false) { HubScreen(HubUiState(loading = false, hubs = twoHubs())) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubScreenPhoneDarkTwoHubsPreview() {
    VestaTheme(darkTheme = true) { HubScreen(HubUiState(loading = false, hubs = twoHubs())) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { HubScreen(HubUiState(loading = false, commands = PreviewCommands, candidates = listOf(PreviewAgent))) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HubScreen(HubUiState(loading = false, unavailable = true)) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubScreenTabletLightHubPreview() {
    VestaTheme(darkTheme = false) { HubScreen(HubUiState(loading = false, hubs = listOf(HubItem(PreviewAgent, inGroup = true)))) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubScreenTabletDarkHubPreview() {
    VestaTheme(darkTheme = true) { HubScreen(HubUiState(loading = false, hubs = listOf(HubItem(PreviewAgent, inGroup = true)))) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubScreenTabletLightAddingPreview() {
    VestaTheme(darkTheme = false) {
        HubScreen(HubUiState(loading = false, hubs = twoHubs().take(1), adding = true, commands = PreviewCommands, ownDevices = listOf(PreviewAgent2)))
    }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubScreenTabletDarkAddingPreview() {
    VestaTheme(darkTheme = true) {
        HubScreen(HubUiState(loading = false, hubs = twoHubs().take(1), adding = true, commands = PreviewCommands, candidates = listOf(PreviewAgent2)))
    }
}

private fun twoHubs() = listOf(HubItem(PreviewAgent, inGroup = true), HubItem(PreviewAgent2, inGroup = true, isDevice = true))
