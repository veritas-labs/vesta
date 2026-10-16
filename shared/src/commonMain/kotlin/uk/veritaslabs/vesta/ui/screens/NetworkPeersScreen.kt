package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.kits.x0x.X0xPeer
import uk.veritaslabs.kits.x0x.X0xPeerStatus
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.network_peers_count
import vesta.shared.generated.resources.network_peers_empty
import vesta.shared.generated.resources.network_peers_legend_active
import vesta.shared.generated.resources.network_peers_ago_hours
import vesta.shared.generated.resources.network_peers_ago_minutes
import vesta.shared.generated.resources.network_peers_ago_seconds
import vesta.shared.generated.resources.network_peers_ago_now
import vesta.shared.generated.resources.network_peers_info_never
import vesta.shared.generated.resources.network_peers_info_agent_unknown
import vesta.shared.generated.resources.network_peers_info_sent
import vesta.shared.generated.resources.network_peers_info_received
import vesta.shared.generated.resources.network_peers_info_rtt_value
import vesta.shared.generated.resources.network_peers_info_rtt_none
import vesta.shared.generated.resources.network_peers_info_rtt_measuring
import vesta.shared.generated.resources.network_peers_info_rtt
import vesta.shared.generated.resources.network_peers_info_relayed
import vesta.shared.generated.resources.network_peers_info_direct
import vesta.shared.generated.resources.network_peers_info_connection
import vesta.shared.generated.resources.network_peers_info_status
import vesta.shared.generated.resources.network_peers_info_machine
import vesta.shared.generated.resources.network_peers_info_agent
import vesta.shared.generated.resources.network_peers_legend_direct
import vesta.shared.generated.resources.network_peers_legend_idle
import vesta.shared.generated.resources.network_peers_legend_unknown
import vesta.shared.generated.resources.network_peers_loading
import vesta.shared.generated.resources.network_peers_title
import vesta.shared.generated.resources.network_peers_you

private val NetworkPeersTabletBreakpoint = 600.dp
/** Room kept between the outermost peers and the graph's edge. */
private val GraphInset = 12.dp
private const val GoldenAngle = 2.399963229728653

/** Stable hooks for UI tests to target. */
internal object NetworkPeersTestTags {
    const val BackButton = "network_peers_back_button"
    const val Graph = "network_peers_graph"
    const val Loading = "network_peers_loading"
    const val Empty = "network_peers_empty"
    const val Selected = "network_peers_selected"
}

/**
 * The peers this device's x0x daemon is connected to, drawn as a graph: this device, "You", in the middle and each peer around it
 * on a line to the centre. A peer's marker says how its connection is doing: filled for [X0xPeerStatus.Active], a ring for
 * [X0xPeerStatus.Idle], a faint ring for [X0xPeerStatus.Unknown], and a green halo when it is also a direct-message connection.
 * Hovering a peer (or tapping it, to keep it) opens a card with what is known of it, including a round trip time measured with [probe]
 * (a peer id to milliseconds, null when it doesn't answer); the card never shows an address. [peers] is null until the first answer. The back arrow calls [onBack]. UI only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPeersScreen(
    peers: List<X0xPeer>?,
    modifier: Modifier = Modifier,
    probe: suspend (peerId: String) -> Long? = { null },
    onBack: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= NetworkPeersTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.network_peers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(NetworkPeersTestTags.BackButton)) {
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
            when {
                peers == null -> Message(stringResource(Res.string.network_peers_loading), NetworkPeersTestTags.Loading, contentPadding)
                peers.isEmpty() -> Message(stringResource(Res.string.network_peers_empty), NetworkPeersTestTags.Empty, contentPadding)
                else -> Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding).padding(bottom = contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PeerLegend()
                    PeerGraph(peers, probe, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, tag: String, padding: Dp) {
    Box(Modifier.fillMaxSize().padding(horizontal = padding).testTag(tag), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Where each peer sits, as a fraction of the graph's radius from its centre: a sunflower spiral, so any number of peers spread evenly. */
internal fun peerPositions(count: Int): List<Offset> = List(count) { i ->
    val angle = i * GoldenAngle
    val radius = 0.3f + 0.65f * sqrt((i + 0.5f) / count)
    Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
}

/** The index of the peer whose marker is within [within] of [at], the nearest one; null when none is. */
private fun nearestPeer(at: Offset, size: IntSize, positions: List<Offset>, inset: Float, within: Float): Int? {
    val middle = Offset(size.width / 2f, size.height / 2f)
    val reach = min(size.width, size.height) / 2f - inset
    return positions.withIndex()
        .minByOrNull { (_, p) -> hypot(middle.x + p.x * reach - at.x, middle.y + p.y * reach - at.y) }
        ?.takeIf { (_, p) -> hypot(middle.x + p.x * reach - at.x, middle.y + p.y * reach - at.y) < within }
        ?.index
}

@Composable
private fun PeerGraph(peers: List<X0xPeer>, probe: suspend (peerId: String) -> Long?, modifier: Modifier = Modifier) {
    // Sorted so a peer keeps its place from one poll to the next.
    val sorted = remember(peers) { peers.sortedBy { it.id } }
    val positions = remember(sorted.size) { peerPositions(sorted.size) }
    // Hovering a peer (mouse) shows it; tapping pins it, which is how touch screens get there. Both are by id, so a poll doesn't lose them.
    var hoveredId by remember { mutableStateOf<String?>(null) }
    var pinnedId by remember { mutableStateOf<String?>(null) }
    val shownId = hoveredId ?: pinnedId
    val shownIndex = sorted.indexOfFirst { it.id == shownId }.takeIf { it >= 0 }
    val shown = shownIndex?.let { sorted[it] }
    val line = MaterialTheme.colorScheme.outline
    val node = MaterialTheme.colorScheme.primary
    val centre = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.background

    var measuring by remember { mutableStateOf(false) }
    var roundTripMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(shownId) {
        roundTripMs = null
        val id = shownId ?: return@LaunchedEffect
        measuring = true
        // Sweeping the mouse across the graph passes many peers; only the one it rests on is probed.
        delay(300)
        roundTripMs = probe(id)
        measuring = false
    }

    // The graph is a square as large as the space allows, so it never needs scrolling.
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight).coerceAtMost(640.dp)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .testTag(NetworkPeersTestTags.Graph)
                    .pointerInput(sorted) {
                        detectTapGestures { tap -> pinnedId = nearestPeer(tap, size, positions, GraphInset.toPx(), 28.dp.toPx())?.let { sorted[it].id } }
                    }
                    .pointerInput(sorted) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.type != PointerType.Mouse) continue
                                when (event.type) {
                                    PointerEventType.Move, PointerEventType.Enter ->
                                        hoveredId = nearestPeer(change.position, size, positions, GraphInset.toPx(), 20.dp.toPx())?.let { sorted[it].id }
                                    PointerEventType.Exit -> hoveredId = null
                                }
                            }
                        }
                    },
            ) {
                val middle = Offset(size.width / 2f, size.height / 2f)
                val reach = min(size.width, size.height) / 2f - GraphInset.toPx()
                sorted.forEachIndexed { i, peer ->
                    val at = Offset(middle.x + positions[i].x * reach, middle.y + positions[i].y * reach)
                    val faint = peer.status == X0xPeerStatus.Unknown
                    // A gentle curve, bowed the same way for every peer, like the lines of the Autonomi network graph.
                    val bow = Offset(-(at.y - middle.y), at.x - middle.x) * 0.12f
                    drawPath(
                        Path().apply {
                            moveTo(middle.x, middle.y)
                            quadraticTo((middle.x + at.x) / 2f + bow.x, (middle.y + at.y) / 2f + bow.y, at.x, at.y)
                        },
                        color = line.copy(alpha = if (faint) 0.25f else 0.6f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                sorted.forEachIndexed { i, peer ->
                    val at = Offset(middle.x + positions[i].x * reach, middle.y + positions[i].y * reach)
                    if (peer.direct) drawCircle(ConnectedGreen.copy(alpha = 0.35f), radius = 11.dp.toPx(), center = at)
                    when (peer.status) {
                        X0xPeerStatus.Active -> drawCircle(node, radius = 6.dp.toPx(), center = at)
                        X0xPeerStatus.Idle -> {
                            drawCircle(surface, radius = 6.dp.toPx(), center = at)
                            drawCircle(node, radius = 6.dp.toPx(), center = at, style = Stroke(width = 2.dp.toPx()))
                        }
                        X0xPeerStatus.Unknown ->
                            drawCircle(line.copy(alpha = 0.6f), radius = 4.dp.toPx(), center = at, style = Stroke(width = 1.dp.toPx()))
                    }
                    if (peer.id == shownId) drawCircle(node, radius = 14.dp.toPx(), center = at, style = Stroke(width = 1.5.dp.toPx()))
                }
                drawCircle(centre, radius = 38.dp.toPx(), center = middle)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(Res.string.network_peers_you),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    stringResource(Res.string.network_peers_count, sorted.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            if (shown != null && shownIndex != null) {
                // In the half of the graph away from the peer, so the card never covers what it describes.
                PeerInfoCard(
                    peer = shown,
                    measuring = measuring,
                    roundTripMs = roundTripMs,
                    modifier = Modifier
                        .align(if (positions[shownIndex].y >= 0f) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(8.dp)
                        .testTag(NetworkPeersTestTags.Selected),
                )
            }
        }
    }
}

@Composable
private fun PeerInfoCard(peer: X0xPeer, measuring: Boolean, roundTripMs: Long?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 300.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoLine(
                stringResource(Res.string.network_peers_info_agent),
                peer.agentId?.let(::shortId) ?: stringResource(Res.string.network_peers_info_agent_unknown),
                monospace = peer.agentId != null,
            )
            InfoLine(stringResource(Res.string.network_peers_info_machine), shortId(peer.id), monospace = true)
            InfoLine(stringResource(Res.string.network_peers_info_status), stringResource(statusLabel(peer.status)))
            InfoLine(
                stringResource(Res.string.network_peers_info_connection),
                stringResource(if (peer.direct) Res.string.network_peers_info_direct else Res.string.network_peers_info_relayed),
            )
            InfoLine(
                stringResource(Res.string.network_peers_info_rtt),
                when {
                    roundTripMs != null -> stringResource(Res.string.network_peers_info_rtt_value, roundTripMs)
                    measuring -> stringResource(Res.string.network_peers_info_rtt_measuring)
                    else -> stringResource(Res.string.network_peers_info_rtt_none)
                },
            )
            InfoLine(stringResource(Res.string.network_peers_info_received), agoText(peer.lastReceivedMsAgo))
            InfoLine(stringResource(Res.string.network_peers_info_sent), agoText(peer.lastSentMsAgo))
        }
    }
}

@Composable
internal fun InfoLine(label: String, value: String, monospace: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.let { if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A long id as its first and last eight characters. */
internal fun shortId(id: String): String = if (id.length <= 20) id else id.take(8) + "…" + id.takeLast(8)

@Composable
internal fun agoText(ms: Long?): String = when {
    ms == null -> stringResource(Res.string.network_peers_info_never)
    ms < 1_000 -> stringResource(Res.string.network_peers_ago_now)
    ms < 60_000 -> stringResource(Res.string.network_peers_ago_seconds, (ms / 1_000).toInt())
    ms < 3_600_000 -> stringResource(Res.string.network_peers_ago_minutes, (ms / 60_000).toInt())
    else -> stringResource(Res.string.network_peers_ago_hours, (ms / 3_600_000).toInt())
}

private fun statusLabel(status: X0xPeerStatus) = when (status) {
    X0xPeerStatus.Active -> Res.string.network_peers_legend_active
    X0xPeerStatus.Idle -> Res.string.network_peers_legend_idle
    X0xPeerStatus.Unknown -> Res.string.network_peers_legend_unknown
}

@Composable
private fun PeerLegend() {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(stringResource(Res.string.network_peers_legend_active)) {
            Box(Modifier.size(12.dp).background(primary, CircleShape))
        }
        LegendItem(stringResource(Res.string.network_peers_legend_idle)) {
            Box(Modifier.size(12.dp).border(2.dp, primary, CircleShape))
        }
        LegendItem(stringResource(Res.string.network_peers_legend_unknown)) {
            Box(Modifier.size(12.dp).border(1.dp, outline, CircleShape))
        }
        LegendItem(stringResource(Res.string.network_peers_legend_direct)) {
            Box(Modifier.size(12.dp).background(ConnectedGreen.copy(alpha = 0.5f), CircleShape))
        }
    }
}

@Composable
private fun LegendItem(label: String, marker: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        marker()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun previewPeers() = List(40) { i ->
    X0xPeer(
        id = (i * 7919L).toString(16).padStart(64, '0'),
        status = when (i % 5) {
            0 -> X0xPeerStatus.Idle
            4 -> X0xPeerStatus.Unknown
            else -> X0xPeerStatus.Active
        },
        direct = i % 9 == 0,
    )
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun NetworkPeersScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { NetworkPeersScreen(previewPeers()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun NetworkPeersScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { NetworkPeersScreen(previewPeers()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun NetworkPeersScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { NetworkPeersScreen(previewPeers()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun NetworkPeersScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { NetworkPeersScreen(previewPeers()) }
}
