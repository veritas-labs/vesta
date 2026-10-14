package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.devicesync.PairingStep
import uk.veritaslabs.vesta.devicesync.PairingStepStatus
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.components.SecondaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.device_pairing_progress_failed_title
import vesta.shared.generated.resources.device_pairing_progress_retry
import vesta.shared.generated.resources.device_pairing_progress_title
import vesta.shared.generated.resources.device_pairing_step_await_join
import vesta.shared.generated.resources.device_pairing_step_create_invite
import vesta.shared.generated.resources.device_pairing_step_grant_admin
import vesta.shared.generated.resources.device_pairing_step_prepare_group
import vesta.shared.generated.resources.device_pairing_step_save_device
import vesta.shared.generated.resources.device_pairing_step_send_invite

private val DevicePairingProgressTabletBreakpoint = 600.dp
private val StatusMarkerSize = 28.dp
private val ChecklistMaxWidth = 420.dp

/** Stable hooks for UI tests to target, independent of the displayed copy. */
internal object DevicePairingProgressTestTags {
    const val Checklist = "device_pairing_progress_checklist"
    const val ErrorText = "device_pairing_progress_error_text"
    const val RetryButton = "device_pairing_progress_retry_button"
    const val BackButton = "device_pairing_progress_back_button"

    fun step(step: PairingStep) = "device_pairing_progress_step_${step.name}"
}

/**
 * Replaces [DevicePairingPasteScreen] once a pasted link is submitted: every step of adding the device as a
 * checklist, each ticked off as it succeeds ([PairingStepStatus.Done]), spinning while it runs and marked
 * when it fails. When a step has failed, [errorMessage] explains and [onRetry] / [onCancel] offer another go
 * or a way back to the paste form; otherwise there is nothing to press, as the flow moves on by itself.
 */
@Composable
fun DevicePairingProgressScreen(
    steps: Map<PairingStep, PairingStepStatus>,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val failed = PairingStepStatus.Failed in steps.values

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isTablet = maxWidth >= DevicePairingProgressTabletBreakpoint
        val contentPadding = if (isTablet) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(contentPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(if (failed) Res.string.device_pairing_progress_failed_title else Res.string.device_pairing_progress_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = ChecklistMaxWidth).fillMaxWidth().testTag(DevicePairingProgressTestTags.Checklist),
            ) {
                PairingStep.entries.forEach { step ->
                    ChecklistRow(
                        label = stringResource(step.label()),
                        status = steps[step] ?: PairingStepStatus.Pending,
                        modifier = Modifier.testTag(DevicePairingProgressTestTags.step(step)),
                    )
                }
            }
            if (failed) {
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 24.dp).testTag(DevicePairingProgressTestTags.ErrorText),
                    )
                }
                PrimaryButton(
                    label = stringResource(Res.string.device_pairing_progress_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 24.dp).testTag(DevicePairingProgressTestTags.RetryButton),
                )
                SecondaryButton(
                    label = stringResource(Res.string.common_back),
                    onClick = onCancel,
                    modifier = Modifier.padding(top = 8.dp).testTag(DevicePairingProgressTestTags.BackButton),
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(label: String, status: PairingStepStatus, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        StatusMarker(status)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = when (status) {
                PairingStepStatus.Failed -> MaterialTheme.colorScheme.error
                PairingStepStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onBackground
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusMarker(status: PairingStepStatus) {
    val colors = MaterialTheme.colorScheme
    val description = status.name
    when (status) {
        PairingStepStatus.Running -> CircularProgressIndicator(
            strokeWidth = 3.dp,
            modifier = Modifier.size(StatusMarkerSize - 4.dp).semantics { contentDescription = description },
        )
        else -> {
            val (fill, glyph, glyphColor) = when (status) {
                PairingStepStatus.Done -> Triple(colors.primary, "✓", colors.onPrimary)
                PairingStepStatus.Failed -> Triple(colors.error, "✕", colors.onError)
                else -> Triple(colors.surfaceVariant, "", Color.Unspecified)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(StatusMarkerSize - 4.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .semantics { contentDescription = description },
            ) {
                Text(text = glyph, color = glyphColor, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun PairingStep.label(): StringResource = when (this) {
    PairingStep.PrepareGroup -> Res.string.device_pairing_step_prepare_group
    PairingStep.CreateInvite -> Res.string.device_pairing_step_create_invite
    PairingStep.SendInvite -> Res.string.device_pairing_step_send_invite
    PairingStep.AwaitJoin -> Res.string.device_pairing_step_await_join
    PairingStep.GrantAdmin -> Res.string.device_pairing_step_grant_admin
    PairingStep.SaveDevice -> Res.string.device_pairing_step_save_device
}

private val RunningSteps = PairingStep.entries.associateWith { step ->
    when {
        step.ordinal < PairingStep.AwaitJoin.ordinal -> PairingStepStatus.Done
        step == PairingStep.AwaitJoin -> PairingStepStatus.Running
        else -> PairingStepStatus.Pending
    }
}

private val FailedSteps = RunningSteps + (PairingStep.AwaitJoin to PairingStepStatus.Failed)

@Composable
private fun PreviewContent(darkTheme: Boolean) {
    VestaTheme(darkTheme = darkTheme) {
        DevicePairingProgressScreen(steps = FailedSteps, errorMessage = "Couldn't reach that device. Check it is open and online, then try again.")
    }
}

@Composable
private fun RunningPreviewContent(darkTheme: Boolean) {
    VestaTheme(darkTheme = darkTheme) { DevicePairingProgressScreen(steps = RunningSteps) }
}

@Preview(name = "Running · Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingProgressRunningPhoneLightPreview() = RunningPreviewContent(darkTheme = false)

@Preview(name = "Running · Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingProgressRunningPhoneDarkPreview() = RunningPreviewContent(darkTheme = true)

@Preview(name = "Failed · Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingProgressFailedPhoneLightPreview() = PreviewContent(darkTheme = false)

@Preview(name = "Failed · Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun DevicePairingProgressFailedPhoneDarkPreview() = PreviewContent(darkTheme = true)

@Preview(name = "Running · Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingProgressRunningTabletLightPreview() = RunningPreviewContent(darkTheme = false)

@Preview(name = "Running · Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingProgressRunningTabletDarkPreview() = RunningPreviewContent(darkTheme = true)

@Preview(name = "Failed · Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingProgressFailedTabletLightPreview() = PreviewContent(darkTheme = false)

@Preview(name = "Failed · Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun DevicePairingProgressFailedTabletDarkPreview() = PreviewContent(darkTheme = true)
