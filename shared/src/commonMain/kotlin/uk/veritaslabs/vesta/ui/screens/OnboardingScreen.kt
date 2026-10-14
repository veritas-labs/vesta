package uk.veritaslabs.vesta.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import uk.veritaslabs.vesta.ui.components.PageDots
import uk.veritaslabs.vesta.ui.components.PrimaryButton
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import uk.veritaslabs.vesta.ui.theme.artPaper
import uk.veritaslabs.vesta.ui.theme.isDarkArt
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.ic_close
import vesta.shared.generated.resources.common_back
import vesta.shared.generated.resources.tour_close
import vesta.shared.generated.resources.tour_get_started
import vesta.shared.generated.resources.tour_next
import vesta.shared.generated.resources.tour_page_of

private val OnboardingContentMaxWidth = 480.dp
private val OnboardingWideBreakpoint = 720.dp
private val OnboardingIllustrationMaxSize = 420.dp
private val OnboardingIllustrationShape = RoundedCornerShape(24.dp)

/** Stable hooks for UI tests to target, independent of displayed copy. */
internal object OnboardingTestTags {
    const val Pager = "onboarding_pager"
    const val Page = "onboarding_page"
    const val Illustration = "onboarding_illustration"
    const val Title = "onboarding_title"
    const val Body = "onboarding_body"
    const val Dots = "onboarding_dots"
    const val Next = "onboarding_next"
    const val Previous = "onboarding_previous"
    const val Finish = "onboarding_finish"
    const val Close = "onboarding_close"
}

/** Whether the tour is the one every new install has to go through, or one the user opened again from Settings. */
enum class OnboardingMode { FirstLaunch, Revisit }

/**
 * The welcome tour: a full-screen slide show of what Vesta is for, one benefit per page, ending on a page whose button calls [onFinish].
 * On [OnboardingMode.FirstLaunch] it cannot be skipped or closed, so the last page's button is the only way out; on
 * [OnboardingMode.Revisit] a close button on every page calls [onFinish] too. Only the [slides] that are enabled are shown.
 * [initialPage] is the page first shown.
 */
@Composable
fun OnboardingScreen(
    mode: OnboardingMode,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    onFinish: () -> Unit = {},
) = OnboardingScreen(mode, OnboardingSlides, modifier, initialPage, onFinish)

@Composable
internal fun OnboardingScreen(
    mode: OnboardingMode,
    slides: List<OnboardingSlide>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    onFinish: () -> Unit = {},
) {
    val shown = slides.filter { it.enabled }
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, shown.lastIndex)) { shown.size }
    val scope = rememberCoroutineScope()
    val current = pagerState.currentPage
    val isLast = current == shown.lastIndex

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        // Always laid out, so the page does not change height between the two modes.
        Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (mode == OnboardingMode.Revisit) {
                IconButton(onClick = onFinish, modifier = Modifier.testTag(OnboardingTestTags.Close)) {
                    Icon(painter = painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.tour_close))
                }
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth().testTag(OnboardingTestTags.Pager)) { index ->
            OnboardingPage(shown[index], modifier = Modifier.testTag(OnboardingTestTags.Page))
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageDots(
                count = shown.size,
                current = current,
                description = stringResource(Res.string.tour_page_of, current + 1, shown.size),
                modifier = Modifier.testTag(OnboardingTestTags.Dots),
            )
            if (isLast) {
                PrimaryButton(
                    label = stringResource(if (mode == OnboardingMode.FirstLaunch) Res.string.tour_get_started else Res.string.tour_close),
                    modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.Finish),
                    onClick = onFinish,
                )
            } else {
                PrimaryButton(
                    label = stringResource(Res.string.tour_next),
                    modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.Next),
                    onClick = { scope.launch { pagerState.animateScrollToPage(current + 1) } },
                )
            }
            // Always laid out, so the buttons above do not jump between the first page and the rest.
            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                if (current > 0) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(current - 1) } },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.testTag(OnboardingTestTags.Previous),
                    ) { Text(stringResource(Res.string.common_back)) }
                }
            }
        }
    }
}

/** The picture above the words on a narrow screen, beside them on a wide one. */
@Composable
private fun OnboardingPage(slide: OnboardingSlide, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (maxWidth >= OnboardingWideBreakpoint && maxWidth > maxHeight) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    OnboardingIllustration(slide, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                }
                OnboardingText(slide, Modifier.weight(1f), TextAlign.Start, Alignment.Start)
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = OnboardingContentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OnboardingIllustration(slide, Modifier.widthIn(max = OnboardingIllustrationMaxSize).fillMaxWidth().aspectRatio(1f))
                OnboardingText(slide, Modifier.fillMaxWidth(), TextAlign.Center, Alignment.CenterHorizontally)
            }
        }
    }
}

@Composable
private fun OnboardingIllustration(slide: OnboardingSlide, modifier: Modifier = Modifier) {
    val dark = isDarkArt()
    Image(
        painter = painterResource(if (dark) slide.illustrationDark else slide.illustration),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .clip(OnboardingIllustrationShape)
            .background(artPaper(dark))
            .testTag(OnboardingTestTags.Illustration),
    )
}

@Composable
private fun OnboardingText(slide: OnboardingSlide, modifier: Modifier, textAlign: TextAlign, horizontalAlignment: Alignment.Horizontal) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(slide.title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.Title),
        )
        Text(
            text = stringResource(slide.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.Body),
        )
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun OnboardingScreenPhoneLightPreview() {
    VestaTheme(darkTheme = false) { OnboardingScreen(OnboardingMode.FirstLaunch) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun OnboardingScreenPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { OnboardingScreen(OnboardingMode.FirstLaunch, initialPage = 2) }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun OnboardingScreenPhoneLightRevisitLastPreview() {
    VestaTheme(darkTheme = false) { OnboardingScreen(OnboardingMode.Revisit, initialPage = Int.MAX_VALUE) }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun OnboardingScreenPhoneDarkLastPreview() {
    VestaTheme(darkTheme = true) { OnboardingScreen(OnboardingMode.FirstLaunch, initialPage = Int.MAX_VALUE) }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun OnboardingScreenTabletLightPreview() {
    VestaTheme(darkTheme = false) { OnboardingScreen(OnboardingMode.FirstLaunch, initialPage = 1) }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun OnboardingScreenTabletDarkPreview() {
    VestaTheme(darkTheme = true) { OnboardingScreen(OnboardingMode.Revisit, initialPage = 3) }
}
