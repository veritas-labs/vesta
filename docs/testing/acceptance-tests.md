# Acceptance tests

Every feature ships with **acceptance tests** as well as unit tests. The acceptance tests say what "feature complete"
means: when they all pass, a user can do what the feature promises. Unit tests say the parts work; acceptance tests
say the parts add up to the feature.

## The rule

A feature is not done until:

1. its **acceptance criteria** are written down (in the feature's ADR, plan or design doc, or if it has none, in the
   acceptance test class's KDoc), as plain sentences from the user's point of view;
2. **every criterion has at least one acceptance test**; and
3. the acceptance tests pass alongside the unit tests (`./gradlew :shared:jvmTest`).

Start from the user's own example of why they want the feature, and turn it into scenarios before writing code.

## What an acceptance test is

- **One scenario per test**, named as a sentence about user-visible behaviour
  (`filteringToTheShopImStandingInShowsOnlyItemsPlannedForIt`), not after a function.
- **Given / When / Then.** Each test carries `// Arrange — Given …`, `// Act — When …` and `// Assert — Then …`
  comments (this satisfies the project's Arrange/Act/Assert rule too). Write the Given/When/Then as a sentence of
  the scenario, not a restatement of the code.
- **Driven like a user:** tap, type and read what's on screen, using the screens' test tags and visible text.
  Don't call view-model or document methods to make the thing under test happen, and don't assert on internals.
  Seeding the *Given* through the real source (`source.save(...)`) is fine.
- **The real stack, faked only at the edge:** the real Automerge-backed source, view model, screens and
  navigation between them. Fake only what is outside the app (the x0x mesh, the camera, the clock).
- **Persistence and sync where the feature has them:** at least one scenario reloads from the saved bytes (or syncs
  to a second device on the fake mesh) and checks the feature still holds.

## Where they live

`shared/src/jvmTest/kotlin/uk/veritaslabs/vesta/acceptance/<Feature>AcceptanceTest.kt`, one class per feature, with
a KDoc naming the feature and listing its acceptance criteria. They run on the JVM with Compose's `runComposeUiTest`;
set `Dispatchers.setMain(Dispatchers.Default)` in `@BeforeTest` when a view model is involved (see
`DevicePairingFlowTest`).

The class has a small harness composable that wires the screens the way `App.kt` does. Reuse `App.kt`'s `internal`
mappers rather than copying them, so the test follows the app.

## Example

`ShoppingListTagsAcceptanceTest` (shopping-list tags) is the reference.
