package uk.veritaslabs.vesta.ui.screens

import uk.veritaslabs.vesta.syncactivity.VestaSyncModules
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.calendar.localMoment
import uk.veritaslabs.kits.sync.PeerActivity
import uk.veritaslabs.kits.sync.ProblemReason
import uk.veritaslabs.vesta.syncactivity.SyncActivityUiState
import uk.veritaslabs.vesta.syncactivity.SyncActivityViewModel
import uk.veritaslabs.kits.sync.SyncEventFilter
import uk.veritaslabs.kits.sync.SyncEventItem
import uk.veritaslabs.kits.sync.SyncEventKind
import uk.veritaslabs.kits.sync.SyncModule
import uk.veritaslabs.kits.sync.SyncVerdict
import uk.veritaslabs.vesta.ui.components.ContentMaxWidth
import uk.veritaslabs.vesta.ui.components.FabClearance
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_refresh
import vesta.shared.generated.resources.sync_activity_clear_device
import vesta.shared.generated.resources.sync_activity_devices_title
import vesta.shared.generated.resources.sync_activity_recent_title
import vesta.shared.generated.resources.sync_activity_retention
import vesta.shared.generated.resources.sync_activity_show_older
import vesta.shared.generated.resources.sync_activity_showing_device
import vesta.shared.generated.resources.sync_activity_sync_now
import vesta.shared.generated.resources.sync_activity_syncing_description
import vesta.shared.generated.resources.sync_activity_title

private val SyncActivityTabletBreakpoint = 600.dp

/** The widest the two-column layout gets: the status column plus the log column. */
private val SyncActivityTwoColumnMaxWidth = ContentMaxWidth + 320.dp

private const val MillisPerHour = 3_600_000L
private const val MillisPerMinute = 60_000L

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object SyncActivityTestTags {
    const val BackButton = "sync_activity_back_button"
    const val SyncNow = "sync_activity_sync_now"
    const val SyncNowProgress = "sync_activity_sync_now_progress"
    const val Loading = "sync_activity_loading"
    const val StatusCard = "sync_activity_status_card"
    const val VerdictTitle = "sync_activity_verdict_title"
    const val VerdictBody = "sync_activity_verdict_body"
    const val DevicesTitle = "sync_activity_devices_title"
    const val DevicePresence = "sync_activity_device_presence"
    const val InviteRow = "sync_activity_invite_row"
    const val FilterButton = "sync_activity_filter_button"
    const val DayHeader = "sync_activity_day_header"
    const val ShowOlder = "sync_activity_show_older"
    const val Retention = "sync_activity_retention"
    const val EmptyLog = "sync_activity_empty_log"
    const val EmptyReason = "sync_activity_empty_reason"
    const val DeviceDetail = "sync_activity_device_detail"
    const val DeviceRetrying = "sync_activity_device_retrying"
    const val ShowDeviceActivity = "sync_activity_show_device_activity"
    const val ClearDevice = "sync_activity_clear_device"

    fun deviceRow(agentId: String) = "sync_activity_device_$agentId"
    fun eventRow(id: Long) = "sync_activity_event_$id"
    fun filterOption(filter: SyncEventFilter) = "sync_activity_filter_${filter.name.lowercase()}"
}

/** The [SyncActivityViewModel]-backed screen for the Home called [homeName]. */
@Composable
fun SyncActivityScreen(
    viewModel: SyncActivityViewModel,
    homeName: String,
    onBack: () -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SyncActivityScreen(
        state = state,
        homeName = homeName,
        modifier = modifier,
        onBack = onBack,
        onSyncNow = viewModel::syncNow,
        onFilter = viewModel::setFilter,
        onSelectDevice = viewModel::selectDevice,
        onLoadOlder = viewModel::loadOlder,
        onInvite = onInvite,
    )
}

/**
 * What has been syncing for one Home (docs/design/sync-activity.md): the status card for [SyncActivityUiState.verdict], the Devices
 * (or an Invite row when the Home is only on this device) and the Recent activity log under day headers. The log is [SyncActivityUiState.events]
 * as given; narrowing it is the view model's job, so [onFilter] and [onSelectDevice] only report the choice. Sync now ([onSyncNow]) is off
 * while syncing or when [SyncActivityUiState.syncAvailable] is false or the daemon is down. [onLoadOlder] is the "Show older" button, [onInvite]
 * the Invite row and [onBack] leaves. A phone opens a device's details in a bottom sheet; a tablet shows them inline and sets two columns.
 * [nowMillis] is "now" for the Today / Yesterday headers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncActivityScreen(
    state: SyncActivityUiState,
    homeName: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onFilter: (SyncEventFilter) -> Unit = {},
    onSelectDevice: (String) -> Unit = {},
    onLoadOlder: () -> Unit = {},
    onInvite: () -> Unit = {},
    nowMillis: Long = remember { Clock.System.now().toEpochMilliseconds() },
) {
    var sheetDevice by remember { mutableStateOf<String?>(null) }
    val verdict = state.verdict
    val busy = state.syncing || verdict == SyncVerdict.Syncing
    val daemonDown = verdict is SyncVerdict.Problem && verdict.reason == ProblemReason.DaemonUnreachable
    val canSyncNow = state.syncAvailable && !busy && !daemonDown
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val expanded = maxWidth >= SyncActivityTabletBreakpoint
        val contentPadding = if (expanded) 24.dp else 16.dp
        val today = remember(nowMillis) { localMoment(nowMillis).date }
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical),
            ),
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(Res.string.sync_activity_title), style = MaterialTheme.typography.titleMedium)
                        if (homeName.isNotBlank()) {
                            Text(homeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(SyncActivityTestTags.BackButton)) {
                        Icon(painter = painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onSyncNow, enabled = canSyncNow, modifier = Modifier.testTag(SyncActivityTestTags.SyncNow)) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).testTag(SyncActivityTestTags.SyncNowProgress),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(painter = painterResource(Res.drawable.ic_refresh), contentDescription = stringResource(Res.string.sync_activity_sync_now))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            if (state.isLoading) {
                // Blank until the first data arrives: no spinner, and no empty state flashing before the log is read.
                Box(Modifier.weight(1f).fillMaxWidth().testTag(SyncActivityTestTags.Loading))
            } else if (expanded) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Row(
                        modifier = Modifier.widthIn(max = SyncActivityTwoColumnMaxWidth).fillMaxSize().padding(horizontal = contentPadding),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        LazyColumn(modifier = Modifier.weight(0.35f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            statusItem(verdict)
                            deviceItems(state, selected = state.selectedDevice, onSelect = onSelectDevice, onInvite = onInvite)
                            state.peers.firstOrNull { it.agentId == state.selectedDevice }?.let { peer ->
                                item(key = "detail") { SyncDeviceDetail(peer, onShowActivity = null, modifier = Modifier.padding(0.dp)) }
                            }
                        }
                        LazyColumn(modifier = Modifier.weight(0.65f).fillMaxSize(), contentPadding = PaddingValues(bottom = FabClearance)) {
                            logItems(state, today, onFilter, onSelectDevice, onLoadOlder)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = FabClearance),
                ) {
                    statusItem(verdict)
                    deviceItems(state, selected = null, onSelect = { sheetDevice = it }, onInvite = onInvite)
                    logItems(state, today, onFilter, onSelectDevice, onLoadOlder)
                }
            }
        }
    }
    sheetDevice?.let { id ->
        state.peers.firstOrNull { it.agentId == id }?.let { peer ->
            ModalBottomSheet(onDismissRequest = { sheetDevice = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                SyncDeviceDetail(
                    peer = peer,
                    onShowActivity = {
                        sheetDevice = null
                        if (state.selectedDevice != id) onSelectDevice(id)
                    },
                )
            }
        } ?: run { sheetDevice = null }
    }
}

private fun LazyListScope.statusItem(verdict: SyncVerdict?) {
    verdict?.let { item(key = "status") { SyncStatusCard(it) } }
}

/** The Devices section (hubs after devices), or the Invite row when the Home is only on this device. */
private fun LazyListScope.deviceItems(state: SyncActivityUiState, selected: String?, onSelect: (String) -> Unit, onInvite: () -> Unit) {
    if (state.verdict == SyncVerdict.OnlyYou) {
        item(key = "invite") { SyncInviteRow(onInvite) }
        return
    }
    if (state.peers.isEmpty()) return
    item(key = "devices-title") {
        Text(
            text = stringResource(Res.string.sync_activity_devices_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() }.testTag(SyncActivityTestTags.DevicesTitle),
        )
    }
    val ordered = state.peers.filterNot { it.isHub } + state.peers.filter { it.isHub }
    ordered.forEach { peer ->
        item(key = "device-${peer.agentId}") {
            SyncDeviceRow(peer, selected = peer.agentId == selected, onClick = { onSelect(peer.agentId) })
        }
    }
}

/** Recent activity: header with the filter, an optional "showing this device" line, the entries under day headers, Show older and the retention note. */
private fun LazyListScope.logItems(
    state: SyncActivityUiState,
    today: CalendarDate,
    onFilter: (SyncEventFilter) -> Unit,
    onSelectDevice: (String) -> Unit,
    onLoadOlder: () -> Unit,
) {
    item(key = "recent-header") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.sync_activity_recent_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            SyncFilterMenu(state.filter, onFilter)
        }
    }
    state.selectedDevice?.let { id ->
        val name = state.peers.firstOrNull { it.agentId == id }?.name
        name?.let {
            item(key = "selected-device") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.sync_activity_showing_device, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onSelectDevice(id) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(SyncActivityTestTags.ClearDevice),
                    ) { Text(stringResource(Res.string.sync_activity_clear_device)) }
                }
            }
        }
    }
    if (state.events.isEmpty()) {
        item(key = "empty") { SyncEmptyLog(state.verdict, narrowed = state.filter != SyncEventFilter.All || state.selectedDevice != null) }
    } else {
        var lastDay: CalendarDate? = null
        state.events.forEach { event ->
            val day = localMoment(event.lastAt).date
            if (day != lastDay) {
                lastDay = day
                item(key = "day-${day.epochDay}") {
                    Text(
                        text = dayHeaderText(day, today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 12.dp).semantics { heading() }.testTag(SyncActivityTestTags.DayHeader),
                    )
                }
            }
            item(key = "event-${event.id}") { SyncActivityItem(event, state.peers) }
        }
        if (state.canLoadOlder) {
            item(key = "older") {
                TextButton(
                    onClick = onLoadOlder,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SyncActivityTestTags.ShowOlder),
                ) { Text(stringResource(Res.string.sync_activity_show_older)) }
            }
        }
        item(key = "retention") {
            Text(
                text = stringResource(Res.string.sync_activity_retention),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp).testTag(SyncActivityTestTags.Retention),
            )
        }
    }
}

private const val PreviewNow = 1_792_000_000_000L
private const val PreviewSam = "agent-sam"
private const val PreviewPriya = "agent-priya"
private const val PreviewTom = "agent-tom"
private const val PreviewHub = "agent-hub"

private fun previewPeers() = listOf(
    PeerActivity(PreviewSam, "Sam's Pixel 9", ownerName = "Sam", online = true, lastSyncedMsAgo = 2 * MillisPerMinute),
    PeerActivity(PreviewPriya, "Priya's iPad", ownerName = "Priya", online = true, lastSyncedMsAgo = 10_000L),
    PeerActivity(PreviewTom, "Tom's phone", ownerName = "Tom", online = false, lastSyncedMsAgo = 72 * MillisPerHour, retryingSinceMsAgo = 20 * MillisPerMinute),
    PeerActivity("agent-me", "Kitchen tablet", isYou = true, online = true, lastSyncedMsAgo = null),
    PeerActivity(PreviewHub, "Kitchen hub", isHub = true, online = true, lastSyncedMsAgo = MillisPerMinute),
)

private fun previewEvents() = listOf(
    SyncEventItem(1, PreviewNow - 5 * MillisPerMinute, PreviewNow - 5 * MillisPerMinute, SyncEventKind.Received, SyncModule(VestaSyncModules.Tasks), PreviewSam),
    SyncEventItem(2, PreviewNow - 9 * MillisPerMinute, PreviewNow - 9 * MillisPerMinute, SyncEventKind.Sent, SyncModule(VestaSyncModules.Shopping), PreviewPriya, count = 3),
    SyncEventItem(3, PreviewNow - 20 * MillisPerMinute, PreviewNow - 20 * MillisPerMinute, SyncEventKind.Failed, SyncModule(VestaSyncModules.Pantry), PreviewTom, detail = "unreachable"),
    SyncEventItem(4, PreviewNow - 26 * MillisPerHour, PreviewNow - 26 * MillisPerHour, SyncEventKind.Membership, SyncModule.Members, PreviewPriya),
)

private fun previewState(
    verdict: SyncVerdict,
    peers: List<PeerActivity> = previewPeers(),
    events: List<SyncEventItem> = previewEvents(),
    syncing: Boolean = false,
) = SyncActivityUiState(isLoading = false, verdict = verdict, peers = peers, events = events, canLoadOlder = events.isNotEmpty(), syncing = syncing)

private val PreviewUpToDate get() = previewState(SyncVerdict.UpToDate("Sam's Pixel 9", 2 * MillisPerMinute))
private val PreviewWaiting get() = previewState(SyncVerdict.WaitingForOthers, peers = previewPeers().map { it.copy(online = false) })
private val PreviewOnlyYou get() = previewState(SyncVerdict.OnlyYou, peers = emptyList(), events = emptyList())
private val PreviewChangesWaiting get() = previewState(SyncVerdict.ChangesWaiting, peers = previewPeers().map { it.copy(online = false) })
private val PreviewProblem get() = previewState(SyncVerdict.Problem(ProblemReason.DaemonUnreachable))
private val PreviewSyncing get() = previewState(SyncVerdict.Syncing, syncing = true)
private val PreviewEmptyLog get() = previewState(SyncVerdict.WaitingForOthers, events = emptyList())

@Composable
private fun SyncActivityPreview(dark: Boolean, state: SyncActivityUiState) {
    VestaTheme(darkTheme = dark) { SyncActivityScreen(state = state, homeName = "The Johnsons", nowMillis = PreviewNow) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneLightUpToDatePreview() = SyncActivityPreview(false, PreviewUpToDate)

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneDarkUpToDatePreview() = SyncActivityPreview(true, PreviewUpToDate)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneLightWaitingPreview() = SyncActivityPreview(false, PreviewWaiting)

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneDarkWaitingPreview() = SyncActivityPreview(true, PreviewWaiting)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneLightOnlyYouPreview() = SyncActivityPreview(false, PreviewOnlyYou)

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneDarkOnlyYouPreview() = SyncActivityPreview(true, PreviewOnlyYou)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneLightChangesWaitingPreview() = SyncActivityPreview(false, PreviewChangesWaiting)

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneDarkChangesWaitingPreview() = SyncActivityPreview(true, PreviewChangesWaiting)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneLightProblemPreview() = SyncActivityPreview(false, PreviewProblem)

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneDarkProblemPreview() = SyncActivityPreview(true, PreviewProblem)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneLightSyncingPreview() = SyncActivityPreview(false, PreviewSyncing)

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneDarkSyncingPreview() = SyncActivityPreview(true, PreviewSyncing)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneLightEmptyLogPreview() = SyncActivityPreview(false, PreviewEmptyLog)

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun SyncActivityPhoneDarkEmptyLogPreview() = SyncActivityPreview(true, PreviewEmptyLog)

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletLightUpToDatePreview() = SyncActivityPreview(false, PreviewUpToDate.copy(selectedDevice = PreviewTom))

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletDarkUpToDatePreview() = SyncActivityPreview(true, PreviewUpToDate)

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletLightWaitingPreview() = SyncActivityPreview(false, PreviewWaiting)

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletDarkWaitingPreview() = SyncActivityPreview(true, PreviewWaiting)

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletLightOnlyYouPreview() = SyncActivityPreview(false, PreviewOnlyYou)

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletDarkOnlyYouPreview() = SyncActivityPreview(true, PreviewOnlyYou)

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletLightChangesWaitingPreview() = SyncActivityPreview(false, PreviewChangesWaiting)

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletDarkChangesWaitingPreview() = SyncActivityPreview(true, PreviewChangesWaiting)

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletLightProblemPreview() = SyncActivityPreview(false, PreviewProblem)

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletDarkProblemPreview() = SyncActivityPreview(true, PreviewProblem)

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletLightSyncingPreview() = SyncActivityPreview(false, PreviewSyncing)

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletDarkSyncingPreview() = SyncActivityPreview(true, PreviewSyncing)

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletLightEmptyLogPreview() = SyncActivityPreview(false, PreviewEmptyLog)

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun SyncActivityTabletDarkEmptyLogPreview() = SyncActivityPreview(true, PreviewEmptyLog)
