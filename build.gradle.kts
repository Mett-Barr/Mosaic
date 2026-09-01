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
    // :app is the composition root: it is the one module allowed to see everyone.
    ":app" to setOf(
        ":core:domain", ":core:data", ":core:ui",
        ":feature:feed", ":feature:detail", ":feature:saved",
    ),
)

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
