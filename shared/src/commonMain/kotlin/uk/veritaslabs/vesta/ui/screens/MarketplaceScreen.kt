package uk.veritaslabs.vesta.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_chevron_right
import vesta.shared.generated.resources.ic_merchants
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.marketplace_local_merchants
import vesta.shared.generated.resources.marketplace_title

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MarketplaceTestTags {
    const val BackButton = "marketplace_back_button"
    const val LocalMerchants = "marketplace_local_merchants"
}

/**
 * The Marketplace (ADR-026): where a Home finds the places it buys from. For now it has one option, Local Merchants
 * ([onLocalMerchantsClick]); the back arrow calls [onBack].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onLocalMerchantsClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
    ) {
        TopAppBar(
            title = { Text(stringResource(Res.string.marketplace_title)) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(MarketplaceTestTags.BackButton)) {
                    Icon(painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.common_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onLocalMerchantsClick)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .testTag(MarketplaceTestTags.LocalMerchants),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(Res.drawable.ic_merchants),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 16.dp),
            )
            Text(
                stringResource(Res.string.marketplace_local_merchants),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MarketplaceScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MarketplaceScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MarketplaceScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MarketplaceScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MarketplaceScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { MarketplaceScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MarketplaceScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MarketplaceScreen() }
}
