package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PageDots
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.device_pairing_back
import vesta.shared.generated.resources.hub_tutorial_finish
import vesta.shared.generated.resources.hub_tutorial_good_body
import vesta.shared.generated.resources.hub_tutorial_good_point_1
import vesta.shared.generated.resources.hub_tutorial_good_point_2
import vesta.shared.generated.resources.hub_tutorial_good_point_3
import vesta.shared.generated.resources.hub_tutorial_good_title
import vesta.shared.generated.resources.hub_tutorial_how_body
import vesta.shared.generated.resources.hub_tutorial_how_point_1
import vesta.shared.generated.resources.hub_tutorial_how_point_2
import vesta.shared.generated.resources.hub_tutorial_how_point_3
import vesta.shared.generated.resources.hub_tutorial_how_title
import vesta.shared.generated.resources.hub_tutorial_more_body
import vesta.shared.generated.resources.hub_tutorial_more_point_1
import vesta.shared.generated.resources.hub_tutorial_more_point_2
import vesta.shared.generated.resources.hub_tutorial_more_title
import vesta.shared.generated.resources.hub_tutorial_next
import vesta.shared.generated.resources.hub_tutorial_page_of
import vesta.shared.generated.resources.hub_tutorial_poor_body
import vesta.shared.generated.resources.hub_tutorial_poor_point_1
import vesta.shared.generated.resources.hub_tutorial_poor_point_2
import vesta.shared.generated.resources.hub_tutorial_poor_point_3
import vesta.shared.generated.resources.hub_tutorial_poor_title
import vesta.shared.generated.resources.hub_tutorial_previous
import vesta.shared.generated.resources.hub_tutorial_ready_body
import vesta.shared.generated.resources.hub_tutorial_ready_point_1
import vesta.shared.generated.resources.hub_tutorial_ready_point_2
import vesta.shared.generated.resources.hub_tutorial_ready_point_3
import vesta.shared.generated.resources.hub_tutorial_ready_title
import vesta.shared.generated.resources.hub_tutorial_skip
import vesta.shared.generated.resources.hub_tutorial_trust_body
import vesta.shared.generated.resources.hub_tutorial_trust_point_1
import vesta.shared.generated.resources.hub_tutorial_trust_point_2
import vesta.shared.generated.resources.hub_tutorial_trust_point_3
import vesta.shared.generated.resources.hub_tutorial_trust_title
import vesta.shared.generated.resources.hub_tutorial_what_body
import vesta.shared.generated.resources.hub_tutorial_what_point_1
import vesta.shared.generated.resources.hub_tutorial_what_point_2
import vesta.shared.generated.resources.hub_tutorial_what_title
import vesta.shared.generated.resources.hub_tutorial_why_body
import vesta.shared.generated.resources.hub_tutorial_why_point_1
import vesta.shared.generated.resources.hub_tutorial_why_point_2
import vesta.shared.generated.resources.hub_tutorial_why_title
import vesta.shared.generated.resources.ic_arrow_left
import vesta.shared.generated.resources.ic_devices
import vesta.shared.generated.resources.ic_home
import vesta.shared.generated.resources.ic_home_heart
import vesta.shared.generated.resources.ic_link
import vesta.shared.generated.resources.ic_settings
import vesta.shared.generated.resources.ic_topology_star_ring_2
import vesta.shared.generated.resources.ic_users

private val HubTutorialContentMaxWidth = 560.dp
private val HubTutorialIconSize = 96.dp

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object HubTutorialTestTags {
    const val BackButton = "hub_tutorial_back_button"
    const val Skip = "hub_tutorial_skip"
    const val Pager = "hub_tutorial_pager"
    const val Page = "hub_tutorial_page"
    const val Title = "hub_tutorial_title"
    const val Point = "hub_tutorial_point"
    const val Dots = "hub_tutorial_dots"
    const val Next = "hub_tutorial_next"
    const val Previous = "hub_tutorial_previous"
    const val Finish = "hub_tutorial_finish"
}

private class HubTutorialPage(
    val icon: DrawableResource,
    val title: StringResource,
    val body: StringResource,
    val points: List<StringResource>,
)

private val HubTutorialPages = listOf(
    HubTutorialPage(
        icon = Res.drawable.ic_topology_star_ring_2,
        title = Res.string.hub_tutorial_what_title,
        body = Res.string.hub_tutorial_what_body,
        points = listOf(Res.string.hub_tutorial_what_point_1, Res.string.hub_tutorial_what_point_2),
    ),
    HubTutorialPage(
        icon = Res.drawable.ic_users,
        title = Res.string.hub_tutorial_why_title,
        body = Res.string.hub_tutorial_why_body,
        points = listOf(Res.string.hub_tutorial_why_point_1, Res.string.hub_tutorial_why_point_2),
    ),
    HubTutorialPage(
        icon = Res.drawable.ic_link,
        title = Res.string.hub_tutorial_how_title,
        body = Res.string.hub_tutorial_how_body,
        points = listOf(Res.string.hub_tutorial_how_point_1, Res.string.hub_tutorial_how_point_2, Res.string.hub_tutorial_how_point_3),
    ),
    HubTutorialPage(
        icon = Res.drawable.ic_devices,
        title = Res.string.hub_tutorial_good_title,
        body = Res.string.hub_tutorial_good_body,
        points = listOf(Res.string.hub_tutorial_good_point_1, Res.string.hub_tutorial_good_point_2, Res.string.hub_tutorial_good_point_3),
    ),
    HubTutorialPage(
        icon = Res.drawable.ic_home,
        title = Res.string.hub_tutorial_poor_title,
        body = Res.string.hub_tutorial_poor_body,
        points = listOf(Res.string.hub_tutorial_poor_point_1, Res.string.hub_tutorial_poor_point_2, Res.string.hub_tutorial_poor_point_3),
    ),
    HubTutorialPage(
        icon = Res.drawable.ic_home_heart,
        title = Res.string.hub_tutorial_trust_title,
        body = Res.string.hub_tutorial_trust_body,
        points = listOf(Res.string.hub_tutorial_trust_point_1, Res.string.hub_tutorial_trust_point_2, Res.string.hub_tutorial_trust_point_3),
    ),
    HubTutorialPage(
        icon = Res.drawable.ic_topology_star_ring_2,
        title = Res.string.hub_tutorial_more_title,
        body = Res.string.hub_tutorial_more_body,
        points = listOf(Res.string.hub_tutorial_more_point_1, Res.string.hub_tutorial_more_point_2),
    ),
    HubTutorialPage(
        icon = Res.drawable.ic_settings,
        title = Res.string.hub_tutorial_ready_title,
        body = Res.string.hub_tutorial_ready_body,
        points = listOf(Res.string.hub_tutorial_ready_point_1, Res.string.hub_tutorial_ready_point_2, Res.string.hub_tutorial_ready_point_3),
    ),
)

/**
 * A full-screen slide show that explains always-on hubs in plain terms: what one is, why it helps, how it works, which devices make
 * good and poor hubs, what to trust it with, and how to set one up. Swipe (or Next / Previous) between pages; [onBack] and Skip leave
 * without finishing, and the last page's button calls [onFinish], which should take the user on to setting a hub up.
 * [initialPage] is the page first shown.
 */
@Composable
fun HubTutorialScreen(
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    onFinish: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val pages = HubTutorialPages
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, pages.lastIndex)) { pages.size }
    val scope = rememberCoroutineScope()
    val current = pagerState.currentPage
    val isLast = current == pages.lastIndex

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag(HubTutorialTestTags.BackButton)) {
                Icon(painter = painterResource(Res.drawable.ic_arrow_left), contentDescription = stringResource(Res.string.device_pairing_back))
            }
            Spacer(Modifier.weight(1f))
            // Kept in the layout on the last page so the bar does not change height.
            if (!isLast) {
                TextButton(onClick = onBack, modifier = Modifier.testTag(HubTutorialTestTags.Skip)) {
                    Text(stringResource(Res.string.hub_tutorial_skip))
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth().testTag(HubTutorialTestTags.Pager)) { index ->
            HubTutorialPageContent(pages[index], modifier = Modifier.testTag(HubTutorialTestTags.Page))
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageDots(
                count = pages.size,
                current = current,
                description = stringResource(Res.string.hub_tutorial_page_of, current + 1, pages.size),
                modifier = Modifier.testTag(HubTutorialTestTags.Dots),
            )
            if (isLast) {
                PrimaryButton(
                    label = stringResource(Res.string.hub_tutorial_finish),
                    modifier = Modifier.fillMaxWidth().testTag(HubTutorialTestTags.Finish),
                    onClick = onFinish,
                )
            } else {
                PrimaryButton(
                    label = stringResource(Res.string.hub_tutorial_next),
                    modifier = Modifier.fillMaxWidth().testTag(HubTutorialTestTags.Next),
                    onClick = { scope.launch { pagerState.animateScrollToPage(current + 1) } },
                )
            }
            // Always laid out, so the buttons above do not jump between the first page and the rest.
            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                if (current > 0) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(current - 1) } },
                        modifier = Modifier.testTag(HubTutorialTestTags.Previous),
                    ) { Text(stringResource(Res.string.hub_tutorial_previous)) }
                }
            }
        }
    }
}

@Composable
private fun HubTutorialPageContent(page: HubTutorialPage, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = HubTutorialContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(HubTutorialIconSize)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(page.icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(HubTutorialIconSize / 2),
                    )
                }
            }
            Text(
                text = stringResource(page.title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(HubTutorialTestTags.Title),
            )
            Text(
                text = stringResource(page.body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                page.points.forEach { point -> PointCard(stringResource(point)) }
            }
        }
    }
}

@Composable
private fun PointCard(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag(HubTutorialTestTags.Point),
        )
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubTutorialScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { HubTutorialScreen() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubTutorialScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { HubTutorialScreen(initialPage = 3) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubTutorialScreenPhoneLightLastPreview() {
    VestaTheme(darkTheme = false) { HubTutorialScreen(initialPage = HubTutorialPages.lastIndex) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun HubTutorialScreenPhoneDarkLastPreview() {
    VestaTheme(darkTheme = true) { HubTutorialScreen(initialPage = HubTutorialPages.lastIndex) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubTutorialScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { HubTutorialScreen(initialPage = 2) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubTutorialScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { HubTutorialScreen(initialPage = 4) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubTutorialScreenTabletLightLastPreview() {
    VestaTheme(darkTheme = false) { HubTutorialScreen(initialPage = HubTutorialPages.lastIndex) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun HubTutorialScreenTabletDarkLastPreview() {
    VestaTheme(darkTheme = true) { HubTutorialScreen() }
}
