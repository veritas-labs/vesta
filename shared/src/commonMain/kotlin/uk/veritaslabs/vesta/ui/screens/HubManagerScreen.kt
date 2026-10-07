package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
import vesta.shared.generated.resources.hub_failed
import vesta.shared.generated.resources.hub_manager_empty_body
import vesta.shared.generated.resources.hub_manager_empty_title
import vesta.shared.generated.resources.hub_manager_how_title
import vesta.shared.generated.resources.hub_manager_setup_title
import vesta.shared.generated.resources.hub_manager_title
import vesta.shared.generated.resources.hub_unavailable
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_clipboard_text
import vesta.shared.generated.resources.ic_devices
import vesta.shared.generated.resources.ic_plus

private val HubManagerContentMaxWidth = 560.dp
private val HubManagerEmptyIconSize = 96.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HubManagerTestTags {
    const val BackButton = "hub_manager_back_button"
    const val HowButton = "hub_manager_how_button"
    const val SetupButton = "hub_manager_setup_button"
    const val AddHub = "hub_manager_add_hub"
    const val EmptyState = "hub_manager_empty_state"
    const val Progress = "hub_manager_progress"
    const val Unavailable = "hub_manager_unavailable"
    const val Failure = "hub_manager_failure"
}

/**
 * The [HubViewModel]-backed screen: it polls the hubs while it is on show. Adding a hub asks the view model for the enrolment
 * commands before [onSetUpHub] opens the screen that shows them.
 */
@Composable
fun HubManagerScreen(
    viewModel: HubViewModel,
    onHowHubsWork: () -> Unit,
    onSetUpHub: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.poll() }
    HubManagerScreen(
        state = state,
        onHowHubsWork = onHowHubsWork,
        onSetUpHub = onSetUpHub,
        onAddHub = {
            viewModel.onAddAnother()
            onSetUpHub()
        },
        onRemove = viewModel::onRemove,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The hub area of a Home, opened from the dashboard's Manage hubs menu item. A full-width button always opens the tutorial on how hubs
 * work ([onHowHubsWork]). Below it are the Home's hubs ([HubUiState.hubs]), each removable ([onRemove]), ending in a plus button that
 * adds another ([onAddHub]). With none there is an empty state whose button sets one up ([onSetUpHub]); [HubUiState.unavailable] and
 * [HubUiState.failed] say why nothing can be done. [onBack] returns to the dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubManagerScreen(
    state: HubUiState,
    modifier: Modifier = Modifier,
    onHowHubsWork: () -> Unit = {},
    onSetUpHub: () -> Unit = {},
    onAddHub: () -> Unit = {},
    onRemove: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.hub_manager_title)) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(HubManagerTestTags.BackButton)) {
                    Icon(painter = painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.device_pairing_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = HubManagerContentMaxWidth).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onHowHubsWork,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().height(60.dp).testTag(HubManagerTestTags.HowButton),
                ) {
                    Icon(painter = painterResource(Res.drawable.ic_clipboard_text), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(Res.string.hub_manager_how_title),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                when {
                    state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag(HubManagerTestTags.Progress))
                    }
                    state.hubs.isEmpty() -> EmptyState(state, onSetUpHub, modifier = Modifier.weight(1f))
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.hubs, key = { it.agentId }) { hub -> RecordedHub(hub, onRemove = { onRemove(hub.agentId) }) }
                        item {
                            if (state.failed) FailureText()
                            AddHubButton(onAddHub)
                        }
                    }
                }
            }
        }
    }
}

/** No hub yet: what one is for and the way to set the first up, or why that can't be done now. */
@Composable
private fun EmptyState(state: HubUiState, onSetUpHub: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(HubManagerTestTags.EmptyState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(HubManagerEmptyIconSize)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(Res.drawable.ic_devices),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(HubManagerEmptyIconSize / 2),
                )
            }
        }
        Text(
            text = stringResource(Res.string.hub_manager_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (state.unavailable) {
            Text(
                text = stringResource(Res.string.hub_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(HubManagerTestTags.Unavailable),
            )
        } else {
            Text(
                text = stringResource(Res.string.hub_manager_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            PrimaryButton(
                label = stringResource(Res.string.hub_manager_setup_title),
                onClick = onSetUpHub,
                modifier = Modifier.testTag(HubManagerTestTags.SetupButton),
            )
        }
        if (state.failed) FailureText()
    }
}

@Composable
private fun FailureText() {
    Text(
        text = stringResource(Res.string.hub_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag(HubManagerTestTags.Failure),
    )
}

/** The last element of the list: a plus button, the same style as the Home picker's, that adds another hub. */
@Composable
private fun AddHubButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .testTag(HubManagerTestTags.AddHub),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_plus),
            contentDescription = stringResource(Res.string.hub_add_another),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

private const val PreviewAgent = "9f2c41d07a3be58c1d6e0f4a8b7c2d3e5f60718293a4b5c6d7e8f90a1b2c3d4e"
private const val PreviewAgent2 = "3b7e90a1c5d2486f1e0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e"

private fun previewHubs() = HubUiState(
    loading = false,
    hubs = listOf(HubItem(PreviewAgent, inGroup = true), HubItem(PreviewAgent2, inGroup = false, isDevice = true)),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubManagerScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HubManagerScreen(HubUiState(loading = false)) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubManagerScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HubManagerScreen(HubUiState(loading = false)) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubManagerScreenPhoneLightHubsPreview() {
    VestaTheme(darkTheme = false) { HubManagerScreen(previewHubs()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubManagerScreenPhoneDarkHubsPreview() {
    VestaTheme(darkTheme = true) { HubManagerScreen(previewHubs()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubManagerScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { HubManagerScreen(HubUiState(loading = false)) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubManagerScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HubManagerScreen(HubUiState(loading = false)) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubManagerScreenTabletLightHubsPreview() {
    VestaTheme(darkTheme = false) { HubManagerScreen(previewHubs()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubManagerScreenTabletDarkHubsPreview() {
    VestaTheme(darkTheme = true) { HubManagerScreen(previewHubs()) }
}
