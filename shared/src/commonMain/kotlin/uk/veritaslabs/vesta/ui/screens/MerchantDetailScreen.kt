package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.ui.components.ContentMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_dots_vertical
import vesta.shared.generated.resources.common_cancel
import vesta.shared.generated.resources.merchant_delete_confirm
import vesta.shared.generated.resources.merchant_delete_confirm_body
import vesta.shared.generated.resources.merchant_delete_confirm_title
import vesta.shared.generated.resources.merchant_delete_menu
import vesta.shared.generated.resources.merchant_detail_agent_id
import vesta.shared.generated.resources.merchant_detail_coordinates
import vesta.shared.generated.resources.merchant_detail_gers_id
import vesta.shared.generated.resources.merchant_detail_hours
import vesta.shared.generated.resources.merchant_detail_location
import vesta.shared.generated.resources.merchant_detail_services
import vesta.shared.generated.resources.merchant_edit_menu
import vesta.shared.generated.resources.merchant_hours_closed
import vesta.shared.generated.resources.merchant_more_options
import vesta.shared.generated.resources.common_back

private val MerchantDetailTabletBreakpoint = 600.dp
/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MerchantDetailTestTags {
    const val BackButton = "merchant_detail_back_button"
    const val Title = "merchant_detail_title"
    const val OverflowButton = "merchant_detail_overflow_button"
    const val EditMenuItem = "merchant_detail_edit_menu_item"
    const val DeleteMenuItem = "merchant_detail_delete_menu_item"
    const val ConfirmDelete = "merchant_detail_delete_confirm"
    const val Address = "merchant_detail_address"
    const val Coordinates = "merchant_detail_coordinates"
    const val GersId = "merchant_detail_gers_id"
    const val AgentId = "merchant_detail_agent_id"
    const val HoursNote = "merchant_detail_hours_note"

    fun service(name: String) = "merchant_detail_service_$name"

    fun hours(day: Weekday) = "merchant_detail_hours_${day.name}"
}

/**
 * One merchant (ADR-026): its type, services, opening hours day by day, location, GERS ID and x0x agent. The overflow menu
 * has Edit ([onEdit]) and Delete ([onDelete], after confirming).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MerchantDetailScreen(
    merchant: Merchant,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= MerchantDetailTabletBreakpoint) 24.dp else 16.dp
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            merchant.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(MerchantDetailTestTags.Title),
                        )
                        Text(
                            merchantTypeLabel(merchant.type),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(MerchantDetailTestTags.BackButton)) {
                        Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(MerchantDetailTestTags.OverflowButton)) {
                            Icon(painterResource(Res.drawable.ic_dots_vertical), contentDescription = stringResource(Res.string.merchant_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.merchant_edit_menu)) },
                                onClick = { menuOpen = false; onEdit() },
                                modifier = Modifier.testTag(MerchantDetailTestTags.EditMenuItem),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.merchant_delete_menu), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmingDelete = true },
                                modifier = Modifier.testTag(MerchantDetailTestTags.DeleteMenuItem),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = contentPadding, vertical = 8.dp)
                    .widthIn(max = ContentMaxWidth),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (merchant.services.isNotEmpty() || merchant.customServices.isNotEmpty()) {
                    Section(stringResource(Res.string.merchant_detail_services)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            merchant.services.forEach { service ->
                                AssistChip(onClick = {}, label = { Text(merchantServiceLabel(service)) }, modifier = Modifier.testTag(MerchantDetailTestTags.service(service.name)))
                            }
                            merchant.customServices.forEach { tag ->
                                AssistChip(onClick = {}, label = { Text(tag) }, modifier = Modifier.testTag(MerchantDetailTestTags.service(tag)))
                            }
                        }
                    }
                }
                if (!merchant.hours.isEmpty) {
                    Section(stringResource(Res.string.merchant_detail_hours)) {
                        Weekday.entries.forEach { day ->
                            val ranges = merchant.hours.on(day)
                            val closed = stringResource(Res.string.merchant_hours_closed)
                            val text = if (ranges.isEmpty()) {
                                closed
                            } else {
                                ranges.joinToString(", ") { "${formatTime(it.openMinute)}–${formatTime(it.closeMinute)}" }
                            }
                            Row(modifier = Modifier.fillMaxWidth().testTag(MerchantDetailTestTags.hours(day))) {
                                Text(weekdayLabel(day), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (ranges.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        merchant.hours.note.takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag(MerchantDetailTestTags.HoursNote))
                        }
                    }
                }
                if (merchant.address.isNotEmpty() || merchant.hasLocation) {
                    Section(stringResource(Res.string.merchant_detail_location)) {
                        merchant.address.takeIf { it.isNotEmpty() }?.let { Text(it, modifier = Modifier.testTag(MerchantDetailTestTags.Address)) }
                        val latitude = merchant.latitude
                        val longitude = merchant.longitude
                        if (latitude != null && longitude != null) {
                            Text(
                                stringResource(Res.string.merchant_detail_coordinates, formatDegrees(latitude), formatDegrees(longitude)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag(MerchantDetailTestTags.Coordinates),
                            )
                        }
                    }
                }
                if (merchant.gersId.isNotEmpty()) {
                    Section(stringResource(Res.string.merchant_detail_gers_id)) {
                        SelectionContainer { Text(merchant.gersId, modifier = Modifier.testTag(MerchantDetailTestTags.GersId)) }
                    }
                }
                if (merchant.x0xAgentId.isNotEmpty()) {
                    Section(stringResource(Res.string.merchant_detail_agent_id)) {
                        SelectionContainer { Text(merchant.x0xAgentId, modifier = Modifier.testTag(MerchantDetailTestTags.AgentId)) }
                    }
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(Res.string.merchant_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.merchant_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }, modifier = Modifier.testTag(MerchantDetailTestTags.ConfirmDelete)) {
                    Text(stringResource(Res.string.merchant_delete_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

@Composable
private fun Section(heading: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** [degrees] to six decimal places (the stored precision), without a trailing run of zeros: "53.4808", "-2.2426". */
internal fun formatDegrees(degrees: Double): String {
    val micro = degreesToMicrodegrees(degrees)
    val sign = if (micro < 0) "-" else ""
    val absolute = kotlin.math.abs(micro.toLong())
    val whole = absolute / MicrodegreesPerDegree
    val fraction = (absolute % MicrodegreesPerDegree).toString().padStart(6, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantDetailPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MerchantDetailScreen(MerchantPreviewMerchants.first()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantDetailPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantDetailScreen(MerchantPreviewMerchants.first()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantDetailTabletLightPreview() {
    VestaTheme(darkTheme = false) { MerchantDetailScreen(MerchantPreviewMerchants.first()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantDetailTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantDetailScreen(MerchantPreviewMerchants.first()) }
}
