package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.HomeNavigationBar
import uk.veritaslabs.vesta.ui.components.HomeTab
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_user_plus
import vesta.shared.generated.resources.members_invite
import vesta.shared.generated.resources.members_title

private val MembersTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object MembersTestTags {
    const val InviteButton = "members_invite_button"
}

/**
 * The Members tab of a Home: an Invite button ([onInviteClick]) for bringing someone into the Home. The
 * bottom nav bar has Home ([onHomeClick]) and Members (selected).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    modifier: Modifier = Modifier,
    onInviteClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= MembersTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.members_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding, vertical = 4.dp)) {
                Button(onClick = onInviteClick, modifier = Modifier.testTag(MembersTestTags.InviteButton)) {
                    Row {
                        Icon(
                            painter = painterResource(Res.drawable.ic_user_plus),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.members_invite))
                    }
                }
            }
            HomeNavigationBar(selected = HomeTab.Members, onHomeClick = onHomeClick)
        }
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun MembersScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { MembersScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun MembersScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { MembersScreen() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MembersScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { MembersScreen() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun MembersScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { MembersScreen() }
}
