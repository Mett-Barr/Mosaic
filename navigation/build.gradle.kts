import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    // Nav3 restores a back stack by serialising its keys, so this module is the
    // one that needs the serialization plugin -- :app no longer declares a key.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

composeCompiler {
    // External types such as java.time cannot annotate their own stability;
    // they are declared once in the root configuration file.
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

android {
    lint { lintConfig = rootProject.file("lint.xml") }

    namespace = "moozy.mosaic.navigation"
    compileSdk = 37
    compileSdkMinor = 0
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { compose = true }
}

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
    // Slack's Compose lint rules. Android's own lint says almost nothing about
    // Compose conventions -- parameter order, modifier handling, what a
    // composable may return -- and those are the ones easy to get wrong here.
    lintChecks(libs.compose.lint.checks)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // implementation, not api. This module is the only one that should be able
    // to reach a feature; api would hand that reach to :app as well, and then
    // the rule saying :app must not depend on a feature would only be checking
    // that :app does not say so out loud. Hilt still finds the three
    // @HiltViewModel classes: its aggregating task reads the runtime classpath,
    // which these are on either way, and the three screens were opened on a
    // device to confirm it rather than inferred. See DECISIONS.md 31.
    implementation(project(":feature:feed"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:saved"))
    implementation(project(":core:domain"))

    // The scopes a shared element needs are handed to the screens through a
    // CompositionLocal that lives in :core:ui, and this is the module that fills
    // it -- it is the only one that can, because it is the only one holding the
    // SharedTransitionLayout and the NavDisplay underneath it. See DECISIONS.md 32.
    implementation(project(":core:ui"))

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)

    // Navigation 3. Back stack restoration goes through kotlinx.serialization,
    // so every NavKey has to be @Serializable.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)

    // The first tests this module has. What they cover is not a screen -- those
    // still need a device (DECISIONS.md 20) -- but the reading of the back stack
    // that decides whether the bar at the bottom is on it, which is plain Kotlin
    // over three keys and needs no Android to answer.
    testImplementation(libs.junit)
}
