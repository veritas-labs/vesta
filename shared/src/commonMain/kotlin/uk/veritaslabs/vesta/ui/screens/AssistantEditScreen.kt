package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.assistant_activity
import vesta.shared.generated.resources.assistant_activity_applied
import vesta.shared.generated.resources.assistant_activity_empty
import vesta.shared.generated.resources.assistant_activity_rejected
import vesta.shared.generated.resources.assistant_edit_access
import vesta.shared.generated.resources.assistant_edit_agent_id
import vesta.shared.generated.resources.assistant_edit_agent_id_hint
import vesta.shared.generated.resources.assistant_edit_ask_each_time
import vesta.shared.generated.resources.assistant_edit_name
import vesta.shared.generated.resources.assistant_edit_name_hint
import vesta.shared.generated.resources.assistant_edit_no_homes
import vesta.shared.generated.resources.assistant_edit_pantry_add
import vesta.shared.generated.resources.assistant_edit_remove
import vesta.shared.generated.resources.assistant_edit_remove_body
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.common_cancel
import vesta.shared.generated.resources.assistant_edit_remove_confirm
import vesta.shared.generated.resources.assistant_edit_remove_title
import vesta.shared.generated.resources.assistant_edit_save
import vesta.shared.generated.resources.assistant_edit_title
import vesta.shared.generated.resources.assistant_edit_trust_title
import vesta.shared.generated.resources.assistant_edit_trust_other_way
import vesta.shared.generated.resources.assistant_edit_trust_body
import vesta.shared.generated.resources.assistant_edit_title_new

private val AssistantEditTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or names. */
internal object AssistantEditTestTags {
    const val BackButton = "assistant_edit_back_button"
    const val AgentIdField = "assistant_edit_agent_id"
    const val NameField = "assistant_edit_name"
    const val SaveButton = "assistant_edit_save"
    const val RemoveButton = "assistant_edit_remove"
    const val ConfirmRemove = "assistant_edit_confirm_remove"
    const val TrustCommand = "assistant_edit_trust_command"

    fun pantryAdd(homeId: String) = "assistant_edit_pantry_add_$homeId"
    fun askEachTime(homeId: String) = "assistant_edit_ask_$homeId"
    const val ActivityRow = "assistant_edit_activity_row"
}

/** What an assistant may do in one Home, as the form shows it. [ask] only matters while [pantryAdd] is on. */
data class HomeAccess(val homeId: String, val homeName: String, val pantryAdd: Boolean, val ask: Boolean)

/** One line of an assistant's activity. */
data class ActivityRow(val summary: String, val homeName: String?, val applied: Boolean)

/**
 * Adds an assistant or edits one (ADR-027). With a null [agentId] the agent ID is typed in; otherwise it is shown
 * and fixed. The user names the assistant and, per Home in [homes], switches on what it may do and whether to be
 * asked each time. [onSave] gets the ID, the name and the form's [HomeAccess] list; a new assistant can't be saved
 * until it has an ID. [onRemove] (existing assistants only) asks for confirmation first. [activity] is newest first.
 * UI only — the caller persists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantEditScreen(
    agentId: String?,
    initialName: String,
    homes: List<HomeAccess>,
    modifier: Modifier = Modifier,
    activity: List<ActivityRow> = emptyList(),
    onSave: (agentId: String, name: String, access: List<HomeAccess>) -> Unit = { _, _, _ -> },
    onRemove: (() -> Unit)? = null,
    onBack: () -> Unit = {},
) {
    var typedId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(initialName) }
    val access = remember(homes) { mutableStateListOf(*homes.toTypedArray()) }
    var removing by remember { mutableStateOf(false) }
    val effectiveId = (agentId ?: typedId).trim()

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= AssistantEditTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Text(stringResource(if (agentId == null) Res.string.assistant_edit_title_new else Res.string.assistant_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(AssistantEditTestTags.BackButton)) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.common_cancel),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = contentPadding, end = contentPadding, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = effectiveId,
                    onValueChange = { typedId = it },
                    readOnly = agentId != null,
                    label = { Text(stringResource(Res.string.assistant_edit_agent_id)) },
                    placeholder = { Text(stringResource(Res.string.assistant_edit_agent_id_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(AssistantEditTestTags.AgentIdField),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.assistant_edit_name)) },
                    placeholder = { Text(stringResource(Res.string.assistant_edit_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(AssistantEditTestTags.NameField),
                )
                if (effectiveId.isNotEmpty()) {
                    TrustHint(effectiveId)
                }
                Text(
                    text = stringResource(Res.string.assistant_edit_access),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (access.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.assistant_edit_no_homes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                access.forEachIndexed { index, home ->
                    HomeAccessCard(home, onChange = { access[index] = it })
                }
                Button(
                    onClick = { onSave(effectiveId, name.trim(), access.toList()) },
                    enabled = effectiveId.isNotEmpty(),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().testTag(AssistantEditTestTags.SaveButton),
                ) { Text(stringResource(Res.string.assistant_edit_save)) }
                if (agentId != null) {
                    Text(
                        text = stringResource(Res.string.assistant_activity),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (activity.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.assistant_activity_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    activity.forEach { row -> ActivityRowView(row) }
                }
                if (onRemove != null) {
                    OutlinedButton(
                        onClick = { removing = true },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(AssistantEditTestTags.RemoveButton),
                    ) { Text(stringResource(Res.string.assistant_edit_remove)) }
                }
            }
        }
    }

    if (removing && onRemove != null) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(Res.string.assistant_edit_remove_title)) },
            text = { Text(stringResource(Res.string.assistant_edit_remove_body, name.ifBlank { effectiveId })) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = false
                        onRemove()
                    },
                    modifier = Modifier.testTag(AssistantEditTestTags.ConfirmRemove),
                ) { Text(stringResource(Res.string.assistant_edit_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = false }) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }
}

/** The x0x command that makes [agentId] reachable: Vesta's daemon drops messages from agents it doesn't trust. */
fun trustCommand(agentId: String) = "x0x trust set $agentId trusted"

@Composable
private fun TrustHint(agentId: String) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.assistant_edit_trust_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.assistant_edit_trust_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = trustCommand(agentId),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(AssistantEditTestTags.TrustCommand),
            )
        }
        Text(
            text = stringResource(Res.string.assistant_edit_trust_other_way),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeAccessCard(access: HomeAccess, onChange: (HomeAccess) -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = access.homeName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        SwitchRow(
            label = stringResource(Res.string.assistant_edit_pantry_add),
            checked = access.pantryAdd,
            onCheckedChange = { on -> onChange(access.copy(pantryAdd = on, ask = if (on) true else access.ask)) },
            modifier = Modifier.testTag(AssistantEditTestTags.pantryAdd(access.homeId)),
        )
        if (access.pantryAdd) {
            SwitchRow(
                label = stringResource(Res.string.assistant_edit_ask_each_time),
                checked = access.ask,
                onCheckedChange = { onChange(access.copy(ask = it)) },
                modifier = Modifier.testTag(AssistantEditTestTags.askEachTime(access.homeId)),
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActivityRowView(row: ActivityRow) {
    Column(modifier = Modifier.fillMaxWidth().testTag(AssistantEditTestTags.ActivityRow)) {
        Text(
            text = row.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = listOfNotNull(
                row.homeName,
                stringResource(if (row.applied) Res.string.assistant_activity_applied else Res.string.assistant_activity_rejected),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val AssistantEditPreviewHomes = listOf(
    HomeAccess("h1", "The Johnsons", pantryAdd = true, ask = false),
    HomeAccess("h2", "Flat 4", pantryAdd = false, ask = true),
)
private val AssistantEditPreviewActivity = listOf(
    ActivityRow("Add 2 × Heinz Ketchup to the Pantry", "The Johnsons", applied = true),
    ActivityRow("Add 1 × Milk to the Pantry: not permitted for this home", "Flat 4", applied = false),
)

@Composable
private fun AssistantEditPreview(darkTheme: Boolean) {
    VestaTheme(darkTheme = darkTheme) {
        AssistantEditScreen(
            agentId = "63ab3172ff7b4afa6717974c9f05946481a0e9b90e9533da8d332933e781d930",
            initialName = "Grok Bot",
            homes = AssistantEditPreviewHomes,
            activity = AssistantEditPreviewActivity,
            onRemove = {},
        )
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AssistantEditScreenPhoneLightPreview() = AssistantEditPreview(darkTheme = false)

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AssistantEditScreenPhoneDarkPreview() = AssistantEditPreview(darkTheme = true)

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AssistantEditScreenTabletLightPreview() = AssistantEditPreview(darkTheme = false)

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AssistantEditScreenTabletDarkPreview() = AssistantEditPreview(darkTheme = true)
