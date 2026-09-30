package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.agent_approval_allow
import vesta.shared.generated.resources.agent_approval_body
import vesta.shared.generated.resources.agent_approval_deny
import vesta.shared.generated.resources.agent_approval_title

private val AgentApprovalCardMaxWidth = 480.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
object AgentApprovalCardTestTags {
    const val Card = "agent_approval_card"
    const val Allow = "agent_approval_allow"
    const val Deny = "agent_approval_deny"
}

/**
 * An x0x agent id shortened for display — `63ab3172…d930` — since the full 64-character id doesn't fit and
 * only needs to be recognisable. Ids too short to shorten are returned as they are.
 */
fun abbreviateAgentId(agentId: String): String =
    if (agentId.length <= 16) agentId else "${agentId.take(8)}…${agentId.takeLast(4)}"

/**
 * Asks the user whether the x0x agent [agentId] — already trusted on the x0x network — may manage their home:
 * [onAllow] approves it, [onDeny] dismisses the request. Pulls colors/shapes from [VestaTheme]; meant to sit
 * over the current screen (see `AgentApprovalHost`).
 */
@Composable
fun AgentApprovalCard(
    agentId: String,
    modifier: Modifier = Modifier,
    onAllow: () -> Unit = {},
    onDeny: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = AgentApprovalCardMaxWidth).testTag(AgentApprovalCardTestTags.Card),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.agent_approval_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.agent_approval_body, abbreviateAgentId(agentId)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag(AgentApprovalCardTestTags.Deny),
                ) { Text(stringResource(Res.string.agent_approval_deny)) }
                Button(
                    onClick = onAllow,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag(AgentApprovalCardTestTags.Allow),
                ) { Text(stringResource(Res.string.agent_approval_allow)) }
            }
        }
    }
}

private const val PreviewAgentId = "63ab3172ff7b4afa6717974c9f05946481a0e9b90e9533da8d332933e781d930"

@Composable
private fun AgentApprovalCardPhoneContent() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AgentApprovalCard(agentId = PreviewAgentId)
    }
}

@Composable
private fun AgentApprovalCardTabletContent() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AgentApprovalCard(agentId = PreviewAgentId)
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun AgentApprovalCardPhoneLightPreview() {
    VestaTheme(darkTheme = false) { AgentApprovalCardPhoneContent() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun AgentApprovalCardPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { AgentApprovalCardPhoneContent() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AgentApprovalCardTabletLightPreview() {
    VestaTheme(darkTheme = false) { AgentApprovalCardTabletContent() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun AgentApprovalCardTabletDarkPreview() {
    VestaTheme(darkTheme = true) { AgentApprovalCardTabletContent() }
}
