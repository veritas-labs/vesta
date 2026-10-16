# Contributing to Vesta

Thanks for your interest. Vesta is alpha software and moves quickly, so a short conversation before a large change saves
everyone time.

## Ways to help

- **Report a bug.** Open an issue with what you did, what you expected, what happened, the platform and version
  (Android version or desktop OS, Vesta version, x0x version), and logs if you have them. For sync problems, say how many
  devices were involved and whether a hub was in use.
- **Suggest a feature.** Open an issue first. Vesta is modular and deliberately small; see
  [docs/product](docs/product) (especially [NON-GOALS](docs/product/NON-GOALS.md)) before proposing something.
- **Fix something or add a feature.** Pick an issue, say you're on it, and open a pull request.
- **Help with a platform.** Windows desktop discovery of the x0x daemon, macOS testing and packaging, and iOS are all
  open. iOS is blocked on Automerge having no iOS binding.
- **Improve the docs**, including translations of the app's strings.

Security problems: don't open a public issue. Use GitHub's "Report a vulnerability" on the Security tab.

## Setting up

Follow [Build from source](README.md#build-from-source). In short: JDK 17+, the Android SDK, and Vesta's kits published to
your local Maven repository (`./gradlew publishToMavenLocal` in the veritas-kits repository). Rust and the Android NDK are
needed only when you build the Android app. For the desktop app you also need an x0x daemon running (`x0x start`).

Before opening a pull request, run:

```
./gradlew :shared:check
```

## How the code is organised

Most code lives in `shared/` (Kotlin Multiplatform; `commonMain` first, platform source sets only where needed).
Architecture decisions are written down as ADRs in [docs/adr](docs/adr). Read the ones that touch your area, and if your
change reverses or adds a significant decision, add or amend an ADR in the same pull request. Numbering is sequential;
take the next free number.

## Conventions

- **Match the surrounding code**: naming, comment density and idiom.
- **Prefer null-safe `let`** (`x?.let { … }`) to `if (x != null) { … }`.
- **Tests are required.** Every change comes with unit tests, and every user-facing feature with Given/When/Then
  acceptance tests in [docs/testing/acceptance-tests.md](docs/testing/acceptance-tests.md).
- **Arrange / Act / Assert**: every test method carries `// Arrange`, `// Act` and `// Assert` comments.
- **Previews**: every screen and UI component has `@Preview`s covering light and dark × phone and tablet, grouped by device.
  See [Compose previews](README.md#compose-previews) and [troubleshooting](docs/troubleshooting/compose-previews.md).
- **Theme, don't hard-code**: take colours, shapes and typography from the app theme.
- **Accessibility matters.** Vesta is meant to be usable from age 8 to 100, including with poor vision: check text size,
  contrast and touch-target size.
- **Local-first**: no feature may require an account or a central server.
- **Database changes** need a migration; see [docs/testing/database-migrations.md](docs/testing/database-migrations.md).
- **Document formats are versioned** (see [ADR-034](docs/adr/ADR-034-document-format-versioning.md)). Don't change a synced
  document's shape without following it, since old and new devices will meet.
- **Dependencies**: after adding or changing one (including the Rust crates in `ffi/x0x-ffi`), run
  `./gradlew generateLicenses` and commit the result.

## Pull requests

- Keep each pull request to one change, and keep the diff free of unrelated reformatting.
- Describe what and why, and how you tested it (including on which device or emulator for UI changes).
- Small, focused commits with clear messages are easier to review than one large one.
- Be patient: this is maintained by a small team.

## Licence and trademarks

By contributing you agree that your contribution is licensed under the [Apache License 2.0](LICENSE), the same as the
project. The name Vesta and the assets in [branding/](branding/) are not covered by that licence; see
[TRADEMARK.md](TRADEMARK.md).

## Conduct

Be kind and assume good faith. Harassment and personal attacks are not tolerated, and maintainers may remove comments or
contributors who engage in them.
