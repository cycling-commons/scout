plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Reuse Android Scout domain sources with a JVM 17 toolchain (Karoo Kotlin/AGP stack).
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("../../Android/domain/src/main/java"))
    }
    named("test") {
        java.setSrcDirs(listOf("../../Android/domain/src/test/java"))
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
