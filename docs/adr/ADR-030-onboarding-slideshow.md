# ADR-030: The welcome tour (onboarding slideshow)

**Status:** Implemented (2026-10-14); run only under Compose UI tests and rendered to images, not yet by hand on a device or the
emulator. The Android debug build compiles.

## Context

A new install opens on an empty Home picker with nothing to say what Vesta is for. A short slideshow should say *why* someone
would use it (the benefits), with *how* it works (private, no account, direct sync) as a quieter closing point, since that matters
to some people and not to others.

## Decisions

### Story: why first, how last

One slide per benefit, bundling related features to keep it short:

1. One place for home life (Homes, invite links)
2. Never run out. Never double-buy. (Pantry and the Shopping list)
3. Settle "what's for dinner?" (Cookbook and the Meal planner)
4. Everyone on the same page (the shared Home calendar and the private calendar)
5. Let your assistant help (assistants, ADR-027), **switched off**
6. Yours, and only yours (no account, no cloud, direct device sync)
7. You're all set (the finishing slide)

### Slides are switched on and off in code

Each slide has an `enabled` flag backed by a `const val` in `OnboardingSlides.kt` (`AssistantsSlideEnabled = false`, and so on).
A slide that is off is filtered out, and the page dots, the "Page n of m" description and the last-page button all follow what is
left. Turning assistants on when the feature is ready is a one-line change. There is no feature-flag mechanism elsewhere in the
app (assistants are gated by passing or omitting a dependency), so this does not reuse one.

### Not skippable on first launch, closable when revisited

`OnboardingMode.FirstLaunch` has no skip, no close and no top bar action: the last slide's "Get started" is the only way out.
`OnboardingMode.Revisit` (from Settings > Application > Welcome tour) shows a close button on every slide, and the last button
says Close. Both return to where the user came from: the Home picker after the first launch, Settings after a revisit.

### Only new installs see it

The flag is the app setting `onboardingSeen` (device level, not synced; no migration, it is a new key in `AppSetting`). When it is
unset, the app asks whether this is an existing install: if the user already has a Home or has chosen a name, the tour is skipped
and the flag set; otherwise the tour shows. The check waits for the Homes and the Profile to load, so the Home picker never flashes
first. Someone who has installed Vesta but never made a Home or chosen a name counts as new and sees the tour.

The Settings cog is hidden while there are no Homes (existing behaviour), so the tour can only be reopened once a Home exists.

### Illustrations: Illustro SVGs recoloured per theme by a script

The pictures are line art from Illustro (`branding/slides/*.svg`, 1:1; the goddess is 2:3). They are flat exports: the ink is black
filled shapes and the paper is an off-white background plus off-white "knockout" shapes hiding the blobs behind the lines, so a
single tint would recolour the blobs too. `scripts/slides-to-vectors.py` turns each SVG into two Android vector drawables, light
and dark, recolouring by role:

- ink becomes the theme's text colour (`#1D1B16` / `#EDE5D8`)
- paper becomes the theme's background (`#F5F4ED` / `#15130F`), exactly, so the picture has no visible edge
- blobs are left alone

The goddess (`img_vesta_line`, used on the empty Home picker in place of `img_vesta.png`) is shaded in many greys, so her greys are
mapped onto the ramp between ink and paper by lightness instead. The script fails with a message if a file has an element or
colour it does not understand. The drawables live in `branding/compose/drawable/` (brand art, not Apache-licensed) and are
symlinked into the Compose resources like the other brand images. Compose chooses light or dark from the background's luminance.

Known rough edges: on the Halloween theme (background `#080605`) the paper shows as a faintly lighter card, and in dark mode the
pale blobs are quite bright against the background.

### Layout

A square illustration above the text on a phone; beside the text on a screen wider than 720dp and wider than it is tall, with the
text capped at 480dp otherwise. Content is top-aligned so the text does not shift between slides. The page dots are a shared
`PageDots` component, also used by the hub tutorial.

## Acceptance criteria

See `OnboardingAcceptanceTest` (`docs/testing/acceptance-tests.md`):

1. A new install shows the tour before anything else, and it cannot be skipped.
2. Finishing it lands on the Home picker.
3. It is shown once.
4. An install that already has a Home never sees it.
5. It can be opened again from Settings, with a close button, and closing returns to Settings.

## Not done

- No by-hand run on a device or the emulator, and no check on iOS.
- The copy is a first draft.
- The assistants slide has its art and copy but is off.
- The Hub tutorial's art and the older `onboarding_*` Home picker strings are unchanged.
