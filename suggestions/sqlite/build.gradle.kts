plugins {
    id("addiyon.android.library")
}

android {
    namespace = "com.addiyon.keyboard.suggestions.sqlite"
}

dependencies {
    api(project(":suggestions:api"))
    implementation(project(":suggestions:core"))
    testImplementation(libs.junit)
}
