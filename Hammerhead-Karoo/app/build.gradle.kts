plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.cyclingcommons.scout.karoo"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.cyclingcommons.scout.karoo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // Release key from the environment (CI: .github/workflows/karoo-release.yml).
        System.getenv("KAROO_KEYSTORE_FILE")?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("KAROO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KAROO_KEY_ALIAS")
                keyPassword = System.getenv("KAROO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Without a release key (local builds) fall back to the debug key so
            // it installs over a debug build (docs/PUBLISHING.md).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        // lintVital crashes analysing the composite karoo-ext build (its AGP/lint
        // version differs from ours). Run `:app:lintDebug` for real lint checks.
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":domain"))
    implementation("io.hammerhead:karoo-ext:1.1.9")
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    // Preview tooling only in debug: it adds an exported PreviewActivity.
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation("junit:junit:4.13.2")
}
