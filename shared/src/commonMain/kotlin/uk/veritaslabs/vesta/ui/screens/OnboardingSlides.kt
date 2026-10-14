package uk.veritaslabs.vesta.ui.screens

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.onboarding_assistants
import vesta.shared.generated.resources.tour_assistants_body
import vesta.shared.generated.resources.onboarding_assistants_dark
import vesta.shared.generated.resources.tour_assistants_title
import vesta.shared.generated.resources.onboarding_calendar
import vesta.shared.generated.resources.tour_calendar_body
import vesta.shared.generated.resources.onboarding_calendar_dark
import vesta.shared.generated.resources.tour_calendar_title
import vesta.shared.generated.resources.onboarding_cookbook
import vesta.shared.generated.resources.tour_cookbook_body
import vesta.shared.generated.resources.onboarding_cookbook_dark
import vesta.shared.generated.resources.tour_cookbook_title
import vesta.shared.generated.resources.onboarding_done
import vesta.shared.generated.resources.tour_done_body
import vesta.shared.generated.resources.onboarding_done_dark
import vesta.shared.generated.resources.tour_done_title
import vesta.shared.generated.resources.onboarding_home
import vesta.shared.generated.resources.tour_home_body
import vesta.shared.generated.resources.onboarding_home_dark
import vesta.shared.generated.resources.tour_home_title
import vesta.shared.generated.resources.onboarding_pantry
import vesta.shared.generated.resources.tour_pantry_body
import vesta.shared.generated.resources.onboarding_pantry_dark
import vesta.shared.generated.resources.tour_pantry_title
import vesta.shared.generated.resources.onboarding_privacy
import vesta.shared.generated.resources.tour_privacy_body
import vesta.shared.generated.resources.onboarding_privacy_dark
import vesta.shared.generated.resources.tour_privacy_title

/**
 * Turn each of these on when its feature is ready to show. A slide that is off is left out of the slide show altogether, and the
 * page dots and the last-page button follow whatever is left.
 */
internal const val HomeSlideEnabled = true
internal const val PantrySlideEnabled = true
internal const val CookbookSlideEnabled = true
internal const val CalendarSlideEnabled = true

/** Assistants are desktop only and have not been run against a live bot yet (ADR-027), so the slide waits. */
internal const val AssistantsSlideEnabled = false
internal const val PrivacySlideEnabled = true

/**
 * One page of the welcome tour. [illustration] and [illustrationDark] are the same picture with its ink and paper recoloured for
 * the theme (see `scripts/slides-to-vectors.py`).
 */
internal class OnboardingSlide(
    val id: String,
    val title: StringResource,
    val body: StringResource,
    val illustration: DrawableResource,
    val illustrationDark: DrawableResource,
    val enabled: Boolean = true,
)

/** Every slide in the order they are shown; the finishing slide is always last. */
internal val OnboardingSlides = listOf(
    OnboardingSlide(
        id = "home",
        title = Res.string.tour_home_title,
        body = Res.string.tour_home_body,
        illustration = Res.drawable.onboarding_home,
        illustrationDark = Res.drawable.onboarding_home_dark,
        enabled = HomeSlideEnabled,
    ),
    OnboardingSlide(
        id = "pantry",
        title = Res.string.tour_pantry_title,
        body = Res.string.tour_pantry_body,
        illustration = Res.drawable.onboarding_pantry,
        illustrationDark = Res.drawable.onboarding_pantry_dark,
        enabled = PantrySlideEnabled,
    ),
    OnboardingSlide(
        id = "cookbook",
        title = Res.string.tour_cookbook_title,
        body = Res.string.tour_cookbook_body,
        illustration = Res.drawable.onboarding_cookbook,
        illustrationDark = Res.drawable.onboarding_cookbook_dark,
        enabled = CookbookSlideEnabled,
    ),
    OnboardingSlide(
        id = "calendar",
        title = Res.string.tour_calendar_title,
        body = Res.string.tour_calendar_body,
        illustration = Res.drawable.onboarding_calendar,
        illustrationDark = Res.drawable.onboarding_calendar_dark,
        enabled = CalendarSlideEnabled,
    ),
    OnboardingSlide(
        id = "assistants",
        title = Res.string.tour_assistants_title,
        body = Res.string.tour_assistants_body,
        illustration = Res.drawable.onboarding_assistants,
        illustrationDark = Res.drawable.onboarding_assistants_dark,
        enabled = AssistantsSlideEnabled,
    ),
    OnboardingSlide(
        id = "privacy",
        title = Res.string.tour_privacy_title,
        body = Res.string.tour_privacy_body,
        illustration = Res.drawable.onboarding_privacy,
        illustrationDark = Res.drawable.onboarding_privacy_dark,
        enabled = PrivacySlideEnabled,
    ),
    OnboardingSlide(
        id = "done",
        title = Res.string.tour_done_title,
        body = Res.string.tour_done_body,
        illustration = Res.drawable.onboarding_done,
        illustrationDark = Res.drawable.onboarding_done_dark,
    ),
)
