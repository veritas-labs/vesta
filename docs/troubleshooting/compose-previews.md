# Troubleshooting Compose `@Preview` rendering

Every `@Composable` screen in `shared/src/commonMain` carries a light/dark × phone/tablet preview
matrix. Those previews render in a pipeline with several moving parts that fail in ways the error
messages describe badly — three separate root causes hit this project on 2026-09-28/29, and two of
them produced misleading evidence that sent the first diagnosis in the wrong direction entirely.

This is the record of what each failure actually was, and the method that eventually found them.

## How the preview pipeline fits together

The IDE here is **IntelliJ IDEA**, not Android Studio, but the Compose preview is still the Android
plugin's renderer (`com.android.tools.idea.compose.preview`) driving **layoutlib** in-process. That
matters for reading stack traces:

| Frame prefix | Comes from |
|---|---|
| `com.android.tools.rendering.*`, `com.android.layoutlib.*` | the IDE's renderer |
| `_layoutlib_._internal_.*` | libraries repackaged *inside* layoutlib (kotlinx-coroutines, kotlin stdlib) |
| `androidx.compose.*`, `org.jetbrains.compose.*` | **this project's own Gradle dependencies** |

So a bug in `androidx.compose.ui.tooling.data` is a bug in a library we resolve and can version —
not in the IDE. Anything under `_layoutlib_._internal_` belongs to the IDE's bundled copy.

Resources are a separate path again. Compose Multiplatform resources are packaged as **Android
assets** under `composeResources/<resource package>/`, and the generated accessors hold that full
path as a compile-time constant:

```kotlin
// shared/build/generated/compose/resourceGenerator/kotlin/.../Drawable0.commonMain.kt
private const val MD: String = "composeResources/vesta.shared.generated.resources/"
ResourceItem(setOf(), "${MD}drawable/img_armchair.xml", -1, -1)
```

`painterResource`/`vectorResource` pass `ResourceItem.path` to the reader **verbatim** — nothing
prepends or rewrites it at read time. (`Res.getUri` and `Res.readBytes` are different: they take a
path *relative* to the resource package and prepend the prefix themselves.)

## Failure 1 — stale Fast Preview overlay classes in `/tmp`

**Symptom**

```
org.jetbrains.compose.resources.MissingResourceException: Missing resource with path: drawable/img_armchair.xml
  at org.jetbrains.compose.resources.DefaultAndroidResourceReader.throwMissingResourceException(ResourceReader.android.kt:93)
  at org.jetbrains.compose.resources.DefaultAndroidResourceReader.getResourceAsStream(ResourceReader.android.kt:80)
  at org.jetbrains.compose.resources.DefaultAndroidResourceReader.read(ResourceReader.android.kt:27)
```

Survives `./gradlew clean`, a full rebuild, File → Invalidate Caches, and an IDE restart.

**Cause**

IntelliJ's **Fast Preview** compiles Kotlin in-process and writes the classes to a scratch directory
`/tmp/overlay<digits>/`, which the render classloader prepends **ahead of** the Gradle output. Those
directories are never cleaned by Gradle or by Invalidate Caches, so a class compiled weeks ago keeps
shadowing the real one indefinitely.

Compose 1.12.0 changed the generated resource paths (bare → `composeResources/<package>/`-prefixed,
and `values/strings.xml` → `values/strings.commonMain.cvr`). An overlay class compiled under 1.11.1
therefore asks for a path that no longer exists:

```
/tmp/overlay13788669325430999457/.../Drawable0_commonMainKt$img_armchair$2.class   (Sep 28 20:49)
  -> "drawable/img_armchair.xml"                                         # stale, 1.11.1-era

shared/build/classes/kotlin/android/main/.../Drawable0_commonMainKt.class
  -> "composeResources/vesta.shared.generated.resources/drawable/img_armchair.xml" # correct
```

**Fix** — quit the IDE (it holds the directories open), then `rm -rf /tmp/overlay*` and restart.
They are scratch directories; the IDE recreates them.

**Prevention** — leave Fast Preview **off** and use the preview's *Build & Refresh*. Fast Preview
recompiles Kotlin without re-running the Gradle `prepareComposeResources*` /
`copyAndroidMainComposeResourcesToAndroidAssets` tasks, so it routinely desynchronises code from
resources; this failure and the `values/strings.xml` one below both came out of the same session.

**The decisive tell.** `MissingResourceException` is
`Exception("Missing resource with path: $path")` — it echoes the requested path with no trimming. So
compare the path in the message against the `MD` constant in the generated accessor:

- **prefixed** (`composeResources/…/drawable/x.xml`) → the code is current; the asset really is
  unreachable. Go to Failure 2.
- **bare** (`drawable/x.xml`) → current bytecode *cannot* produce this. Something is serving stale
  classes. Go hunting in `/tmp/overlay*`.

## Failure 2 — the Gradle build is failing, so resources never regenerate

**Symptom** — `MissingResourceException` with a correctly **prefixed** path, or
`NoSuchMethodError` on a generated accessor such as
`String0_commonMainKt.getPantry_title(Res$string)`, or
`NoSuchMethodException: Composable …Preview not found`.

**Cause** — the generated compose-resources on the preview classpath are stale or absent. Either
Fast Preview skipped the Gradle resource tasks, or — the case on 2026-09-29 — **the build was
failing outright** and nothing regenerated at all. Compose 1.12.x's Android artifacts declare
`minCompileSdk 37` in their AAR metadata, and the project was still on 36:

```
> Task :shared:checkAndroidMainAarMetadata FAILED
  16 dependencies require compileSdk 37 — :shared is currently compiled against android-36
```

**Fix** — make `./gradlew :shared:assemble` actually succeed before reading anything into a resource
error. Then verify the asset and the requested path line up:

```bash
# what the compiled code asks for
strings shared/build/classes/kotlin/android/main/vesta/shared/generated/resources/Drawable0_commonMainKt.class \
  | grep 'img_armchair'

# what is actually packaged
find shared/build/intermediates/assets/androidMain/mergeAndroidMainAssets -name 'img_armchair*'
```

## Failure 3 — `CompositionDataTree.mapTree` NPE (upstream, fixed in Compose 1.12.1)

**Symptom**

```
java.lang.NullPointerException
  at androidx.compose.ui.tooling.data.CompositionDataTree.mapTree(CompositionDataTree.kt:99)
  at androidx.compose.ui.tooling.data.CompositionDataTree.build(CompositionDataTree.kt:78)
  at androidx.compose.ui.tooling.ComposeViewAdapter.processViewInfos(ComposeViewAdapter.android.kt:265)
```

**Cause** — a bug in `androidx.compose.ui:ui-tooling-data`, present in 1.11.2 and 1.12.0:

```kotlin
children.filter { it in processedNodes }                        // key-presence test...
        .groupByTo(childrenToAdd, { it.findContextGroup()!! }, { processedNodes[it]!! })
```

`processedNodes` is `MutableMap<CompositionInstance, R?>` — **nullable values** — so the filter
excludes nothing (every child is stored, `null` included) and `processedNodes[it]!!` throws as soon
as any nested sub-composition maps to `null`. That happens whenever a sub-composition's slot table
comes back empty (`SlotTree.mapTreeWithStitching` returns `null`), which depends on
subcompose/reuse state rather than on anything a screen controls.

**Fix** — Compose Multiplatform **1.12.1** or later. There is no workaround by pinning:
1.12.0 is byte-identical here, and downgrading `ui-tooling-data` alone breaks `ComposeViewAdapter`,
which needs its matching `makeTree` overload.

**Why it was hard to diagnose.** It fires only when a nested sub-composition is transiently empty,
so it looks arbitrary: it appeared with no repo change (an IDE update shifted subcompose timing),
and it broke `BoxWithConstraints` → `LazyVerticalGrid` (the pickers) while leaving
`BoxWithConstraints` → `LazyColumn` (PantryScreen) working. That asymmetry invited the wrong
conclusion that nesting a `Lazy*` inside `BoxWithConstraints` was inherently unsafe. It is not —
`BoxWithConstraints` + `maxWidth` is the right idiom for the tablet breakpoint, and is preferable to
`LocalWindowInfo.current.containerSize` because it measures the composable's own slot rather than
the whole window.

> The `Box` + `LocalWindowInfo` workaround this bug prompted was removed on 2026-09-29 once 1.12.1
> landed. All seven screens now use `BoxWithConstraints` + `maxWidth` — do not reintroduce the
> workaround without first confirming against this section that the NPE is genuinely back.

## Version constraints these uncovered

Both are recorded in `gradle/libs.versions.toml`; repeated here because they fail confusingly.

- **`android-compileSdk` must be ≥ 37** for Compose 1.12.x. Fails at
  `:shared:checkAndroidMainAarMetadata`. `targetSdk` is independent and stays at 36.
- **`material3` must track `composeMultiplatform`'s major/minor.** material3 reaches into foundation
  internals, so a skew fails at *runtime*, not compile time. Leaving material3 on `1.11.0-alpha07`
  under Compose 1.12.1 made every `OutlinedTextField` test fail with
  `IllegalStateException: Must run runDetachLifecycle() once after runAttachLifecycle() and before markAsDetached()`
  from `androidx.compose.ui.Modifier$Node`. Note material3 is published separately from Compose and
  only as alphas past 1.9 — there is no `material3:1.12.1`; the 1.12 line tops out at
  `1.12.0-alpha03`.

## Method: how to troubleshoot the next one

**1. Get the stack trace from the preview panel, not the log.** Preview errors do *not* all reach
`~/.cache/JetBrains/IntelliJIdea<version>/log/idea.log`. Absence from the log is **not** evidence
that nothing failed — assuming otherwise is what produced the first wrong diagnosis here. The log is
still worth grepping for `ComposePreviewRepresentation`, `LayoutlibSceneRenderer` and
`FastPreviewManager` to see render/compile timing, but the panel is the source of truth.

**2. Read the actual library source.** Sources jars are already in the Gradle cache:

```bash
find ~/.gradle/caches/modules-2 -name '*ui-tooling-data*sources.jar'
find ~/.gradle/caches/modules-2 -name '*components-resources-android*sources.jar'
```

Unzip and read the exact line the trace names. Reasoning from memory about library internals is how
`CompositionDataTree` got wrongly written off as a non-existent class when it was real.

**3. Check the build is green before blaming the preview.** `./gradlew :shared:assemble`.

**4. Compare what the code asks for against what is on disk.** For resources, the `strings` +
`find` pair under Failure 2. If they match and it still fails, suspect stale bytecode.

**5. Hunt stale bytecode outside the project.** Gradle output is not the only classpath entry:

```bash
find /tmp/overlay* -name '<TheClass>*.class' 2>/dev/null
find . -name '*.class' -exec grep -al '<some constant>' {} +   # note: -a, these are binary
find . \( -name '*.jar' -o -name '*.aar' \) | while read j; do
  unzip -p "$j" '*<TheClass>*.class' 2>/dev/null | strings | grep '<some constant>'
done
```

**6. If the runtime disagrees with the disk, make the preview tell you.** A throwaway `@Composable`
in `commonMain` with its own `@Preview` can print what the preview's own classloader sees. For
resources:

```kotlin
@Composable
private fun ResourceProbe() {
    // Required for non-composable Res.getUri/readBytes: the Android context they use is a static
    // set either by AndroidContextProvider at app startup (which never runs in a preview) or by
    // this effect. painterResource gets it free via LocalResourceReader.currentOrPreview.
    PreviewContextConfigurationEffect()
    Text(runCatching { Res.getUri("drawable/img_armchair.xml") }.getOrElse { "FAIL: ${it.message}" })
}
```

Omitting `PreviewContextConfigurationEffect()` yields a misleading
`Android context is not initialized` on every path — that suffix means the probe is wrong, not the
resource. Delete the probe once it has answered the question.

## Quick reference

| Symptom | Likely cause | Action |
|---|---|---|
| `MissingResourceException`, **bare** path | stale Fast Preview overlay | `rm -rf /tmp/overlay*`, restart IDE |
| `MissingResourceException`, **prefixed** path | build failing / resources not regenerated | fix `./gradlew :shared:assemble` |
| `NoSuchMethodError` on a `Res.*` accessor | Fast Preview desynced from resource tasks | disable Fast Preview, Build & Refresh |
| `NPE` in `CompositionDataTree.mapTree` | upstream ui-tooling-data bug | Compose ≥ 1.12.1 |
| `checkAndroidMainAarMetadata` FAILED | `compileSdk` below a dependency's `minCompileSdk` | raise `android-compileSdk` |
| `Must run runDetachLifecycle()…` in tests | material3 / Compose version skew | align `material3` with `composeMultiplatform` |
