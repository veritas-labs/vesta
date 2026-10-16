import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.JsonReportRenderer

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.licenseReport)
}

// The app version comes from gradle.properties; the build code is derived from it so Android's
// versionCode never needs a hand-kept counter and always orders releases correctly:
//   code = (MAJOR * 10000 + MINOR * 100 + PATCH) * 100 + stage
// where stage is N for -alpha.N, 25 + N for -beta.N, 50 + N for -rc.N (N 1-24) and 99 for a final release.
// So 0.1.0-alpha.1 -> 101, 0.1.0-beta.2 -> 127, 0.1.0 -> 199, 1.2.3 -> 1020399.
val vestaVersion = providers.gradleProperty("vestaVersion").get()
val vestaBuildCode = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{1,2})(?:-(alpha|beta|rc)\.(\d{1,2}))?""").matchEntire(vestaVersion)
    ?.destructured
    ?.let { (major, minor, patch, stage, number) ->
        val prerelease = number.toIntOrNull()?.takeIf { it in 1..24 }
        val stageCode = when {
            stage.isEmpty() -> 99
            prerelease == null -> null
            else -> prerelease + mapOf("alpha" to 0, "beta" to 25, "rc" to 50).getValue(stage)
        }
        stageCode?.let { (major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()) * 100 + it }
    }
    ?: error("vestaVersion must be MAJOR.MINOR.PATCH (each 0-99), optionally -alpha.N, -beta.N or -rc.N (N 1-24), was '$vestaVersion'")
extra["vestaVersion"] = vestaVersion
extra["vestaBuildCode"] = vestaBuildCode

// The third-party licence list shown under Settings > Open source licences.
// `./gradlew generateLicenses` rewrites the committed
// shared/src/commonMain/composeResources/files/licenses.json from what the
// shipped apps actually resolve (Gradle dependencies via the report below, the
// embedded x0x daemon's Rust crates via `cargo metadata`); rerun it whenever
// a dependency changes. It needs network access for the Cargo part.
val licenseReportDir = layout.buildDirectory.dir("reports/licenses").get().asFile.path

// The renderers and filters read the extension off every project they report
// on, and look for the extracted licence files in its outputDir.
subprojects {
    apply(plugin = "com.github.jk1.dependency-license-report")
    extensions.configure<LicenseReportExtension> { outputDir = licenseReportDir }
}

licenseReport {
    projects = arrayOf(project(":shared"), project(":androidApp"), project(":desktopApp"))
    // Only what ends up in the apps: no compile-only, test or tooling classpaths.
    configurations = arrayOf("jvmRuntimeClasspath", "androidRuntimeClasspath", "releaseRuntimeClasspath", "runtimeClasspath")
    excludeOwnGroup = true
    // The kits (veritas-kits, Apache-2.0, ours) are not third parties; their own third-party dependencies
    // (SQLDelight, Automerge, ...) still arrive transitively and stay on the list.
    excludeGroups = arrayOf("uk.veritaslabs.kits")
    outputDir = licenseReportDir
    filters = arrayOf(LicenseBundleNormalizer())
    renderers = arrayOf(JsonReportRenderer("licenses.json", false))
}

// By default Gradle only writes to stdout when a test fails, so a green
// `./gradlew test` run looks silent. Log every finished test, on every Test
// task in every subproject, so the results are always visible.
allprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            events("passed", "skipped", "failed")
        }
        // Without this, an unchanged Test task is skipped as UP-TO-DATE on a
        // rerun and nothing above ever fires — force it to actually execute,
        // and therefore print, every time.
        outputs.upToDateWhen { false }
    }
}

// --- Merged licence list for the app ----------------------------------------

val rustFfiDir = layout.projectDirectory.dir("ffi/x0x-ffi")
// Rust only ships inside the mobile apps (desktop talks to a standalone x0xd).
val rustTargets = listOf("aarch64-linux-android", "aarch64-apple-ios")
val licensesResource = layout.projectDirectory.file("shared/src/commonMain/composeResources/files/licenses.json")
// Kotlin Multiplatform publishes one artifact per target (foo, foo-jvm, foo-android...); list the library once.
val targetSuffix = Regex("-(jvm|android|desktop|awt)$")
val UNDECLARED_LICENSE = "Not declared"

fun gradleLicenses(): List<Map<String, Any>> {
    @Suppress("UNCHECKED_CAST")
    val report = groovy.json.JsonSlurper().parse(file("$licenseReportDir/licenses.json")) as Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    return (report["dependencies"] as List<Map<String, Any?>>).map { dependency ->
        val licenses = (dependency["moduleLicenses"] as List<Map<String, Any?>>).mapNotNull { it["moduleLicense"] as? String }
        val url = (dependency["moduleUrls"] as List<String>).firstOrNull()
        mapOf(
            "name" to (dependency["moduleName"] as String).let { name ->
                name.replace(targetSuffix, "")
            },
            "version" to (dependency["moduleVersion"] as String),
            "licenses" to licenses.distinct().ifEmpty { listOf(UNDECLARED_LICENSE) },
            "url" to (url ?: ""),
        )
    }
}

fun rustLicenses(): List<Map<String, Any>> {
    val crates = mutableMapOf<String, Map<String, Any>>()
    for (target in rustTargets) {
        val output = providers.exec {
            workingDir = rustFfiDir.asFile
            commandLine("cargo", "metadata", "--locked", "--format-version", "1", "--filter-platform", target)
        }.standardOutput.asText.get()
        @Suppress("UNCHECKED_CAST")
        val metadata = groovy.json.JsonSlurper().parseText(output) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val packages = (metadata["packages"] as List<Map<String, Any?>>).associateBy { it["id"] as String }
        @Suppress("UNCHECKED_CAST")
        val nodes = ((metadata["resolve"] as Map<String, Any?>)["nodes"] as List<Map<String, Any?>>).associateBy { it["id"] as String }
        // Walk the normal (non-dev, non-build) dependencies from the FFI crate: what actually ships.
        @Suppress("UNCHECKED_CAST")
        val root = (metadata["resolve"] as Map<String, Any?>)["root"] as String
        val pending = ArrayDeque(listOf(root))
        val seen = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!seen.add(id)) continue
            @Suppress("UNCHECKED_CAST")
            (nodes.getValue(id)["deps"] as List<Map<String, Any?>>)
                .filter { dep -> (dep["dep_kinds"] as List<Map<String, Any?>>).any { it["kind"] == null } }
                .forEach { pending.add(it["pkg"] as String) }
        }
        (seen - root).map { packages.getValue(it) }.forEach { pkg ->
            val name = pkg["name"] as String
            val version = pkg["version"] as String
            val license = (pkg["license"] as? String) ?: UNDECLARED_LICENSE
            crates["$name:$version"] = mapOf(
                "name" to "crates.io:$name",
                "version" to version,
                "licenses" to listOf(license),
                "url" to ((pkg["repository"] ?: pkg["homepage"]) as? String ?: ""),
            )
        }
    }
    return crates.values.toList()
}

// The report plugin's task holds on to Project objects, which the configuration cache cannot serialise.
tasks.named("generateLicenseReport") {
    notCompatibleWithConfigurationCache("jk1 dependency-license-report keeps Project references")
}

tasks.register("generateLicenses") {
    notCompatibleWithConfigurationCache("reads the licence report and runs cargo at execution time")
    group = "documentation"
    description = "Regenerates the in-app open source licence list (needs network for the Cargo part)."
    dependsOn("generateLicenseReport")
    outputs.file(licensesResource)
    doLast {
        val merged = (gradleLicenses() + rustLicenses())
            .distinctBy { "${it["name"]}:${it["version"]}" }
            .sortedWith(compareBy({ (it["name"] as String).lowercase() }, { it["version"] as String }))
        licensesResource.asFile.parentFile.mkdirs()
        licensesResource.asFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(merged)) + "\n")
        logger.lifecycle("Wrote ${merged.size} entries to ${licensesResource.asFile.relativeTo(projectDir)}")
    }
}
