import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

val vestaVersion = rootProject.extra["vestaVersion"] as String
val vestaBuildCode = rootProject.extra["vestaBuildCode"] as Int
val (vestaMajor, vestaMinor) = vestaVersion.split('.', '-').take(2).map { it.toInt() }
// patch and pre-release stage folded together, 0-9999
val vestaPatchAndStage = vestaBuildCode % 10_000

compose.desktop {
    application {
        mainClass = "uk.veritaslabs.vesta.MainKt"
        jvmArgs += listOf("-Dvesta.version=$vestaVersion", "-Dvesta.build=$vestaBuildCode")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "uk.veritaslabs.vesta"
            packageVersion = vestaVersion
            // MSI versions are numeric MAJOR.MINOR.BUILD (BUILD up to 65535), so the patch and the pre-release stage fold
            // into BUILD: 0.1.0-alpha.1 -> 0.1.1, 0.1.0 -> 0.1.99, 0.1.1 -> 0.1.199. Still orders correctly.
            windows { msiPackageVersion = "$vestaMajor.$vestaMinor.$vestaPatchAndStage" }
            // jpackage on macOS rejects a major version of 0 and any suffix, so 0.x disk images are labelled 1.minor.build.
            macOS { dmgPackageVersion = "${maxOf(vestaMajor, 1)}.$vestaMinor.$vestaPatchAndStage" }
            // Brand asset under /branding (see branding/README.md and TRADEMARK.md).
            linux {
                // No dash allowed in a deb/rpm version; "~" spells a pre-release there (sorts before the final release).
                packageVersion = vestaVersion.replace('-', '~')
                iconFile.set(rootProject.file("branding/app-icon.png"))
            }
        }
    }
}