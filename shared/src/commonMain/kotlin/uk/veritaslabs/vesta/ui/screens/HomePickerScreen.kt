package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.AvatarButton
import uk.veritaslabs.vesta.ui.components.AvatarButtonOrientation
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.ProfileAvatar
import uk.veritaslabs.vesta.ui.components.SecondaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.img_armchair
import vesta.shared.generated.resources.onboarding_create_home
import vesta.shared.generated.resources.onboarding_delete_home
import vesta.shared.generated.resources.onboarding_edit_home
import vesta.shared.generated.resources.onboarding_empty_subtitle
import vesta.shared.generated.resources.onboarding_empty_title
import vesta.shared.generated.resources.onboarding_get_started
import vesta.shared.generated.resources.onboarding_join_home
import vesta.shared.generated.resources.onboarding_title

private val HomePickerTabletBreakpoint = 600.dp

/** A single row of the home picker screen's home list — see [HomePickerScreen]. */
data class HomeListItem(
    val id: String,
    val name: String,
    val avatar: DrawableResource? = null,
)

/**
 * Carbon copy of [ProfilePickerScreen] for now, under its own name while
 * onboarding diverges from the shared-device profile picker: presents a
 * grid/list of [homes] to choose from (when non-empty) plus a "Create home"
 * action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePickerScreen(
    homes: List<HomeListItem>,
    modifier: Modifier = Modifier,
    onHomeSelected: (HomeListItem) -> Unit = {},
    onCreateHome: () -> Unit = {},
    onJoinHome: () -> Unit = {},
    onEditHome: (HomeListItem) -> Unit = {},
    onDeleteHome: (HomeListItem) -> Unit = {},
) {
    var homeForActions by remember { mutableStateOf<HomeListItem?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= HomePickerTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = contentPadding),
        ) {
            Spacer(Modifier.height(contentPadding))
            Text(
                text = stringResource(
                    if (homes.isEmpty()) Res.string.onboarding_empty_title else Res.string.onboarding_title,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (homes.isEmpty()) {
                Text(
                    text = stringResource(Res.string.onboarding_empty_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(contentPadding))

            if (homes.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(Res.drawable.img_armchair),
                        contentDescription = null,
                        modifier = Modifier.width(if (isTablet) 320.dp else 280.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 3 else 1),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(homes, key = { it.id }) { home ->
                        AvatarButton(
                            label = home.name,
                            avatar = { size -> ProfileAvatar(name = home.name, size = size, avatar = home.avatar) },
                            orientation = if (isTablet) AvatarButtonOrientation.Vertical else AvatarButtonOrientation.Horizontal,
                            modifier = if (isTablet) Modifier else Modifier.fillMaxWidth(),
                            onClick = { onHomeSelected(home) },
                            onLongClick = { homeForActions = home },
                        )
                    }
                }
            }

            SecondaryButton(
                label = stringResource(Res.string.onboarding_join_home),
                onClick = onJoinHome,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(contentPadding))
            PrimaryButton(
                label = stringResource(
                    Res.string.onboarding_create_home,
                ),
                onClick = onCreateHome,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(contentPadding))
        }
    }

    homeForActions?.let { actionsTarget ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { homeForActions = null },
            sheetState = sheetState,
        ) {
            HomePickerActionsSheetContent(
                home = actionsTarget,
                onEdit = {
                    homeForActions = null
                    onEditHome(actionsTarget)
                },
                onDelete = {
                    homeForActions = null
                    onDeleteHome(actionsTarget)
                },
            )
        }
    }
}

@Composable
private fun HomePickerActionsSheetContent(
    home: HomeListItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = home.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.onboarding_edit_home))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.onboarding_delete_home), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
    }
}

private val HomePickerPreviewHomes = listOf(
    HomeListItem(id = "the-johnsons", name = "The Johnsons"),
    HomeListItem(id = "flat-4b", name = "Flat 4B"),
    HomeListItem(id = "mums-place", name = "Mum's Place"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomePickerScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomePickerScreen(homes = HomePickerPreviewHomes) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomePickerScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomePickerScreen(homes = HomePickerPreviewHomes) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomePickerScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomePickerScreen(homes = HomePickerPreviewHomes) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomePickerScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomePickerScreen(homes = HomePickerPreviewHomes) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomePickerScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HomePickerScreen(homes = emptyList()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HomePickerScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HomePickerScreen(homes = emptyList()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomePickerScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { HomePickerScreen(homes = emptyList()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HomePickerScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HomePickerScreen(homes = emptyList()) }
}
