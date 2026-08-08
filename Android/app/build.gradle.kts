plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

import org.gradle.api.tasks.testing.Test
import java.io.File

val jdkMajor = JavaVersion.current().majorVersion.toInt()

val androidRoot = rootProject.layout.projectDirectory.asFile

fun parseEnvFile(file: File): Map<String, String> =
    file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim().trim('"')
            key to value
        }
        .toMap()

val helpDir = rootProject.layout.projectDirectory.dir("help")

tasks.register<Copy>("prepareHelpContent") {
    val override = helpDir.file("help.json").asFile
    val fallback = helpDir.file("help.example.json").asFile
    from(if (override.exists()) override else fallback)
    into(layout.buildDirectory.dir("generated/help/assets"))
    rename { "help.json" }
}

tasks.register<Copy>("prepareBrandAssets") {
    val brandDir = File(androidRoot.parentFile, "Brand")
    val fallbackDir = File(androidRoot, "brand-fallback")
    into(layout.buildDirectory.dir("generated/brand/res/drawable-nodpi"))

    listOf(
        "instance-logo.webp" to "instance_logo.webp",
    ).forEach { (brandFile, dest) ->
        val override = File(brandDir, brandFile)
        val fallback = File(fallbackDir, brandFile)
        from(if (override.exists()) override else fallback) {
            rename { dest }
        }
    }
}

tasks.register<Copy>("prepareBrandSvg") {
    val brandDir = File(androidRoot.parentFile, "Brand")
    val fallbackDir = File(androidRoot, "brand-fallback")
    val source = File(brandDir, "welcome-logo.svg").takeIf { it.exists() }
        ?: File(fallbackDir, "welcome-logo.svg")
    from(source)
    into(layout.buildDirectory.dir("generated/brand/assets"))
    rename { "welcome-logo.svg" }
}

tasks.register("prepareSplashIcon") {
    val brandDir = File(androidRoot.parentFile, "Brand")
    val fallbackDir = File(androidRoot, "brand-fallback")
    val toolsDir = File(androidRoot, "tools")
    // Prefer Brand/splash-icon.svg (gitignored instance art); else generic
    // brand-fallback placeholders (not Scout / CC marks).
    val svgSource = File(brandDir, "splash-icon.svg").takeIf { it.exists() }
        ?: File(fallbackDir, "splash-icon.svg")
    val fallbackWebp = File(fallbackDir, "splash-icon.webp")
    val outFile = layout.buildDirectory.file("generated/brand/res/drawable-nodpi/splash_icon.webp").get().asFile

    inputs.file(svgSource)
    outputs.file(outFile)

    doLast {
        outFile.parentFile.mkdirs()
        fun run(command: List<String>): Int =
            ProcessBuilder(command)
                .directory(toolsDir)
                .redirectErrorStream(true)
                .start()
                .waitFor()

        val rendered =
            try {
                if (!File(toolsDir, "node_modules/sharp").exists()) {
                    check(run(listOf("npm", "install", "--no-fund", "--no-audit")) == 0)
                }
                run(
                    listOf(
                        "node",
                        "render-splash-icon.mjs",
                        svgSource.absolutePath,
                        outFile.absolutePath,
                    ),
                ) == 0
            } catch (_: Exception) {
                false
            }

        if (!rendered) {
            check(fallbackWebp.exists()) { "Failed to render splash icon and no fallback webp found" }
            fallbackWebp.copyTo(outFile, overwrite = true)
        }
    }
}

tasks.register<Copy>("prepareLegalContent") {
    val override = helpDir.file("legal.json").asFile
    val fallback = helpDir.file("legal.example.json").asFile
    from(if (override.exists()) override else fallback)
    into(layout.buildDirectory.dir("generated/legal/assets"))
    rename { "legal.json" }
}

tasks.register("prepareInstanceConfig") {
    val override = File(androidRoot, ".env.dev.local")
    val fallback = File(androidRoot, ".env.example")
    val outFile = layout.buildDirectory.file("generated/instance/assets/instance.json").get().asFile

    inputs.file(fallback)
    if (override.exists()) {
        inputs.file(override)
    }
    outputs.file(outFile)

    doLast {
        val source = if (override.exists()) override else fallback
        val env = parseEnvFile(source)
        val url = env["SCOUT_INSTANCE_URL"]
            ?: error("SCOUT_INSTANCE_URL missing in ${source.name}")
        val name = env["SCOUT_INSTANCE_NAME"]
            ?: error("SCOUT_INSTANCE_NAME missing in ${source.name}")
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """{"instance_url":"$url","instance_name":"$name"}""",
        )
    }
}

android {
    namespace = "org.cyclingcommons.scout"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.cyclingcommons.scout"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.toVersion(jdkMajor)
        targetCompatibility = JavaVersion.toVersion(jdkMajor)
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            assets.srcDir("build/generated/help/assets")
            assets.srcDir("build/generated/legal/assets")
            assets.srcDir("build/generated/instance/assets")
            assets.srcDir("build/generated/brand/assets")
            res.srcDir("build/generated/brand/res")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jdkMajor.toString()))
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":fit"))
    implementation(files("libs/android_antlib_4-16-0.aar"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    implementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.named("preBuild") {
    dependsOn(
        "prepareHelpContent",
        "prepareLegalContent",
        "prepareInstanceConfig",
        "prepareBrandAssets",
        "prepareBrandSvg",
        "prepareSplashIcon",
    )
}
