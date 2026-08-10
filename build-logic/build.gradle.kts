plugins {
    `kotlin-dsl`
}

group = "com.addiyon.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.gradle.plugin)
    implementation(libs.sqlite.jdbc)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "addiyon.android.application"
            implementationClass = "com.addiyon.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "addiyon.android.library"
            implementationClass = "com.addiyon.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "addiyon.android.compose"
            implementationClass = "com.addiyon.buildlogic.AndroidComposeConventionPlugin"
        }
        register("kotlinJvm") {
            id = "addiyon.kotlin.jvm"
            implementationClass = "com.addiyon.buildlogic.KotlinJvmConventionPlugin"
        }
        register("languageDictionaries") {
            id = "addiyon.language-dictionaries"
            implementationClass =
                "com.addiyon.buildlogic.dictionary.LanguageDictionariesConventionPlugin"
        }
    }
}
