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