// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

tasks.register("checkKeyboardProducts") {
    group = "verification"
    description = "Run verification for every keyboard product and its benchmark assembly."
    dependsOn(
        ":keyboard:contracts:check",
        ":keyboard:core:check",
        ":keyboard:preferences:check",
        ":keyboard:ui:check",
        ":keyboard:runtime:check",
        ":language:api:check",
        ":language:android-api:check",
        ":language:english:check",
        ":language:amharic:check",
        ":language:oromo:check",
        ":suggestions:api:check",
        ":suggestions:core:check",
        ":suggestions:sqlite:check",
        ":features:api:check",
        ":features:emoji:check",
        ":features:voice:check",
        ":features:ai:check",
        ":features:app-shell:check",
        ":apps:textrevamp:testDebugUnitTest",
        ":apps:textrevamp:lintRelease",
        ":apps:textrevamp:verifyDebugProductContents",
        ":apps:addiyon:verifyDebugProductContents",
        ":benchmark:assembleBenchmarkRelease"
    )
}
