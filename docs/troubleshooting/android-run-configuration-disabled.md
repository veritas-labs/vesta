# Troubleshooting a disabled Android run configuration

On 2026-09-30 the `androidApp` run configuration had its Run/Debug/Profile buttons **greyed out** in
IntelliJ IDEA. The configuration existed, the Pixel 7a AVD was booted and visible to adb, and
`./gradlew :androidApp:installDebug` worked from the terminal. Rebuilding, `clean`, File → Invalidate
Caches and IDE restarts changed nothing — for about a month.

The run configuration was never the problem. **Every Gradle sync of this project had been failing
since 2026-09-01**, and a greyed-out Android run button is a downstream symptom of that. This is the
record of the causal chain, the evidence that dated it, and the probe technique that separated an
IDE-side failure from a build-side one.

## Why a failed sync disables the button

The Android run configuration is validated before the button is drawn, and a *fatal* validation error
disables it. In `plugins/android/lib/android.jar`, `AndroidRunConfigurationBase.validate` delegates
to the project system, and `GradleProjectSystem.validateRunConfiguration` calls
`GradleApkProvider.doValidate`, which opens with:

```java
// com.android.tools.idea.run.GradleApkProvider#doValidate
if (GradleAndroidModel.get(facet) == null)
    return fatal("The project has not yet been synced with Gradle configuration");
```

`GradleAndroidModel` is attached to the module's Android facet **by a successful sync**. No
successful sync → no model → fatal error → dead button. Nothing in `.idea/` is corrupt, which is
exactly why cache invalidation never helped: each rebuild just re-runs a sync that fails the same
way.

The other fatal checks in the same chain, all of which present identically as a greyed-out button:

| Message | Condition |
|---|---|
| `The project has not yet been synced with Gradle configuration` | `GradleAndroidModel.get(facet) == null` |
| `No Android facet found for <module>` | no `AndroidFacet` on the run config's module |
| `The current project can not be run` | facet `projectType` is not an app/test/feature type |
| `Please select Android SDK` | `AndroidPlatforms.getInstance(module) == null` |
| `Run configuration <name> is not supported in the current project. Cannot obtain the application ID.` | project system returns no `ApplicationIdProvider` |
| `Bundle task not supported for module '<module>'` | only when *Deploy APK from bundle* is on |
| `run.error.apk.not.signed` | selected variant's artifact is unsigned |

Separately, `AndroidRunConfigurationBase.canRunOn` is a one-liner —
`return target instanceof AndroidExecutionTarget` — so a configuration can also be disabled when the
device selector resolves no Android execution target. That was **not** the cause here (adb saw
`Pixel_7a [emulator-5554]` online), but check it before assuming sync.

## Symptom 1 — the sync is failing and says nothing useful

Sync failures of this shape do not announce themselves clearly. In
`~/.cache/JetBrains/IntelliJIdea2026.2/log/idea.log`:

```
WARN - GradleSyncStateHolder - Gradle sync failed in 30 s 701 ms. Unknown cause
WARN - GradleSyncStateHolder - No error message given
java.lang.IllegalStateException: No error message given
    at com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder.syncFailed(GradleSyncStateImpl.kt:262)
...
com.intellij.openapi.externalSystem.model.ExternalSystemPartialResolutionException:      <- empty message
    at org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.resolveProjectInfo(GradleProjectResolver.java:217)
```

`ExternalSystemPartialResolutionException` means resolution *completed* but one or more resolver
extensions threw. Here the exception carried no message and no `Caused by`, so the log is a dead end.

Count them before anything else — one failure is noise, sixty is the answer:

```bash
cd ~/.cache/JetBrains/IntelliJIdea2026.2/log
grep -hE "sync with Gradle for project 'Vesta'|Gradle sync (failed|finished)" idea*.log \
  | sed 's/.*GradleSyncStateHolder - //' | paste - - | grep Vesta | sort | uniq -c
```

Across every retained log there was not one `Gradle sync finished` for Vesta.

> **The real error is in the Build tool window → Sync tab, not in `idea.log`.** Per-model failures are
> reported as build events. Look there first; it would have saved most of this investigation.

## Symptom 2 — the Android half of the import silently stops updating

The tell that it is specifically the *Android* side failing, while Kotlin/Java modules still import:
compare mtimes in the IDE's external-system module cache.

```bash
ls -la ~/.cache/JetBrains/IntelliJIdea2026.2/projects/vesta.*/external_build_system/modules/
```

```
Vesta.androidApp.main.xml      Sep 29 17:01     <- stale
Vesta.androidApp.xml           Sep 29 17:01     <- stale
Vesta.shared.jvmMain.xml       Sep 29 20:26     <- rewritten by later syncs
Vesta.desktopApp.main.xml      Sep 29 19:55     <- rewritten by later syncs
```

The `androidApp` files were frozen at an earlier sync while everything else kept being rewritten.
Corroborating: the sync log walks `INITIAL_PHASE` → `BASE_SCRIPT_MODEL_PHASE` →
`PROJECT_MODEL_PHASE` → `SOURCE_SET_MODEL_PHASE` → `SCRIPT_MODEL_PHASE` → `ADDITIONAL_MODEL_PHASE`,
logs `Project data resolved in 787 ms`, and then fails — with **no Android resolver output anywhere in
the sync at all**.

Also note the facet keeps the *last good* AGP version, which reads as reassuring and is not:

```xml
<!-- external_build_system/modules/Vesta.androidApp.xml -->
<facet type="android-gradle" name="Android-Gradle">
  <option name="LAST_SUCCESSFUL_SYNC_AGP_VERSION" value="9.1.1" />
```

## Cause — Gradle wrapper newer than the Gradle the IDE ships

Commit `eb53cb3` "update gradle wrapper" (**2026-09-01**) moved the wrapper **9.3.1 → 9.7.1**:

```bash
git log -p --pretty='--- %h %ad %s' --date=short -- gradle/wrapper/gradle-wrapper.properties \
  | grep -E '^--- |^\+distributionUrl'
```

IntelliJ IDEA 2026.2.3 (`IU-262.10968.63`) ships Gradle **9.6.0**:

```bash
ls ~/.local/share/JetBrains/Toolbox/apps/intellij-idea/plugins/gradle-plugin/lib/ | grep gradle-api
# gradle-api-9.6.0.jar
```

One Gradle minor ahead of what the IDE knows. The CLI is unaffected — Gradle 9.7.1 is perfectly fine
on its own — so the build stays green while the IDE's model fetch dies at the end of the sync with an
empty exception.

The dating lines up: `.idea/deploymentTargetSelector.xml` records the last device the run
configuration ever successfully targeted as **2026-09-03**, two days after the wrapper bump.

```xml
<DropdownSelection timestamp="2026-09-03T15:28:26.844317967Z">
  <DeviceId pluginId="LocalEmulator" identifier="path=/home/mark/.android/avd/Pixel_7a.avd" />
```

**Fix** — pin the wrapper to a Gradle the IDE ships support for:

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.1-bin.zip
```

Then **Sync Project with Gradle Files**. The buttons re-enable as soon as one sync succeeds; the run
configuration needs no repair.

**Rule to keep** — do not bump the Gradle wrapper past the version bundled with the installed IDE.
Check `plugins/gradle-plugin/lib/gradle-api-*.jar` before bumping, and re-check it after an IDE
update, since a newer IDE is what unblocks a newer wrapper.

## Ruled out: AGP newer than the IDE's "latest known compatible"

Worth writing down because it looks damning and is a red herring. The IDE launches sync with:

```
-Dandroid.studio.latest.known.compatible.agp.version=9.1.0
```

while `gradle/libs.versions.toml` pins `agp = "9.1.1"`. AGP does carry forward-compatibility guards
(`AgpVersionTooNew`, `AgpVersionIncompatible` in `android-gradle.jar`), so this reads like the cause.

It is not. Reproducing with that exact flag, AGP 9.1.1 built every model the IDE asks for and
reported **zero sync issues**. Confirm rather than assume — see the probe below.

## Method: prove whether the failure is build-side or IDE-side

The decisive move is to fetch the sync models yourself over the Gradle Tooling API, using the IDE's
own init scripts and flags. If the models build, the build is innocent and the fault is in the IDE's
resolver chain — which redirects the whole investigation.

**Recover the IDE's exact sync invocation** from the log; it lists every flag:

```bash
grep -o 'Passing command-line to Gradle Tooling API: --init-script.*' \
  ~/.cache/JetBrains/IntelliJIdea2026.2/log/idea.log | tail -1
```

The IDE's generated init scripts survive in `/tmp` and can be reused verbatim
(`/tmp/ijMapper1.gradle` defines the `mapPath()` helper that `sync.studio.tooling*.gradle` needs —
**pass both or the second one fails to apply**):

```bash
ls /tmp/sync.studio.tooling*.gradle /tmp/ijMapper*.gradle
```

**The probe.** A `BuildAction` runs inside the daemon, and the Tooling API only ships the action's
*own* classpath root — so the model interfaces must be unpacked **beside the action's class files**,
not merely on the consumer classpath. Otherwise every lookup returns
`ClassNotFoundException: com.android.builder.model.v2.models.Versions` and looks like a real failure.

```bash
mkdir -p /tmp/probe && cd /tmp/probe

# model interfaces, unpacked next to the action classes
unzip -oq ~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/builder-model/9.1.1/*/builder-model-9.1.1.jar -x 'META-INF/*'
unzip -oq ~/.local/share/JetBrains/IntelliJIdea2026.2/android/lib/android-gradle.jar 'com/android/ide/gradle/model/*'
```

```java
// Probe.java
import org.gradle.tooling.*;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import com.android.builder.model.v2.models.*;
import com.android.builder.model.v2.ide.SyncIssue;
import java.io.File; import java.util.*;

public class Probe {
  public static class Action implements BuildAction<List<String>> {
    public List<String> execute(BuildController c) {
      List<String> out = new ArrayList<>();
      String[] models = {
        "com.android.builder.model.v2.models.Versions",
        "com.android.builder.model.v2.models.BasicAndroidProject",
        "com.android.builder.model.v2.models.AndroidProject",
        "com.android.builder.model.v2.models.AndroidDsl",
        "com.android.ide.gradle.model.GradlePluginModel",
        "com.android.ide.gradle.model.GradlePropertiesModel",
      };
      for (BasicGradleProject p : c.getBuildModel().getProjects()) {
        for (String m : models) {
          try {
            Object r = c.findModel(p, Class.forName(m));
            out.add(p.getPath() + "  " + m.substring(m.lastIndexOf('.') + 1)
                    + " -> " + (r == null ? "null" : "OK"));
          } catch (Throwable t) {
            StringBuilder sb = new StringBuilder();
            for (Throwable e = t; e != null; e = e.getCause())
              sb.append("\n      <- ").append(e.getClass().getName()).append(": ").append(e.getMessage());
            out.add(p.getPath() + "  " + m.substring(m.lastIndexOf('.') + 1) + " -> FAILED" + sb);
          }
        }
        ProjectSyncIssues si = c.findModel(p, ProjectSyncIssues.class);
        if (si != null) for (SyncIssue s : si.getSyncIssues())
          out.add("   ISSUE severity=" + s.getSeverity() + " type=" + s.getType() + " msg=" + s.getMessage());
      }
      return out;
    }
  }

  public static void main(String[] a) throws Exception {
    try (ProjectConnection pc = GradleConnector.newConnector()
             .forProjectDirectory(new File(a[0])).connect()) {
      BuildActionExecuter<List<String>> ex = pc.action(new Action());
      ex.withArguments("--init-script", "/tmp/ijMapper1.gradle",
                       "--init-script", "/tmp/sync.studio.tooling11.gradle",
                       "-Pandroid.injected.build.model.only.versioned=3",
                       "-Pandroid.injected.invoked.from.ide=true",
                       "-Dandroid.studio.latest.known.compatible.agp.version=9.1.0",
                       "-Didea.sync.active=true");
      for (String s : ex.run()) System.out.println(s);
    } catch (Throwable t) {
      System.out.println("TOP-LEVEL FAILURE:");
      for (Throwable e = t; e != null; e = e.getCause())
        System.out.println("  <- " + e.getClass().getName() + ": " + e.getMessage());
    }
  }
}
```

```bash
G=$(echo ~/.gradle/wrapper/dists/gradle-9.6.1-bin/*/gradle-9.6.1/lib)
CP=".:$(ls $G/*.jar | tr '\n' ':')"        # the whole lib dir; the consumer needs more than the API jar
javac -cp "$CP" -d . Probe.java && java -cp "$CP" Probe ~/Development/VeritasLabs/Vesta
```

Result on 2026-09-30, on both Gradle 9.7.1 and 9.6.1:

```
:androidApp  Versions -> OK          (agp=9.1.1, no sync issues)
:androidApp  AndroidProject -> OK
:androidApp  AndroidDsl -> OK
:androidApp  GradlePluginModel -> OK
:shared      Versions -> null        <- expected, see below
```

Two things to read correctly:

- **`null` is not a failure.** `:shared` uses `com.android.kotlin.multiplatform.library`, which
  publishes no v2 Android model; its Android data travels inside the Kotlin MPP model's extras
  (`com.android.kotlin.multiplatform.ide.models.serialization.*`). `null` for `:shared`,
  `:desktopApp` and the root project is correct.
- **`IllegalStateException: parameter required`** on `LegacyAndroidGradlePluginProperties` is also
  correct — it is a parameterised model and needs `findModel(p, type, paramType, action)`.

Everything green here means the build scripts, AGP, the SDK, the facets and the run config XML are
all fine, and the fault is in the IDE.

## Remaining suspects, if the wrapper is not it

Neither was needed on 2026-09-30, but both were live candidates and both are cheap to test:

- **`org.gradle.configuration-cache=true`** in `gradle.properties`, interacting with the IDE's
  `-Didea.parallelModelFetch.enabled=true`. The probe above exercises the configuration cache but not
  parallel model fetch, so it cannot clear this one. Turn it off temporarily and sync.
- **Plugin version skew.** The Kotlin Multiplatform plugin was at `262.10968.109-IJ` while the Kotlin
  plugin and the IDE itself were at `262.10968.63`. Independently-released plugins being ahead is
  normal, but a KMP resolver extension is in the sync chain. Check with:

  ```bash
  grep -hoE 'Plugin to blame: [A-Za-z ]+ version: [0-9A-Za-z.-]+' \
    ~/.cache/JetBrains/IntelliJIdea2026.2/log/idea*.log | sort -u
  ```

  Bundled plugins updated separately from the IDE live in
  `~/.local/share/JetBrains/IntelliJIdea2026.2/<plugin>/`, **not** in the Toolbox install directory —
  which is why `plugins/android/` appears missing from the install and the log carries harmless
  `Unable to sampleData in path .../plugins/android/resources/sampleData` warnings.

## Quick reference

| Symptom | Likely cause | Action |
|---|---|---|
| Android Run/Debug greyed out, CLI build fine | sync failing → no `GradleAndroidModel` | count Vesta syncs in `idea*.log`; read Build → Sync tab |
| `Gradle sync failed … Unknown cause`, empty `ExternalSystemPartialResolutionException` | IDE-side resolver threw with no message | run the Tooling API probe to clear the build, then read the Sync tab |
| `androidApp*.xml` mtimes frozen while other modules update | Android half of the import never completes | same as above |
| Wrapper bumped, then sync broke | wrapper newer than IDE's bundled Gradle | match `plugins/gradle-plugin/lib/gradle-api-*.jar` |
| `ClassNotFoundException` on `builder.model.v2` classes in a probe | model jars not beside the `BuildAction` classes | unpack them into the action's classpath root |
| `Could not find method mapPath()` applying `sync.studio.tooling*.gradle` | missing companion init script | also pass `/tmp/ijMapper*.gradle` |
| `LAST_SUCCESSFUL_SYNC_AGP_VERSION` looks correct | it is the *last good* value, not the current one | ignore it as evidence |
