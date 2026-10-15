package uk.veritaslabs.vesta.x0x

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.x0x_connectivity_connected
import vesta.shared.generated.resources.x0x_connectivity_connecting
import vesta.shared.generated.resources.x0x_connectivity_offline
import vesta.shared.generated.resources.x0x_label

private val connectedGreen = Color(0xFF4CAF50)

/** A small status dot, coloured by [connectivity], followed by an "x0x" label. */
@Composable
fun X0xConnectivityIndicator(connectivity: X0xConnectivity, modifier: Modifier = Modifier) {
    val color = when (connectivity) {
        is X0xConnectivity.Connected -> connectedGreen
        X0xConnectivity.Connecting -> MaterialTheme.colorScheme.onSurfaceVariant
        X0xConnectivity.Offline -> MaterialTheme.colorScheme.error
    }
    val description = stringResource(
        when (connectivity) {
            is X0xConnectivity.Connected -> Res.string.x0x_connectivity_connected
            X0xConnectivity.Connecting -> Res.string.x0x_connectivity_connecting
            X0xConnectivity.Offline -> Res.string.x0x_connectivity_offline
        },
    )
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(
            text = stringResource(Res.string.x0x_label),
            style = MaterialTheme.typography.labelMedium.copy(
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
