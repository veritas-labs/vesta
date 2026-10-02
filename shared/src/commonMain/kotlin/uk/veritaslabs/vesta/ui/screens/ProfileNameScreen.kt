package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.profile.ProfileNameRules
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.profile_name_body_rename
import vesta.shared.generated.resources.profile_name_body_setup
import vesta.shared.generated.resources.profile_name_counter
import vesta.shared.generated.resources.profile_name_error
import vesta.shared.generated.resources.profile_name_field_label
import vesta.shared.generated.resources.profile_name_save
import vesta.shared.generated.resources.profile_name_setup_continue
import vesta.shared.generated.resources.profile_name_title_rename
import vesta.shared.generated.resources.profile_name_title_setup
import vesta.shared.generated.resources.settings_back

private val ProfileNameTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object ProfileNameTestTags {
    const val NameField = "profile_name_field"
    const val Counter = "profile_name_counter"
    const val Error = "profile_name_error"
    const val SaveButton = "profile_name_save_button"
    const val BackButton = "profile_name_back_button"
}

/** Whether the screen is first asking for a name, or changing the one already chosen. */
enum class ProfileNameMode { Setup, Rename }

/**
 * Asks for the user's display name (docs/adr/ADR-016-profile-setup.md): how other people see them in a Home. It must be
 * 2 to 40 characters; [onSave] is only called with a name that is, and a bad one shows why once the user tries to save it.
 * [onBack] leaves without saving.
 */
@Composable
fun ProfileNameScreen(
    initialName: String,
    mode: ProfileNameMode,
    modifier: Modifier = Modifier,
    onSave: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var name by remember { mutableStateOf(initialName) }
    var triedToSave by remember { mutableStateOf(false) }
    val invalid = !ProfileNameRules.isValid(name)

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= ProfileNameTabletBreakpoint) 24.dp else 16.dp

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().testTag(ProfileNameTestTags.BackButton),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_left),
                contentDescription = stringResource(Res.string.settings_back),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(contentPadding).padding(top = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(
                    if (mode == ProfileNameMode.Setup) Res.string.profile_name_title_setup else Res.string.profile_name_title_rename,
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    if (mode == ProfileNameMode.Setup) Res.string.profile_name_body_setup else Res.string.profile_name_body_rename,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(ProfileNameRules.MaxLength) },
                label = { Text(stringResource(Res.string.profile_name_field_label)) },
                singleLine = true,
                isError = triedToSave && invalid,
                modifier = Modifier.fillMaxWidth().testTag(ProfileNameTestTags.NameField),
            )
            Text(
                text = stringResource(Res.string.profile_name_counter, name.length, ProfileNameRules.MaxLength),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).testTag(ProfileNameTestTags.Counter),
            )
            if (triedToSave && invalid) {
                Text(
                    text = stringResource(Res.string.profile_name_error, ProfileNameRules.MinLength, ProfileNameRules.MaxLength),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp).testTag(ProfileNameTestTags.Error),
                )
            }
            PrimaryButton(
                label = stringResource(
                    if (mode == ProfileNameMode.Setup) Res.string.profile_name_setup_continue else Res.string.profile_name_save,
                ),
                onClick = {
                    triedToSave = true
                    if (!invalid) onSave(name.trim())
                },
                modifier = Modifier.padding(top = 16.dp).testTag(ProfileNameTestTags.SaveButton),
            )
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ProfileNameScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ProfileNameScreen("Mark's laptop", ProfileNameMode.Setup) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ProfileNameScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ProfileNameScreen("Mark", ProfileNameMode.Rename) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ProfileNameScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { ProfileNameScreen("Mark", ProfileNameMode.Rename) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ProfileNameScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ProfileNameScreen("Mark's laptop", ProfileNameMode.Setup) }
}
