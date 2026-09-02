import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

android {
    lint { lintConfig = rootProject.file("lint.xml") }

    testOptions.unitTests.isIncludeAndroidResources = true

    namespace = "moozy.mosaic.data"
    // API 37 ships with a minor version (platforms;android-37.0); omitting it
    // makes the tooling resolve a target that does not exist.
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig { minSdk = 24 }

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
