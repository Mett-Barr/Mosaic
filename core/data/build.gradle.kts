import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

// TMDB's v4 read access token, from local.properties -- which git ignores, and
// which a clean checkout does not have. Absent it is the empty string: the build
// must not fail over it (the assignment requires a checkout to build with one
// command), and the data module reads the blank and wires up a repository that
// asks for nothing. Trimmed because a token pasted into a properties file tends
// to arrive with a space in front of it.
//
// This does put the token in the APK of a build that has one, which is true of
// every client-side key and is not what keeping it out of the repository is for
// (DECISIONS.md 40).
val tmdbToken: String = Properties().apply {
    val local = rootProject.file("local.properties")
    if (local.exists()) local.inputStream().use { load(it) }
}.getProperty("tmdb.token").orEmpty().trim()

android {
    lint { lintConfig = rootProject.file("lint.xml") }

    testOptions.unitTests.isIncludeAndroidResources = true

    namespace = "moozy.mosaic.data"
    // API 37 ships with a minor version (platforms;android-37.0); omitting it
    // makes the tooling resolve a target that does not exist.
    compileSdk = 37
    compileSdkMinor = 0

    // AGP 9 no longer generates BuildConfig unless asked. This module is the only
    // one that needs it: the token belongs where the client that sends it lives,
    // and putting it on :app would hand it to every module through the graph.
    buildFeatures { buildConfig = true }

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "TMDB_TOKEN", "\"$tmdbToken\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time is only available from API 26; minSdk is 24.
        isCoreLibraryDesugaringEnabled = true
    }
}

// Room writes the schema of every version it compiles to this directory, and the
// files are checked in. Version 1 has nothing to migrate from, so the first one is
// unused -- it is the artefact that makes the *first* migration reviewable instead
// of guessed. Without a location Room only warns, and a warning is not a schema.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

kotlin {
    // Pin the compiling JDK rather than only the target bytecode level: the two
    // are different settings, and an unpinned build picks up whatever JDK the IDE
    // happens to run Gradle with.
    jvmToolchain(17)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    // Only so that a Room DAO can be given a Context in a JVM test. No screen
    // is tested with it: those still need a device, and that is written down
    // in the README rather than pretended away.
    kspTest(libs.androidx.room.compiler)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
}
