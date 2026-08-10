// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

tasks.register("checkKeyboardProducts") {
    group = "verification"
    description = "Run verification for every keyboard product and its benchmark assembly."
    dependsOn(
        ":keyboard:contracts:check",
        ":keyboard:core:check",
        ":language:api:check",
        ":language:english:check",
        ":language:amharic:check",
        ":suggestions:api:check",
        ":suggestions:core:check",
        ":suggestions:sqlite:check",
        ":app:testDebugUnitTest",
        ":app:verifyCoreDebugUnitTestCoverage",
        ":app:lintRelease",
        ":app:verifyDebugLanguagePackAssets",
        ":benchmark:assembleBenchmarkRelease"
    )
}
