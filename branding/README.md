# Branding

Everything in this directory is Vesta's brand identity: the logo, the app icon and
the launcher icons. **It is not covered by the Apache License 2.0** that applies to
the rest of the repository, and it may not be used without permission. See
[TRADEMARK.md](../TRADEMARK.md).

If you fork Vesta, replace these assets with your own and rename the app.

| Path | Used by |
| --- | --- |
| `app-icon.png` | The master app icon (1080x1080). The Android launcher icons, the iOS icon and the desktop (Linux) icon are all generated from it. |
| `android/res/` | Android launcher icons. Added to the `androidApp` resources in `androidApp/build.gradle.kts`. |
| `compose/drawable/` | The in-app logo and artwork. Compose Multiplatform allows only one resources folder per source set, so the files in `shared/src/commonMain/composeResources/drawable/` that start `img_` or `onboarding_` are symlinks to these. The `onboarding_*.xml`, `empty_*.xml` and `img_vesta_line*.xml` files are generated (light and dark) from `slides/` by `scripts/slides-to-vectors.py`; do not edit them by hand. |
| `slides/` | The Illustro line-art SVGs the welcome tour, the empty Home picker and the empty states are made from (ADR-030). |

## One brand asset lives elsewhere

The iOS app icon (a 1024px copy of `app-icon.png`), `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png`,
has to stay inside the Xcode asset catalog. It is a brand asset under the same terms as
everything here.
