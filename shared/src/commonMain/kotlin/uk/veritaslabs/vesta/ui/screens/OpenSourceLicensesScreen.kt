package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.licenses.OpenSourceLicense
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.licenses_empty
import vesta.shared.generated.resources.licenses_intro
import vesta.shared.generated.resources.licenses_title

private val LicensesTabletBreakpoint = 600.dp

/** Stable hooks for UI tests to target. */
internal object OpenSourceLicensesTestTags {
    const val BackButton = "licenses_back_button"
    const val List = "licenses_list"
    const val Empty = "licenses_empty"
}

/**
 * The open source libraries Vesta is built with ([licenses], already sorted), each with its version and the licence(s) it comes
 * under, below a note that Vesta itself is free software. The back arrow calls [onBack]. UI only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    licenses: List<OpenSourceLicense>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val contentPadding = if (maxWidth >= LicensesTabletBreakpoint) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(OpenSourceLicensesTestTags.BackButton)) {
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
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentPadding).testTag(OpenSourceLicensesTestTags.List),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.licenses_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (licenses.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.licenses_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(OpenSourceLicensesTestTags.Empty),
                        )
                    }
                }
                items(licenses, key = { "${it.name}:${it.version}" }) { license -> LicenseRow(license) }
            }
        }
    }
}

@Composable
private fun LicenseRow(license: OpenSourceLicense) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = license.name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${license.version} · ${license.licenses.joinToString(" / ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun previewLicenses() = listOf(
    OpenSourceLicense("org.automerge:automerge", "0.0.9", listOf("MIT License")),
    OpenSourceLicense("app.cash.sqldelight:runtime", "2.4.0", listOf("Apache License, Version 2.0")),
    OpenSourceLicense("crates.io:x0x", "0.46.3", listOf("MIT OR Apache-2.0")),
    OpenSourceLicense("net.java.dev.jna:jna", "5.17.0", listOf("Apache License, Version 2.0", "GNU LESSER GENERAL PUBLIC LICENSE, Version 2.1")),
)

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun OpenSourceLicensesScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { OpenSourceLicensesScreen(previewLicenses()) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun OpenSourceLicensesScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { OpenSourceLicensesScreen(previewLicenses()) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun OpenSourceLicensesScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { OpenSourceLicensesScreen(previewLicenses()) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun OpenSourceLicensesScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { OpenSourceLicensesScreen(previewLicenses()) }
}
