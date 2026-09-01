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
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
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
}