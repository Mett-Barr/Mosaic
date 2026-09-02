pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Resolves the Java toolchain the modules pin by downloading a matching JDK
    // when the machine has none, so a clean checkout builds without first
    // installing a specific JDK by hand.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Mosaic"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":navigation")
include(":feature:feed")
include(":feature:detail")
include(":feature:saved")
