package uk.veritaslabs.vesta.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_tag
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.shopping_filter_hide_checked
import vesta.shared.generated.resources.shopping_filter_label
import vesta.shared.generated.resources.shopping_filter_no_tags
import vesta.shared.generated.resources.shopping_filter_tags_heading
import vesta.shared.generated.resources.shopping_filter_title

private val FilterTabletBreakpoint = 600.dp

/** Stable hooks for UI tests on [ShoppingListFilterScreen]. */
internal object ShoppingListFilterTestTags {
    const val BackButton = "shopping_filter_back_button"
    const val HideCheckedSwitch = "shopping_filter_hide_checked_switch"
}

/** The shopping list's filter screen, wired to [viewModel]. */
@Composable
fun ShoppingListFilterScreen(
    viewModel: ShoppingListViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShoppingListFilterScreen(
        tags = shoppingTags(state.items),
        selectedTags = state.selectedTags,
        onToggleTag = viewModel::toggleTag,
        hideChecked = state.hideChecked,
        onHideCheckedChange = viewModel::setHideChecked,
        modifier = modifier,
        onBack = onBack,
    )
}

/**
 * Filters for the shopping list: one chip per tag in [tags] ([selectedTags], lower-cased, are the ones switched on;
 * [onToggleTag] is called with a chip's tag when it is tapped), and a "Hide checked items" switch
 * ([hideChecked], [onHideCheckedChange]). [onBack] is the top-bar back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShoppingListFilterScreen(
    tags: List<String>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    hideChecked: Boolean,
    onHideCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val filterDescription = stringResource(Res.string.shopping_filter_label)
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= FilterTabletBreakpoint) 24.dp else 16.dp
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.shopping_filter_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(ShoppingListFilterTestTags.BackButton)) {
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
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = contentPadding),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = stringResource(Res.string.shopping_filter_hide_checked),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = hideChecked,
                        onCheckedChange = onHideCheckedChange,
                        modifier = Modifier.testTag(ShoppingListFilterTestTags.HideCheckedSwitch),
                    )
                }
                Text(
                    text = stringResource(Res.string.shopping_filter_tags_heading),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.shopping_filter_no_tags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = filterDescription },
                    ) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tag.lowercase() in selectedTags,
                                onClick = { onToggleTag(tag) },
                                label = { Text(tag) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_tag),
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.testTag(ShoppingListTestTags.filterChip(tag)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PreviewTags = listOf("Butcher", "Farm shop", "Supermarket")

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListFilterPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListFilterScreen(PreviewTags, setOf("farm shop"), {}, true, {}) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ShoppingListFilterPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListFilterScreen(PreviewTags, setOf("farm shop"), {}, true, {}) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListFilterTabletLightPreview() {
    VestaTheme(darkTheme = false) { ShoppingListFilterScreen(PreviewTags, setOf("farm shop"), {}, true, {}) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ShoppingListFilterTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ShoppingListFilterScreen(PreviewTags, setOf("farm shop"), {}, true, {}) }
}
