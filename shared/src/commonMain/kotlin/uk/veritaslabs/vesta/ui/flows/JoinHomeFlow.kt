package uk.veritaslabs.vesta.ui.flows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.homejoin.HomeJoining
import uk.veritaslabs.vesta.homejoin.JoinLink
import uk.veritaslabs.vesta.homejoin.JoinedHome
import uk.veritaslabs.vesta.homejoin.RedeemInviteViewModel
import uk.veritaslabs.vesta.ui.screens.DevicePairingGeneratedScreen
import uk.veritaslabs.vesta.ui.screens.RedeemInviteScreen
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.join_home_body
import vesta.shared.generated.resources.join_home_have_invite_link
import vesta.shared.generated.resources.join_home_title

/** Stable hooks for UI tests to target, independent of displayed copy. */
object JoinHomeFlowTestTags {
    const val HaveInviteLinkButton = "join_home_have_invite_link_button"
}

/**
 * Join a Home (docs/adr/ADR-015-joining-a-home.md): shows this person's join link for an admin of the Home to paste,
 * and calls [onJoined] once the invitation has arrived and the Home is on this device. [onExit] abandons it (the link
 * stops working). A button under the link switches to pasting an invite link an admin sent instead
 * (docs/adr/ADR-018-invite-links.md); once that request is on its way the flow exits, and the Home turns up in the
 * picker when an admin approves it. If x0x can't say what this device's agent is, there is no link to show, so it exits.
 */
@Composable
fun JoinHomeFlow(joining: HomeJoining, onExit: () -> Unit, onJoined: (JoinedHome) -> Unit, modifier: Modifier = Modifier) {
    var link by remember { mutableStateOf<JoinLink?>(null) }
    var enteringInvite by remember { mutableStateOf(false) }
    // Each visit to the invite link screen gets a fresh view model, not a finished one left in the store.
    var redeemSession by remember { mutableIntStateOf(0) }
    val exit by rememberUpdatedState(onExit)
    val joined by rememberUpdatedState(onJoined)
    LaunchedEffect(joining) {
        // Listen before the link is on show, so an invitation can't arrive unnoticed.
        launch(start = CoroutineStart.UNDISPATCHED) { joined(joining.joined.first()) }
        link = runCatching { joining.openLink() }.getOrNull() ?: run { exit(); null }
    }
    DisposableEffect(joining) { onDispose { joining.cancelLink() } }

    if (enteringInvite) {
        val redeemViewModel = viewModel(key = "redeem-invite-$redeemSession") { RedeemInviteViewModel(joining) }
        RedeemInviteScreen(redeemViewModel, onBack = { enteringInvite = false }, onStarted = onExit, modifier = modifier)
        return
    }
    link?.let {
        Box(modifier) {
            DevicePairingGeneratedScreen(
                pairingLink = it.encode(),
                title = stringResource(Res.string.join_home_title),
                body = stringResource(Res.string.join_home_body),
                onCancel = onExit,
            )
            TextButton(
                onClick = {
                    redeemSession++
                    enteringInvite = true
                },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().testTag(JoinHomeFlowTestTags.HaveInviteLinkButton),
            ) { Text(stringResource(Res.string.join_home_have_invite_link)) }
        }
    } ?: Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
