import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// --- x0x embedded daemon (mobile only; desktop talks to the x0xd REST API) --
// The Rust crate ffi/x0x-ffi embeds x0x::server behind a C ABI. Android loads
// it via JNI (EmbeddedX0x.android.kt and X0xBridge, now in the x0x kit; the .so is
// staged into androidApp/src/main/jniLibs by scripts/build-android-ffi.sh);
// iOS links the static library through the cinterop bindings that live in the x0x kit
// (vesta-kits, x0x-kit/native); Xcode links the archive, so nothing here binds it any more.
// Native packaging moves to the kit in extraction plan WP26.
val x0xFfiDir = rootProject.projectDir.resolve("ffi/x0x-ffi")
val x0xIosDistDir = x0xFfiDir.resolve("dist/ios")

// Builds libx0x_ffi.a for iOS. Requires macOS (the C parts of the x0x
// dependency tree — aws-lc-sys/ring/sqlite — need Apple's SDK); the Xcode
// "Build x0x FFI" phase runs it automatically on the Mac. Cargo is
// incremental, so this is a fast no-op when nothing changed.
val buildX0xIosFfi by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the x0x FFI static libraries for iOS (Rust)."
    workingDir = rootProject.projectDir
    commandLine(rootProject.projectDir.resolve("scripts/build-ios-ffi.sh").absolutePath)
    inputs.file(rootProject.projectDir.resolve("scripts/build-ios-ffi.sh"))
    inputs.file(x0xFfiDir.resolve("Cargo.toml"))
    inputs.file(x0xFfiDir.resolve("Cargo.lock"))
    inputs.dir(x0xFfiDir.resolve("src"))
    inputs.dir(x0xFfiDir.resolve("include"))
    outputs.dir(x0xIosDistDir)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    android {
        namespace = "uk.veritaslabs.vesta.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        // The Automerge-backed code is shared by the two JVM-based targets, which have no common
        // intermediate source set in the default hierarchy — so both compile this directory directly.
        androidMain.get().kotlin.srcDir("src/jvmAndroidMain/kotlin")
        jvmMain.get().kotlin.srcDir("src/jvmAndroidMain/kotlin")
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.kotlinx.coroutinesAndroid)
            // Automerge CRDT (JNI bindings, see the libs.versions.toml comment);
            // androidnative adds the Android .so — the main jar's bundled
            // natives are desktop-only (win/mac/linux).
            implementation(libs.automerge)
            implementation(libs.automerge.androidnative)
            // Barcode scanner: CameraX preview + ML Kit analysis, permission via activity-compose.
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.mlkit.barcodeScanning)
        }
        commonMain.dependencies {
            // The x0x client, extracted to vesta-kits. api: Vesta's own public types expose its types.
            api(libs.kits.x0x)
            // The database (KitsDatabase, its schema and migrations, the open path), extracted to vesta-kits. api: the
            // generated queries expose SQLDelight's runtime types to Vesta's own code.
            api(libs.kits.store)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.qrose)
            // Lottie playback for the "added to Pantry" celebration.
            implementation(libs.compottie)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.compose.uiTest)
            implementation(libs.kits.x0xTesting)
            implementation(libs.kits.storeTesting)
        }
        jvmMain.dependencies {
            // Automerge CRDT (JNI bindings, see the libs.versions.toml comment);
            // the jar bundles natives for desktop (win/mac/linux, x64+arm64).
            implementation(libs.automerge)
            // Desktop barcode scanner: webcam frames decoded with ZXing.
            implementation(libs.webcam.capture)
            implementation(libs.zxing.core)
        }
        jvmTest.dependencies {
            // Skiko's native renderer, needed for runComposeUiTest on the jvm
            // target; desktopApp pulls this in for the real app, but shared's
            // own jvmTest doesn't otherwise depend on it.
            implementation(compose.desktop.currentOs)
            // Gradle 9 no longer auto-provisions this; required to run any
            // JUnit Platform engine via useJUnitPlatform().
            runtimeOnly(libs.junitPlatform.launcher)
            // kotlin-test resolves to kotlin-test-junit (JUnit4) on the jvm
            // target today (see gradle/libs.versions.toml junit = "4.13.2").
            // The vintage engine lets those existing tests keep running
            // alongside JUnit Platform engines under one test task.
            runtimeOnly(libs.junitVintage.engine)
            implementation(libs.kotlinx.coroutinesTest)
            // The compatibility tests open database files with the bundled driver themselves.
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.eygraber.sqldelightAndroidxDriver)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Switches jvmTest to the JUnit Platform launcher (kotlin-test-junit tests run
// via junit-vintage-engine). Deferred to afterEvaluate: the Kotlin
// Multiplatform plugin sets this task's test framework to plain JUnit4 in its
// own afterEvaluate hook, which would otherwise run after — and silently
// revert — a top-level call here.
afterEvaluate {
    tasks.named<Test>("jvmTest") {
        useJUnitPlatform()
    }
}

// commonTest's runComposeUiTest-based tests aren't a supported combination on
// Android's local/host JUnit runner — JetBrains' own Compose Multiplatform
// docs confirm this (https://kotlinlang.org/docs/multiplatform/compose-test.html),
// and without Robolectric wired up as the JUnit runner they crash with an NPE
// from Build.FINGERPRINT being null. They already run for real on jvmTest
// (see the jvmTest dependency above) and would run on a device/emulator via
// testAndroidDeviceTest, so just skip them for the host-side JVM run.
tasks.matching { it.name == "testAndroidHostTest" }.configureEach {
    (this as Test).exclude("**/ui/components/**", "**/ui/screens/**")
}

// The Kotlin Multiplatform plugin doesn't create a plain `test` lifecycle
// task the way the Android/JVM plugins do for androidApp/desktopApp, so
// root-level `./gradlew test` silently skipped this module's actual test
// suites. Wire one up to the host-side (no device/simulator needed) test
// tasks; iOS tests still run separately via Xcode/iosSimulatorArm64Test.
tasks.register("test") {
    group = "verification"
    description = "Runs the JVM and Android host-side test suites."
    dependsOn("jvmTest", "testAndroidHostTest")
}