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

moduleGraphConfig {
    readmePath.set("${rootDir}/README.md")
    heading.set("### Module graph")
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
