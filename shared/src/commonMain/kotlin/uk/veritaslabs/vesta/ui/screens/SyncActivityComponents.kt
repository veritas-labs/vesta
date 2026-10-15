package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.calendar.localMoment
import uk.veritaslabs.vesta.calendar.formatMinute
import uk.veritaslabs.vesta.syncactivity.PeerActivity
import uk.veritaslabs.vesta.syncactivity.ProblemReason
import uk.veritaslabs.vesta.syncactivity.SyncEventFilter
import uk.veritaslabs.vesta.syncactivity.SyncEventItem
import uk.veritaslabs.vesta.syncactivity.SyncEventKind
import uk.veritaslabs.vesta.syncactivity.SyncModuleName
import uk.veritaslabs.vesta.syncactivity.SyncVerdict
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.calendar_months
import vesta.shared.generated.resources.ic_alert_triangle
import vesta.shared.generated.resources.ic_arrow_down
import vesta.shared.generated.resources.ic_arrow_up
import vesta.shared.generated.resources.ic_circle_check
import vesta.shared.generated.resources.ic_clock
import vesta.shared.generated.resources.ic_home
import vesta.shared.generated.resources.ic_refresh
import vesta.shared.generated.resources.ic_topology_star_ring_2
import vesta.shared.generated.resources.ic_user_plus
import vesta.shared.generated.resources.ic_users
import vesta.shared.generated.resources.sync_activity_another_device
import vesta.shared.generated.resources.sync_activity_detail_kind
import vesta.shared.generated.resources.sync_activity_detail_owner
import vesta.shared.generated.resources.sync_activity_detail_retrying
import vesta.shared.generated.resources.sync_activity_detail_show_activity
import vesta.shared.generated.resources.sync_activity_detail_synced
import vesta.shared.generated.resources.sync_activity_detail_title
import vesta.shared.generated.resources.sync_activity_detail_will_keep_trying
import vesta.shared.generated.resources.sync_activity_direction_membership
import vesta.shared.generated.resources.sync_activity_direction_problem
import vesta.shared.generated.resources.sync_activity_direction_received
import vesta.shared.generated.resources.sync_activity_direction_sent
import vesta.shared.generated.resources.sync_activity_event_failed
import vesta.shared.generated.resources.sync_activity_event_mailbox_in
import vesta.shared.generated.resources.sync_activity_event_mailbox_out
import vesta.shared.generated.resources.sync_activity_event_membership
import vesta.shared.generated.resources.sync_activity_event_received
import vesta.shared.generated.resources.sync_activity_event_sent
import vesta.shared.generated.resources.sync_activity_event_unknown_message
import vesta.shared.generated.resources.sync_activity_event_unsupported_version
import vesta.shared.generated.resources.sync_activity_filter_all
import vesta.shared.generated.resources.sync_activity_filter_problems
import vesta.shared.generated.resources.sync_activity_filter_received
import vesta.shared.generated.resources.sync_activity_filter_sent
import vesta.shared.generated.resources.sync_activity_hub
import vesta.shared.generated.resources.sync_activity_invite
import vesta.shared.generated.resources.sync_activity_invite_body
import vesta.shared.generated.resources.sync_activity_module_calendar
import vesta.shared.generated.resources.sync_activity_module_cookbook
import vesta.shared.generated.resources.sync_activity_module_meal_plan
import vesta.shared.generated.resources.sync_activity_module_members
import vesta.shared.generated.resources.sync_activity_module_mailbox
import vesta.shared.generated.resources.sync_activity_module_merchants
import vesta.shared.generated.resources.sync_activity_module_pantry
import vesta.shared.generated.resources.sync_activity_module_shopping
import vesta.shared.generated.resources.sync_activity_module_tasks
import vesta.shared.generated.resources.sync_activity_offline
import vesta.shared.generated.resources.sync_activity_online
import vesta.shared.generated.resources.sync_activity_this_device
import vesta.shared.generated.resources.sync_activity_verdict_changes_body
import vesta.shared.generated.resources.sync_activity_verdict_changes_title
import vesta.shared.generated.resources.sync_activity_verdict_newer_body
import vesta.shared.generated.resources.sync_activity_verdict_newer_title
import vesta.shared.generated.resources.sync_activity_verdict_only_you_body
import vesta.shared.generated.resources.sync_activity_verdict_only_you_title
import vesta.shared.generated.resources.sync_activity_verdict_problem_daemon
import vesta.shared.generated.resources.sync_activity_verdict_problem_stalled
import vesta.shared.generated.resources.sync_activity_verdict_problem_title
import vesta.shared.generated.resources.sync_activity_verdict_syncing_body
import vesta.shared.generated.resources.sync_activity_verdict_syncing_title
import vesta.shared.generated.resources.sync_activity_verdict_up_to_date_body
import vesta.shared.generated.resources.sync_activity_verdict_up_to_date_title
import vesta.shared.generated.resources.sync_activity_verdict_up_to_date_with
import vesta.shared.generated.resources.sync_activity_verdict_waiting_body
import vesta.shared.generated.resources.sync_activity_verdict_waiting_title
import vesta.shared.generated.resources.sync_activity_your_device
import vesta.shared.generated.resources.sync_activity_day_date
import vesta.shared.generated.resources.sync_activity_day_today
import vesta.shared.generated.resources.sync_activity_day_yesterday
import vesta.shared.generated.resources.sync_activity_empty_filtered
import vesta.shared.generated.resources.sync_activity_empty_only_you
import vesta.shared.generated.resources.sync_activity_empty_title
import vesta.shared.generated.resources.sync_activity_empty_waiting
import uk.veritaslabs.vesta.calendar.CalendarDate
import uk.veritaslabs.vesta.ui.components.EmptyStateArt
import uk.veritaslabs.vesta.ui.components.EmptyStateIllustration
import androidx.compose.ui.text.style.TextAlign

/** The status card: an icon whose shape differs per verdict (never colour alone), the verdict as a heading, and one sentence of why. */
@Composable
internal fun SyncStatusCard(verdict: SyncVerdict, modifier: Modifier = Modifier) {
    val icon: DrawableResource
    val tint: Color
    val title: String
    val body: String
    when (verdict) {
        is SyncVerdict.Problem -> {
            icon = Res.drawable.ic_alert_triangle
            tint = MaterialTheme.colorScheme.error
            title = stringResource(Res.string.sync_activity_verdict_problem_title)
            body = stringResource(
                if (verdict.reason == ProblemReason.DaemonUnreachable) Res.string.sync_activity_verdict_problem_daemon
                else Res.string.sync_activity_verdict_problem_stalled,
            )
        }
        SyncVerdict.NeedsNewerVersion -> {
            icon = Res.drawable.ic_alert_triangle
            tint = MaterialTheme.colorScheme.secondary
            title = stringResource(Res.string.sync_activity_verdict_newer_title)
            body = stringResource(Res.string.sync_activity_verdict_newer_body)
        }
        SyncVerdict.OnlyYou -> {
            icon = Res.drawable.ic_home
            tint = MaterialTheme.colorScheme.onSurfaceVariant
            title = stringResource(Res.string.sync_activity_verdict_only_you_title)
            body = stringResource(Res.string.sync_activity_verdict_only_you_body)
        }
        is SyncVerdict.UpToDate -> {
            icon = Res.drawable.ic_circle_check
            tint = ConnectedGreen
            title = stringResource(Res.string.sync_activity_verdict_up_to_date_title)
            val ago = agoText(verdict.msAgo)
            body = verdict.withName?.let { stringResource(Res.string.sync_activity_verdict_up_to_date_with, ago, it) }
                ?: stringResource(Res.string.sync_activity_verdict_up_to_date_body, ago)
        }
        SyncVerdict.WaitingForOthers -> {
            icon = Res.drawable.ic_clock
            tint = MaterialTheme.colorScheme.onSurfaceVariant
            title = stringResource(Res.string.sync_activity_verdict_waiting_title)
            body = stringResource(Res.string.sync_activity_verdict_waiting_body)
        }
        SyncVerdict.ChangesWaiting -> {
            icon = Res.drawable.ic_arrow_up
            tint = MaterialTheme.colorScheme.secondary
            title = stringResource(Res.string.sync_activity_verdict_changes_title)
            body = stringResource(Res.string.sync_activity_verdict_changes_body)
        }
        SyncVerdict.Syncing -> {
            icon = Res.drawable.ic_refresh
            tint = MaterialTheme.colorScheme.primary
            title = stringResource(Res.string.sync_activity_verdict_syncing_title)
            body = stringResource(Res.string.sync_activity_verdict_syncing_body)
        }
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().testTag(SyncActivityTestTags.StatusCard),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            if (verdict == SyncVerdict.Syncing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = tint)
            } else {
                Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }.testTag(SyncActivityTestTags.VerdictTitle),
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SyncActivityTestTags.VerdictBody),
                )
            }
        }
    }
}

/**
 * One device or hub: a presence dot (filled online, ring offline) plus the words "Online" / "Offline" so it never rests on the dot alone,
 * the name, the owner, a hub icon and the relative time it last synced. Offline rows are muted. Opens the device's details.
 */
@Composable
internal fun SyncDeviceRow(peer: PeerActivity, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = !peer.online
    val nameColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val presence = stringResource(if (peer.online) Res.string.sync_activity_online else Res.string.sync_activity_offline)
    val role = peerRoleText(peer)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .testTag(SyncActivityTestTags.deviceRow(peer.agentId))
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dotColor = if (peer.online) ConnectedGreen else MaterialTheme.colorScheme.outline
            Box(
                Modifier.size(12.dp).let {
                    if (peer.online) it.background(dotColor, CircleShape) else it.border(BorderStroke(2.dp, dotColor), CircleShape)
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(peer.name, style = MaterialTheme.typography.bodyLarge, color = nameColor)
                Text(
                    text = listOfNotNull(role, presence).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SyncActivityTestTags.DevicePresence),
                )
            }
            if (peer.isHub) {
                Icon(
                    painter = painterResource(Res.drawable.ic_topology_star_ring_2),
                    contentDescription = stringResource(Res.string.sync_activity_hub),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = agoText(peer.lastSyncedMsAgo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Sam", "This device", "Your device", "Sam · Hub": who the device belongs to and whether it is a hub. */
@Composable
private fun peerRoleText(peer: PeerActivity): String? {
    val owner = when {
        peer.isYou -> stringResource(Res.string.sync_activity_this_device)
        peer.ownerName != null -> peer.ownerName
        peer.isOwnDevice -> stringResource(Res.string.sync_activity_your_device)
        else -> null
    }
    val hub = if (peer.isHub) stringResource(Res.string.sync_activity_hub) else null
    return listOfNotNull(owner, hub).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The row shown instead of the Devices list when this Home is only on this device. */
@Composable
internal fun SyncInviteRow(onInvite: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SyncActivityTestTags.InviteRow)
            .clickable(role = Role.Button, onClick = onInvite)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_user_plus),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(stringResource(Res.string.sync_activity_invite), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
            Text(
                stringResource(Res.string.sync_activity_invite_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The filter as a text button with a dropdown of All / Received / Sent / Problems. */
@Composable
internal fun SyncFilterMenu(filter: SyncEventFilter, onFilter: (SyncEventFilter) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val current = stringResource(filterLabel(filter))
        TextButton(
            onClick = { open = true },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(SyncActivityTestTags.FilterButton),
        ) {
            Text(current)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SyncEventFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(filterLabel(option))) },
                    onClick = {
                        open = false
                        onFilter(option)
                    },
                    modifier = Modifier.testTag(SyncActivityTestTags.filterOption(option)),
                )
            }
        }
    }
}

private fun filterLabel(filter: SyncEventFilter): StringResource = when (filter) {
    SyncEventFilter.All -> Res.string.sync_activity_filter_all
    SyncEventFilter.Received -> Res.string.sync_activity_filter_received
    SyncEventFilter.Sent -> Res.string.sync_activity_filter_sent
    SyncEventFilter.Problems -> Res.string.sync_activity_filter_problems
}

private fun moduleLabel(module: SyncModuleName): StringResource = when (module) {
    SyncModuleName.Pantry -> Res.string.sync_activity_module_pantry
    SyncModuleName.Shopping -> Res.string.sync_activity_module_shopping
    SyncModuleName.Cookbook -> Res.string.sync_activity_module_cookbook
    SyncModuleName.MealPlan -> Res.string.sync_activity_module_meal_plan
    SyncModuleName.Calendar -> Res.string.sync_activity_module_calendar
    SyncModuleName.Tasks -> Res.string.sync_activity_module_tasks
    SyncModuleName.Merchants -> Res.string.sync_activity_module_merchants
    SyncModuleName.Members -> Res.string.sync_activity_module_members
    SyncModuleName.Mailbox -> Res.string.sync_activity_module_mailbox
}

/** One log entry: a direction icon with a description, one plain sentence, the time, and a second line when there is one. */
@Composable
internal fun SyncActivityItem(item: SyncEventItem, peers: List<PeerActivity>, modifier: Modifier = Modifier) {
    val device = item.peerAgent?.let { id -> peers.firstOrNull { it.agentId == id }?.name } ?: stringResource(Res.string.sync_activity_another_device)
    val module = stringResource(moduleLabel(item.module))
    val count = item.count.coerceAtLeast(1)
    val sentence = when (item.kind) {
        SyncEventKind.Received -> pluralStringResource(Res.plurals.sync_activity_event_received, count, module, device, count)
        SyncEventKind.Sent -> pluralStringResource(Res.plurals.sync_activity_event_sent, count, module, device, count)
        SyncEventKind.Failed -> pluralStringResource(Res.plurals.sync_activity_event_failed, count, device, count)
        SyncEventKind.Membership -> pluralStringResource(Res.plurals.sync_activity_event_membership, count, device, count)
        SyncEventKind.MailboxIn -> pluralStringResource(Res.plurals.sync_activity_event_mailbox_in, count, module, device, count)
        SyncEventKind.MailboxOut -> pluralStringResource(Res.plurals.sync_activity_event_mailbox_out, count, module, device, count)
        SyncEventKind.UnsupportedVersion -> pluralStringResource(Res.plurals.sync_activity_event_unsupported_version, count, device, count)
        SyncEventKind.UnknownMessage -> pluralStringResource(Res.plurals.sync_activity_event_unknown_message, count, device, count)
    }
    val (icon, directionLabel) = when (item.kind) {
        SyncEventKind.Received, SyncEventKind.MailboxIn -> Res.drawable.ic_arrow_down to Res.string.sync_activity_direction_received
        SyncEventKind.Sent, SyncEventKind.MailboxOut -> Res.drawable.ic_arrow_up to Res.string.sync_activity_direction_sent
        SyncEventKind.Failed, SyncEventKind.UnsupportedVersion, SyncEventKind.UnknownMessage ->
            Res.drawable.ic_alert_triangle to Res.string.sync_activity_direction_problem
        SyncEventKind.Membership -> Res.drawable.ic_users to Res.string.sync_activity_direction_membership
    }
    val unreadable = item.kind == SyncEventKind.UnsupportedVersion || item.kind == SyncEventKind.UnknownMessage
    val failed = item.kind == SyncEventKind.Failed || unreadable
    val time = formatMinute(localMoment(item.lastAt).minute)
    val note = if (failed && (item.detail == "unreachable" || item.detail == "retrying")) stringResource(Res.string.sync_activity_detail_will_keep_trying) else null
    val what = item.detail?.takeIf { unreadable }
    val secondLine = listOfNotNull(time, note, what).joinToString(" · ")
    val directionText = stringResource(directionLabel)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$directionText. $sentence. $secondLine" }
            .testTag(SyncActivityTestTags.eventRow(item.id))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = directionText,
            tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(sentence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(secondLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Today", "Yesterday" or the date, for the day [day] relative to [today]. */
@Composable
internal fun dayHeaderText(day: CalendarDate, today: CalendarDate): String {
    val months = stringArrayResource(Res.array.calendar_months)
    return when (day) {
        today -> stringResource(Res.string.sync_activity_day_today)
        today.plusDays(-1) -> stringResource(Res.string.sync_activity_day_yesterday)
        else -> stringResource(Res.string.sync_activity_day_date, day.day, months[day.month - 1], day.year)
    }
}

/** Nothing has synced yet (or nothing matches the filter), with the reason underneath when there is one. */
@Composable
internal fun SyncEmptyLog(verdict: SyncVerdict?, narrowed: Boolean, modifier: Modifier = Modifier) {
    val reason = when {
        narrowed -> null
        verdict == SyncVerdict.OnlyYou -> Res.string.sync_activity_empty_only_you
        verdict == SyncVerdict.WaitingForOthers -> Res.string.sync_activity_empty_waiting
        else -> null
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp).testTag(SyncActivityTestTags.EmptyLog),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmptyStateIllustration(EmptyStateArt.SyncedDevices, size = 160.dp)
        Text(
            text = stringResource(if (narrowed) Res.string.sync_activity_empty_filtered else Res.string.sync_activity_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        reason?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(SyncActivityTestTags.EmptyReason),
            )
        }
    }
}

/**
 * A device's details, shown in a bottom sheet on a phone and inline beside the log on a tablet: name, owner, hub or one of your own
 * devices, when it last synced, and a note when a message to it is being retried. [onShowActivity] narrows the log to it (null hides the
 * button). The list of documents synced with the device is not offered: the screen's state does not carry it.
 */
@Composable
internal fun SyncDeviceDetail(peer: PeerActivity, onShowActivity: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp).testTag(SyncActivityTestTags.DeviceDetail),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.sync_activity_detail_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Text(peer.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        val owner = when {
            peer.isYou || peer.isOwnDevice -> stringResource(Res.string.sync_activity_your_device)
            else -> peer.ownerName
        }
        owner?.let { InfoLine(stringResource(Res.string.sync_activity_detail_owner), it) }
        val kind = if (peer.isHub) stringResource(Res.string.sync_activity_hub) else if (peer.isYou) stringResource(Res.string.sync_activity_this_device) else null
        kind?.let { InfoLine(stringResource(Res.string.sync_activity_detail_kind), it) }
        InfoLine(stringResource(Res.string.sync_activity_detail_synced), agoText(peer.lastSyncedMsAgo))
        peer.retryingSinceMsAgo?.let {
            Text(
                text = stringResource(Res.string.sync_activity_detail_retrying, agoText(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(SyncActivityTestTags.DeviceRetrying),
            )
        }
        onShowActivity?.let {
            OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SyncActivityTestTags.ShowDeviceActivity)) {
                Text(stringResource(Res.string.sync_activity_detail_show_activity))
            }
        }
    }
}
