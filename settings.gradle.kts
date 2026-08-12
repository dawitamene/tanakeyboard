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

rootProject.name = "Addiyon Keyboards"
include(":benchmark")
include(":keyboard:contracts")
include(":keyboard:core")
include(":keyboard:ui")
include(":keyboard:runtime")
include(":keyboard:preferences")
include(":language:api")
include(":language:android-api")
include(":language:english")
include(":language:amharic")
include(":suggestions:api")
include(":suggestions:core")
include(":suggestions:sqlite")
include(":features:api")
include(":features:emoji")
include(":features:voice")
include(":features:ai")
include(":features:app-shell")
include(":language:oromo")
include(":apps:addiyon")
include(":apps:textrevamp")
