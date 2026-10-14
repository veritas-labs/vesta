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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.EmptyState
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_plus
import vesta.shared.generated.resources.merchant_day_friday
import vesta.shared.generated.resources.merchant_day_monday
import vesta.shared.generated.resources.merchant_day_saturday
import vesta.shared.generated.resources.merchant_day_sunday
import vesta.shared.generated.resources.merchant_day_thursday
import vesta.shared.generated.resources.merchant_day_tuesday
import vesta.shared.generated.resources.merchant_day_wednesday
import vesta.shared.generated.resources.merchant_service_bakery
import vesta.shared.generated.resources.merchant_service_butcher
import vesta.shared.generated.resources.merchant_service_cafe
import vesta.shared.generated.resources.merchant_service_deli
import vesta.shared.generated.resources.merchant_service_fishmonger
import vesta.shared.generated.resources.merchant_service_greengrocer
import vesta.shared.generated.resources.merchant_service_groceries
import vesta.shared.generated.resources.merchant_service_household
import vesta.shared.generated.resources.merchant_service_pharmacy
import vesta.shared.generated.resources.merchant_type_independent_trader
import vesta.shared.generated.resources.merchant_type_local_chain
import vesta.shared.generated.resources.merchant_type_market_trader
import vesta.shared.generated.resources.merchant_type_national_chain
import vesta.shared.generated.resources.merchant_type_online_retailer
import vesta.shared.generated.resources.merchant_type_other
import vesta.shared.generated.resources.merchants_add
import vesta.shared.generated.resources.merchants_empty_subtitle
import vesta.shared.generated.resources.merchants_empty_title
import vesta.shared.generated.resources.merchants_filter_label
import vesta.shared.generated.resources.merchants_no_matches
import vesta.shared.generated.resources.merchants_search_label
import vesta.shared.generated.resources.merchants_title
import vesta.shared.generated.resources.common_back

private val MerchantsTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy or merchant names. */
internal object MerchantsTestTags {
    const val BackButton = "merchants_back_button"
    const val AddMerchantButton = "merchants_add_button"
    const val SearchField = "merchants_search_field"
    const val EmptyMessage = "merchants_empty_message"

    fun typeChip(type: MerchantType) = "merchants_type_chip_${type.name}"

    /** Tag for the row of the merchant with the given [key]. */
    fun merchant(key: String) = "merchants_merchant_$key"
}

@Composable
internal fun merchantTypeLabel(type: MerchantType): String = stringResource(
    when (type) {
        MerchantType.MarketTrader -> Res.string.merchant_type_market_trader
        MerchantType.IndependentTrader -> Res.string.merchant_type_independent_trader
        MerchantType.LocalChain -> Res.string.merchant_type_local_chain
        MerchantType.NationalChain -> Res.string.merchant_type_national_chain
        MerchantType.OnlineRetailer -> Res.string.merchant_type_online_retailer
        MerchantType.Other -> Res.string.merchant_type_other
    },
)

@Composable
internal fun merchantServiceLabel(service: MerchantService): String = stringResource(
    when (service) {
        MerchantService.Groceries -> Res.string.merchant_service_groceries
        MerchantService.Butcher -> Res.string.merchant_service_butcher
        MerchantService.Greengrocer -> Res.string.merchant_service_greengrocer
        MerchantService.Fishmonger -> Res.string.merchant_service_fishmonger
        MerchantService.Bakery -> Res.string.merchant_service_bakery
        MerchantService.Deli -> Res.string.merchant_service_deli
        MerchantService.Cafe -> Res.string.merchant_service_cafe
        MerchantService.Pharmacy -> Res.string.merchant_service_pharmacy
        MerchantService.Household -> Res.string.merchant_service_household
    },
)

@Composable
internal fun weekdayLabel(day: Weekday): String = stringResource(
    when (day) {
        Weekday.Monday -> Res.string.merchant_day_monday
        Weekday.Tuesday -> Res.string.merchant_day_tuesday
        Weekday.Wednesday -> Res.string.merchant_day_wednesday
        Weekday.Thursday -> Res.string.merchant_day_thursday
        Weekday.Friday -> Res.string.merchant_day_friday
        Weekday.Saturday -> Res.string.merchant_day_saturday
        Weekday.Sunday -> Res.string.merchant_day_sunday
    },
)

/** The merchant's services as one line, predefined first (labelled) then the typed ones. */
@Composable
internal fun merchantServicesLine(merchant: Merchant): String =
    (merchant.services.map { merchantServiceLabel(it) } + merchant.customServices).joinToString(", ")

/** The merchants list (ADR-026) wired to [viewModel]; blank until the merchants have loaded, so the empty message doesn't flash. */
@Composable
fun MerchantsScreen(
    viewModel: MerchantsViewModel,
    modifier: Modifier = Modifier,
    onMerchantSelected: (Merchant) -> Unit = {},
    onBack: () -> Unit = {},
    onAddMerchant: () -> Unit = {},
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        MerchantsScreen(
            merchants = state.merchants,
            filter = state.filter,
            modifier = modifier,
            onMerchantSelected = onMerchantSelected,
            onBack = onBack,
            onAddMerchant = onAddMerchant,
            onQueryChange = viewModel::setQuery,
            onToggleType = viewModel::toggleType,
        )
    }
}

/**
 * The Home's merchants: a search field, a filter chip per type in use and a row per merchant in [merchants] that [filter]
 * lets through, each showing its name, type and services. A tap on a row calls [onMerchantSelected]; [onAddMerchant] is the
 * floating action button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantsScreen(
    merchants: List<Merchant>,
    modifier: Modifier = Modifier,
    filter: MerchantsFilter = MerchantsFilter(),
    onMerchantSelected: (Merchant) -> Unit = {},
    onBack: () -> Unit = {},
    onAddMerchant: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onToggleType: (MerchantType) -> Unit = {},
) {
    val filterDescription = stringResource(Res.string.merchants_filter_label)
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= MerchantsTabletBreakpoint) 24.dp else 16.dp
        val visible = filterMerchants(merchants, filter)

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.merchants_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(MerchantsTestTags.BackButton)) {
                        Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            if (merchants.isNotEmpty()) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(Res.string.merchants_search_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = contentPadding).testTag(MerchantsTestTags.SearchField),
                )
                val types = MerchantType.entries.filter { type -> merchants.any { it.type == type } }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = contentPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = filterDescription },
                ) {
                    items(types, key = { it.name }) { type ->
                        FilterChip(
                            selected = filter.type == type,
                            onClick = { onToggleType(type) },
                            label = { Text(merchantTypeLabel(type)) },
                            modifier = Modifier.testTag(MerchantsTestTags.typeChip(type)),
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        if (merchants.isEmpty()) EmptyStateArt.Merchants else EmptyStateArt.NoResults,
                        title = stringResource(if (merchants.isEmpty()) Res.string.merchants_empty_title else Res.string.merchants_no_matches),
                        body = if (merchants.isEmpty()) stringResource(Res.string.merchants_empty_subtitle) else null,
                        modifier = Modifier.testTag(MerchantsTestTags.EmptyMessage),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = contentPadding, end = contentPadding, top = 4.dp, bottom = FabClearance),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.key }) { merchant ->
                        MerchantRow(merchant, onClick = { onMerchantSelected(merchant) }, modifier = Modifier.testTag(MerchantsTestTags.merchant(merchant.key)))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddMerchant,
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(contentPadding)
                .testTag(MerchantsTestTags.AddMerchantButton),
        ) {
            Icon(painterResource(Res.drawable.ic_plus), contentDescription = stringResource(Res.string.merchants_add))
        }
    }
}

@Composable
private fun MerchantRow(merchant: Merchant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(merchant.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(merchantTypeLabel(merchant.type), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            merchantServicesLine(merchant).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val WeekdayHours = listOf(OpenRange(9 * 60, 17 * 60 + 30))

internal val MerchantPreviewMerchants = listOf(
    Merchant(
        key = "butcher", name = "Hargreaves & Son", type = MerchantType.IndependentTrader,
        services = listOf(MerchantService.Butcher, MerchantService.Deli), customServices = listOf("Game in season"),
        hours = OpeningHours(
            days = Weekday.entries.take(6).associateWith { WeekdayHours } + (Weekday.Saturday to listOf(OpenRange(8 * 60, 13 * 60))),
        ),
        latitude = 53.4808, longitude = -2.2426, address = "12 Market Street",
        gersId = "08f194ad3a5a5c1b0200d4a3f2c1e9a7", x0xAgentId = "a".repeat(64),
    ),
    Merchant(
        key = "tesco", name = "Tesco Extra", type = MerchantType.NationalChain,
        services = listOf(MerchantService.Groceries, MerchantService.Pharmacy, MerchantService.Household),
        hours = OpeningHours(Weekday.entries.associateWith { listOf(OpenRange(6 * 60, 24 * 60)) }),
    ),
    Merchant(
        key = "stall", name = "Dave's Veg Stall", type = MerchantType.MarketTrader, services = listOf(MerchantService.Greengrocer),
        hours = OpeningHours(
            days = mapOf(Weekday.Wednesday to listOf(OpenRange(8 * 60, 15 * 60)), Weekday.Saturday to listOf(OpenRange(8 * 60, 16 * 60))),
            note = "Market days only",
        ),
        address = "Stall 14, north aisle",
    ),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantsScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MerchantsScreen(MerchantPreviewMerchants) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantsScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantsScreen(MerchantPreviewMerchants) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantsScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { MerchantsScreen(MerchantPreviewMerchants) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantsScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantsScreen(MerchantPreviewMerchants) }
}

@Preview(name = "Empty Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantsScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MerchantsScreen(merchants = emptyList()) }
}

@Preview(name = "Empty Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MerchantsScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantsScreen(merchants = emptyList()) }
}

@Preview(name = "Empty Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantsScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { MerchantsScreen(merchants = emptyList()) }
}

@Preview(name = "Empty Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MerchantsScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MerchantsScreen(merchants = emptyList()) }
}
