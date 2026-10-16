import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Builds the x0x embedded-daemon shared library (libx0x_ffi.so) for Android
// and stages it into src/main/jniLibs (AGP's default jniLibs source set).
// Cargo is incremental, so this is a fast no-op when nothing changed.
val buildX0xAndroidFfi by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the x0x FFI shared libraries for Android (Rust)."
    workingDir = rootProject.projectDir
    commandLine(rootProject.projectDir.resolve("scripts/build-android-ffi.sh").absolutePath)
    inputs.file(rootProject.projectDir.resolve("scripts/build-android-ffi.sh"))
    inputs.file(rootProject.projectDir.resolve("ffi/x0x-ffi/Cargo.toml"))
    inputs.file(rootProject.projectDir.resolve("ffi/x0x-ffi/Cargo.lock"))
    inputs.dir(rootProject.projectDir.resolve("ffi/x0x-ffi/src"))
    inputs.dir(rootProject.projectDir.resolve("ffi/x0x-ffi/include"))
    outputs.dir(projectDir.resolve("src/main/jniLibs"))
}

tasks.named("preBuild") {
    dependsOn(buildX0xAndroidFfi)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "uk.veritaslabs.vesta"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "uk.veritaslabs.vesta"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = rootProject.extra["vestaBuildCode"] as Int
        versionName = rootProject.extra["vestaVersion"] as String
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Release signing reads secrets/keystore.properties (git-ignored) with the keys
    // storeFile (relative to secrets/), storePassword, keyAlias and keyPassword.
    // Without that file the release build is left unsigned, so contributors and
    // debug builds are unaffected.
    val secretsDir = rootProject.projectDir.resolve("secrets")
    val keystoreProperties = secretsDir.resolve("keystore.properties")
        .takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
    signingConfigs {
        keystoreProperties?.let { props ->
            fun required(key: String) = props.getProperty(key)
                ?: error("secrets/keystore.properties is missing '$key'")
            create("release") {
                storeFile = secretsDir.resolve(required("storeFile"))
                storePassword = required("storePassword")
                keyAlias = required("keyAlias")
                keyPassword = required("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // The launcher icons are brand assets, kept apart from the Apache-licensed
    // code under /branding (see branding/README.md and TRADEMARK.md).
    sourceSets.getByName("main").res.srcDir(rootProject.projectDir.resolve("branding/android/res"))
}