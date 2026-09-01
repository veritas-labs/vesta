import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// --- x0x embedded daemon (mobile only; desktop talks to the x0xd REST API) --
// The Rust crate ffi/x0x-ffi embeds x0x::server behind a C ABI. Android loads
// it via JNI (see src/androidMain/.../x0x/EmbeddedX0x.android.kt, the .so is
// staged into androidApp/src/main/jniLibs by scripts/build-android-ffi.sh);
// iOS links the static library through the cinterop below.
val x0xFfiDir = rootProject.projectDir.resolve("ffi/x0x-ffi")
val x0xIosDistDir = x0xFfiDir.resolve("dist/ios")
val x0xIosDefTemplate = projectDir.resolve("native/x0xffi.def")

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
        // The static libx0x_ffi.a is linked into the final app by Xcode
        // (OTHER_LDFLAGS[sdk=…] in the iosApp project + the "Build x0x FFI"
        // script phase). Gradle only generates the bindings from the header,
        // so iOS framework/test links work on any host — unreferenced FFI
        // code is elided at link time, and the app's final link (on the Mac)
        // pulls in the archive.
        iosTarget.compilations.getByName("main") {
            val generatedDef = layout.buildDirectory
                .file("cinterop/${iosTarget.name}/x0xffi.def").get().asFile
            generatedDef.parentFile.mkdirs()
            generatedDef.writeText(
                buildString {
                    appendLine(x0xIosDefTemplate.readText().trimEnd())
                    appendLine("compilerOpts = -I${x0xFfiDir.resolve("include").absolutePath}")
                }
            )
            cinterops.create("x0xffi") {
                defFile(generatedDef)
            }
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
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}