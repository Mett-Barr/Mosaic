import dev.iurysouza.modulegraph.LinkText
import dev.iurysouza.modulegraph.ModuleType.AndroidApp
import dev.iurysouza.modulegraph.ModuleType.AndroidLibrary
import dev.iurysouza.modulegraph.ModuleType.Kotlin
import dev.iurysouza.modulegraph.Orientation
import dev.iurysouza.modulegraph.Theme

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    // Declared here (without applying) so it shares a class loader with the Hilt
    // plugin — see https://github.com/google/dagger/issues/3965
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.module.graph)
    alias(libs.plugins.kover)
}

// The module split is only worth its cost if something checks it. The compiler
// enforces one part of it — :core:domain is plain Kotlin, so Android types cannot
// reach it — but nothing stops a feature from reaching into :core:data or into a
// sibling feature except a person noticing. Gradle knows the real edges, so the
// rule is checked here rather than asserted in a document.
val allowedProjectDependencies = mapOf(
    ":core:domain" to emptySet(),
    ":core:data" to setOf(":core:domain"),
    ":core:ui" to setOf(":core:domain"),
    // The theme still arrives through Compose, from whoever applied it above.
    // The edge is here for what Compose cannot pass down: a timestamp format
    // that two screens have to agree on, and did not while each kept its own.
    ":feature:feed" to setOf(":core:domain", ":core:ui"),
    ":feature:detail" to setOf(":core:domain", ":core:ui"),
    ":feature:saved" to setOf(":core:domain"),
    // The one module that knows which screen leads to which, and therefore the
    // only one allowed to see every feature at once.
    ":navigation" to setOf(
        ":core:domain", ":feature:feed", ":feature:detail", ":feature:saved",
    ),
    // :app is the composition root and nothing else. It must not declare an edge
    // to a feature -- the moment it has one, a screen decision can be made there
    // again. What this cannot say is that :app never sees a feature type:
    // :navigation exposes them with api, and this reads declared edges rather
    // than the classpath (DECISIONS.md 31).
    ":app" to setOf(":core:domain", ":core:data", ":core:ui", ":navigation"),
)

// Coverage is aggregated here because a per-module number answers the wrong
// question: :core:domain is exercised largely by tests that live in the modules
// above it, and read alone it would look untested. Kover is measurement, not a
// gate -- no threshold is set, because a number that must go up is a number
// people write tests against instead of against behaviour.
dependencies {
    listOf(
        ":core:domain", ":core:data", ":core:ui",
        ":feature:feed", ":feature:detail", ":feature:saved",
        ":navigation", ":app",
    ).forEach { kover(project(it)) }
}

kover {
    reports {
        filters {
            excludes {
                // Code this project did not write. Hilt, Room and Compose each
                // generate classes that land in the same packages as the source,
                // and counting them measures the annotation processors rather
                // than the tests.
                classes(
                    "dagger.hilt.*",
                    "hilt_aggregated_deps.*",
                    "*_Factory",
                    "*_Factory\$*",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "*_MembersInjector",
                    "*_GeneratedInjector",
                    "*_Impl",
                    "*_Impl\$*",
                    "*ComposableSingletons*",
                    "*\$serializer",
                )
                // Deliberately NOT excluded: @Composable. Screens really are
                // untested (DECISIONS.md 20), and a filter that hid them would
                // turn the one number that says so into one that does not.
            }
        }
    }
}

// Subprojects have to be evaluated before their declared dependencies can be read.
evaluationDependsOnChildren()

val checkModuleDependencies by tasks.registering {
    group = "verification"
    description = "Fails when a module depends on one the architecture does not allow."
    doLast {
        val violations = allowedProjectDependencies.flatMap { (path, allowed) ->
            project(path).configurations
                .flatMap { configuration -> configuration.dependencies }
                .filterIsInstance<ProjectDependency>()
                .map { it.path }
                .distinct()
                // AGP and KSP add configurations that point a module at itself
                // (test variants, generated sources); those are not architecture.
                .filterNot { it == path || it in allowed }
                .map { "$path must not depend on $it" }
        }
        require(violations.isEmpty()) {
            "Module dependency rules violated:\n" + violations.joinToString("\n") { "  - $it" }
        }
    }
}

// Runs as part of `./gradlew build`, so the gate covers it without a separate step.
subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkModuleDependencies) }
}

moduleGraphConfig {
    readmePath.set("${rootDir}/README.md")
    heading.set("### 模組相依圖")
    orientation.set(Orientation.TOP_TO_BOTTOM)
    linkText.set(LinkText.NONE)
    nestingEnabled.set(false)
    setStyleByModuleType.set(true)
    theme.set(
        Theme.BASE(
            // A mid grey so the edges stay visible on both GitHub themes.
            themeVariables = mapOf("lineColor" to "#8C8C8C"),
            moduleTypes = listOf(
                AndroidApp("#2C4162"),
                AndroidLibrary("#3BD482"),
                Kotlin("#8150FF"),
            ),
        ),
    )
}
