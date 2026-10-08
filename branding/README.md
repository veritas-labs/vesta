# Branding

Everything in this directory is Vesta's brand identity: the logo, the app icon and
the launcher icons. **It is not covered by the Apache License 2.0** that applies to
the rest of the repository, and it may not be used without permission. See
[TRADEMARK.md](../TRADEMARK.md).

If you fork Vesta, replace these assets with your own and rename the app.

| Path | Used by |
| --- | --- |
| `android/res/` | Android launcher icons. Added to the `androidApp` resources in `androidApp/build.gradle.kts`. |
| `compose/drawable/` | The in-app logo and artwork. Compose Multiplatform allows only one resources folder per source set, so `shared/src/commonMain/composeResources/drawable/img_*.png` are symlinks to these files. |

## One brand asset lives elsewhere

The iOS app icon, `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png`,
has to stay inside the Xcode asset catalog. It is a brand asset under the same terms as
everything here.
