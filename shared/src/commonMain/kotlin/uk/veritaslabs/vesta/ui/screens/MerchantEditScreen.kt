package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.ui.components.ContentMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.ic_minus
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.merchant_edit_add_range
import vesta.shared.generated.resources.merchant_edit_add_title
import vesta.shared.generated.resources.merchant_edit_address_label
import vesta.shared.generated.resources.merchant_edit_agent_id_label
import vesta.shared.generated.resources.common_cancel
import vesta.shared.generated.resources.merchant_edit_close_label
import vesta.shared.generated.resources.merchant_edit_coordinates_error
import vesta.shared.generated.resources.merchant_edit_custom_services_label
import vesta.shared.generated.resources.merchant_edit_edit_title
import vesta.shared.generated.resources.merchant_edit_gers_id_label
import vesta.shared.generated.resources.merchant_edit_hours_error
import vesta.shared.generated.resources.merchant_edit_hours_heading
import vesta.shared.generated.resources.merchant_edit_hours_note_label
import vesta.shared.generated.resources.merchant_edit_latitude_label
import vesta.shared.generated.resources.merchant_edit_location_heading
import vesta.shared.generated.resources.merchant_edit_longitude_label
import vesta.shared.generated.resources.merchant_edit_name_label
import vesta.shared.generated.resources.merchant_edit_open_label
import vesta.shared.generated.resources.merchant_edit_remove_range
import vesta.shared.generated.resources.merchant_edit_save
import vesta.shared.generated.resources.merchant_edit_services_label
import vesta.shared.generated.resources.merchant_edit_time_hint
import vesta.shared.generated.resources.merchant_edit_type_label

private val MerchantEditTabletBreakpoint = 600.dp
private const val MaxLatitude = 90.0
private const val MaxLongitude = 180.0

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MerchantEditTestTags {
    const val CancelButton = "merchant_edit_cancel_button"
    const val SaveButton = "merchant_edit_save_button"
    const val NameField = "merchant_edit_name"
    const val CustomServicesField = "merchant_edit_custom_services"
    const val HoursNoteField = "merchant_edit_hours_note"
    const val AddressField = "merchant_edit_address"
    const val LatitudeField = "merchant_edit_latitude"
    const val LongitudeField = "merchant_edit_longitude"
    const val CoordinatesError = "merchant_edit_coordinates_error"
    const val HoursError = "merchant_edit_hours_error"
    const val GersIdField = "merchant_edit_gers_id"
    const val AgentIdField = "merchant_edit_agent_id"

    fun typeChip(type: MerchantType) = "merchant_edit_type_${type.name}"

    fun serviceChip(service: MerchantService) = "merchant_edit_service_${service.name}"

    fun addRange(day: Weekday) = "merchant_edit_add_range_${day.name}"

    fun removeRange(day: Weekday, index: Int) = "merchant_edit_remove_range_${day.name}_$index"

    fun openField(day: Weekday, index: Int) = "merchant_edit_open_${day.name}_$index"

    fun closeField(day: Weekday, index: Int) = "merchant_edit_close_${day.name}_$index"
}

/** One stretch of opening hours as typed: "HH:MM" in each box; both blank means nothing entered yet. */
internal data class RangeDraft(val open: String = "", val close: String = "") {
    val isBlank: Boolean get() = open.isBlank() && close.isBlank()

    /** The range these times make, or null if either is not a time or it doesn't open before it closes. */
    val range: OpenRange? get() = parseTime(open)?.let { from -> parseTime(close)?.let { to -> OpenRange(from, to) } }?.takeIf { it.isValid }

    /** True when there is nothing typed, or what is typed makes a range. */
    val isValid: Boolean get() = isBlank || range != null
}

private fun OpeningHours.drafts(): Map<Weekday, List<RangeDraft>> =
    Weekday.entries.associateWith { day -> on(day).map { RangeDraft(formatTime(it.openMinute), formatTime(it.closeMinute)) } }

internal fun Map<Weekday, List<RangeDraft>>.toHours(note: String) =
    OpeningHours(mapValues { (_, drafts) -> drafts.mapNotNull { it.range } }, note).normalised()

/**
 * The form for adding a merchant ([merchant] null) or editing one: name, type, services (the known ones as chips plus
 * typed extras), opening hours for each day, location (address, latitude and longitude), GERS ID and x0x agent ID.
 * Save is enabled once there is a name, every typed time is a time, and the coordinates are either both valid or both blank.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MerchantEditScreen(
    merchant: Merchant?,
    modifier: Modifier = Modifier,
    onSave: (Merchant) -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val original = merchant ?: Merchant()
    var name by remember(merchant) { mutableStateOf(original.name) }
    var type by remember(merchant) { mutableStateOf(original.type) }
    var services by remember(merchant) { mutableStateOf(original.services.toSet()) }
    var customServices by remember(merchant) { mutableStateOf(original.customServices.joinToString(", ")) }
    var hours by remember(merchant) { mutableStateOf(original.hours.drafts()) }
    var hoursNote by remember(merchant) { mutableStateOf(original.hours.note) }
    var address by remember(merchant) { mutableStateOf(original.address) }
    var latitude by remember(merchant) { mutableStateOf(original.latitude?.let(::formatDegrees).orEmpty()) }
    var longitude by remember(merchant) { mutableStateOf(original.longitude?.let(::formatDegrees).orEmpty()) }
    var gersId by remember(merchant) { mutableStateOf(original.gersId) }
    var agentId by remember(merchant) { mutableStateOf(original.x0xAgentId) }

    val latitudeValue = parseCoordinate(latitude, MaxLatitude)
    val longitudeValue = parseCoordinate(longitude, MaxLongitude)
    val coordinatesValid = (latitude.isBlank() && longitude.isBlank()) || (latitudeValue != null && longitudeValue != null)
    val hoursValid = hours.values.all { drafts -> drafts.all { it.isValid } }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= MerchantEditTabletBreakpoint) 24.dp else 16.dp
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(if (merchant == null) Res.string.merchant_edit_add_title else Res.string.merchant_edit_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag(MerchantEditTestTags.CancelButton)) {
                        Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.common_cancel))
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
                    .padding(horizontal = contentPadding)
                    .padding(bottom = 32.dp),
            ) {
                Column(modifier = Modifier.widthIn(max = ContentMaxWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.merchant_edit_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(MerchantEditTestTags.NameField),
                    )
                    Heading(stringResource(Res.string.merchant_edit_type_label))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MerchantType.entries.forEach { option ->
                            FilterChip(
                                selected = type == option,
                                onClick = { type = option },
                                label = { Text(merchantTypeLabel(option)) },
                                modifier = Modifier.testTag(MerchantEditTestTags.typeChip(option)),
                            )
                        }
                    }
                    Heading(stringResource(Res.string.merchant_edit_services_label))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MerchantService.entries.forEach { option ->
                            FilterChip(
                                selected = option in services,
                                onClick = { services = if (option in services) services - option else services + option },
                                label = { Text(merchantServiceLabel(option)) },
                                modifier = Modifier.testTag(MerchantEditTestTags.serviceChip(option)),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customServices,
                        onValueChange = { customServices = it },
                        label = { Text(stringResource(Res.string.merchant_edit_custom_services_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(MerchantEditTestTags.CustomServicesField),
                    )
                    Heading(stringResource(Res.string.merchant_edit_hours_heading))
                    Weekday.entries.forEach { day ->
                        DayHoursEditor(
                            day = day,
                            drafts = hours[day].orEmpty(),
                            onChange = { changed -> hours = hours + (day to changed) },
                        )
                    }
                    if (!hoursValid) {
                        Text(
                            stringResource(Res.string.merchant_edit_hours_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(MerchantEditTestTags.HoursError),
                        )
                    }
                    OutlinedTextField(
                        value = hoursNote,
                        onValueChange = { hoursNote = it },
                        label = { Text(stringResource(Res.string.merchant_edit_hours_note_label)) },
                        modifier = Modifier.fillMaxWidth().testTag(MerchantEditTestTags.HoursNoteField),
                    )
                    Heading(stringResource(Res.string.merchant_edit_location_heading))
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(Res.string.merchant_edit_address_label)) },
                        modifier = Modifier.fillMaxWidth().testTag(MerchantEditTestTags.AddressField),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = { latitude = it },
                            label = { Text(stringResource(Res.string.merchant_edit_latitude_label)) },
                            singleLine = true,
                            isError = !coordinatesValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag(MerchantEditTestTags.LatitudeField),
                        )
                        OutlinedTextField(
                            value = longitude,
                            onValueChange = { longitude = it },
                            label = { Text(stringResource(Res.string.merchant_edit_longitude_label)) },
                            singleLine = true,
                            isError = !coordinatesValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag(MerchantEditTestTags.LongitudeField),
                        )
                    }
                    if (!coordinatesValid) {
                        Text(
                            stringResource(Res.string.merchant_edit_coordinates_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(MerchantEditTestTags.CoordinatesError),
                        )
                    }
                    OutlinedTextField(
                        value = gersId,
                        onValueChange = { gersId = it },
                        label = { Text(stringResource(Res.string.merchant_edit_gers_id_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(MerchantEditTestTags.GersIdField),
                    )
                    OutlinedTextField(
                        value = agentId,
                        onValueChange = { agentId = it },
                        label = { Text(stringResource(Res.string.merchant_edit_agent_id_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(MerchantEditTestTags.AgentIdField),
                    )
                    PrimaryButton(
                        label = stringResource(Res.string.merchant_edit_save),
                        enabled = name.isNotBlank() && coordinatesValid && hoursValid,
                        onClick = {
                            val (known, custom) = splitServices(services.map { it.name } + customServices.split(','))
                            onSave(
                                original.copy(
                                    name = name.trim(),
                                    type = type,
                                    services = known,
                                    customServices = custom,
                                    hours = hours.toHours(hoursNote),
                                    latitude = latitudeValue.takeIf { coordinatesValid && latitude.isNotBlank() },
                                    longitude = longitudeValue.takeIf { coordinatesValid && longitude.isNotBlank() },
                                    address = address.trim(),
                                    gersId = gersId.trim(),
                                    x0xAgentId = agentId.trim(),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag(MerchantEditTestTags.SaveButton),
                    )
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

/** One weekday's row of opening hours: its ranges as open and close boxes with a remove button each, and a button to add a range. */
@Composable
private fun DayHoursEditor(day: Weekday, drafts: List<RangeDraft>, onChange: (List<RangeDraft>) -> Unit) {
    val dayName = weekdayLabel(day)
    val hint = stringResource(Res.string.merchant_edit_time_hint)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange(drafts + RangeDraft()) }, modifier = Modifier.testTag(MerchantEditTestTags.addRange(day))) {
                Icon(painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.merchant_edit_add_range, dayName))
            }
        }
        drafts.forEachIndexed { index, draft ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.open,
                    onValueChange = { onChange(drafts.toMutableList().also { list -> list[index] = draft.copy(open = it) }) },
                    label = { Text(stringResource(Res.string.merchant_edit_open_label)) },
                    placeholder = { Text(hint) },
                    singleLine = true,
                    isError = !draft.isValid,
                    modifier = Modifier.weight(1f).testTag(MerchantEditTestTags.openField(day, index)),
                )
                OutlinedTextField(
                    value = draft.close,
                    onValueChange = { onChange(drafts.toMutableList().also { list -> list[index] = draft.copy(close = it) }) },
                    label = { Text(stringResource(Res.string.merchant_edit_close_label)) },
                    placeholder = { Text(hint) },
                    singleLine = true,
                    isError = !draft.isValid,
                    modifier = Modifier.weight(1f).testTag(MerchantEditTestTags.closeField(day, index)),
                )
                IconButton(
                    onClick = { onChange(drafts.filterIndexed { i, _ -> i != index }) },
                    modifier = Modifier.testTag(MerchantEditTestTags.removeRange(day, index)),
                ) {
                    Icon(painterResource(Res.drawable.ic_minus), contentDescription = stringResource(Res.string.merchant_edit_remove_range, dayName))
                }
            }
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantEditPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MerchantEditScreen(MerchantPreviewMerchants.first()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantEditPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantEditScreen(MerchantPreviewMerchants.first()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantEditTabletLightPreview() {
    VestaTheme(darkTheme = false) { MerchantEditScreen(merchant = null) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantEditTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantEditScreen(merchant = null) }
}
