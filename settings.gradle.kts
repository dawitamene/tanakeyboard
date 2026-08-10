pluginManagement {
    includeBuild("build-logic")
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
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Addiyon Keyboard"
include(":app")
include(":benchmark")
include(":keyboard:contracts")
include(":keyboard:core")
include(":language:api")
include(":language:english")
include(":language:amharic")
include(":suggestions:api")
include(":suggestions:core")
include(":suggestions:sqlite")
