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
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.img_armchair
import vesta.shared.generated.resources.profile_picker_create_profile
import vesta.shared.generated.resources.profile_picker_delete_profile
import vesta.shared.generated.resources.profile_picker_edit_profile
import vesta.shared.generated.resources.profile_picker_empty_subtitle
import vesta.shared.generated.resources.profile_picker_empty_title
import vesta.shared.generated.resources.profile_picker_get_started
import vesta.shared.generated.resources.profile_picker_title

/** A single row of the Profile Picker's list — see [ProfilePickerScreen]. */
data class ProfileListItem(
    val id: String,
    val name: String,
    val avatar: DrawableResource? = null,
)

private val TabletBreakpoint = 600.dp

/**
 * The shared-device Profile Picker (`S1b` in docs/design/profile-switching.md):
 * a grid/list of [profiles] plus a "Create profile" action. Long-pressing a
 * profile (touch devices) opens a bottom sheet with "Edit profile" and
 * "Delete profile" actions instead of selecting it.
 *
 * UI only — the caller owns [profiles] and reacts to the callbacks; no
 * view-model or persistence lives here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerScreen(
    profiles: List<ProfileListItem>,
    modifier: Modifier = Modifier,
    onProfileSelected: (ProfileListItem) -> Unit = {},
    onCreateProfile: () -> Unit = {},
    onEditProfile: (ProfileListItem) -> Unit = {},
    onDeleteProfile: (ProfileListItem) -> Unit = {},
) {
    var profileForActions by remember { mutableStateOf<ProfileListItem?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= TabletBreakpoint
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
                    if (profiles.isEmpty()) Res.string.profile_picker_empty_title else Res.string.profile_picker_title,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(Res.string.profile_picker_empty_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(contentPadding))

            if (profiles.isEmpty()) {
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
                    items(profiles, key = { it.id }) { profile ->
                        AvatarButton(
                            label = profile.name,
                            avatar = { size -> ProfileAvatar(name = profile.name, size = size, avatar = profile.avatar) },
                            orientation = if (isTablet) AvatarButtonOrientation.Vertical else AvatarButtonOrientation.Horizontal,
                            modifier = if (isTablet) Modifier else Modifier.fillMaxWidth(),
                            onClick = { onProfileSelected(profile) },
                            onLongClick = { profileForActions = profile },
                        )
                    }
                }
            }

            PrimaryButton(
                label = stringResource(
                    if (profiles.isEmpty()) Res.string.profile_picker_get_started else Res.string.profile_picker_create_profile,
                ),
                onClick = onCreateProfile,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(contentPadding))
        }
    }

    profileForActions?.let { actionsTarget ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { profileForActions = null },
            sheetState = sheetState,
        ) {
            ProfileActionsSheetContent(
                profile = actionsTarget,
                onEdit = {
                    profileForActions = null
                    onEditProfile(actionsTarget)
                },
                onDelete = {
                    profileForActions = null
                    onDeleteProfile(actionsTarget)
                },
            )
        }
    }
}

@Composable
private fun ProfileActionsSheetContent(
    profile: ProfileListItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.profile_picker_edit_profile))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.profile_picker_delete_profile), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
    }
}

private val PreviewProfiles = listOf(
    ProfileListItem(id = "tom", name = "Tom"),
    ProfileListItem(id = "arthur", name = "Arthur"),
    ProfileListItem(id = "mei", name = "Mei"),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ProfilePickerScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ProfilePickerScreen(profiles = PreviewProfiles) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ProfilePickerScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ProfilePickerScreen(profiles = PreviewProfiles) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ProfilePickerScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { ProfilePickerScreen(profiles = PreviewProfiles) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ProfilePickerScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ProfilePickerScreen(profiles = PreviewProfiles) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ProfilePickerScreenEmptyPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ProfilePickerScreen(profiles = emptyList()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ProfilePickerScreenEmptyPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ProfilePickerScreen(profiles = emptyList()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ProfilePickerScreenEmptyTabletLightPreview() {
    VestaTheme(darkTheme = false) { ProfilePickerScreen(profiles = emptyList()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ProfilePickerScreenEmptyTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ProfilePickerScreen(profiles = emptyList()) }
}
