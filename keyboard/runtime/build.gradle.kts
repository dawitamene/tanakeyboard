plugins {
    id("addiyon.android.library")
    id("addiyon.android.compose")
}

android {
    namespace = "com.addiyon.keyboard.runtime"
}

dependencies {
    api(project(":keyboard:contracts"))
    api(project(":language:api"))
    api(project(":language:android-api"))
    implementation(project(":keyboard:core"))
    api(project(":keyboard:ui"))
    implementation(project(":keyboard:preferences"))
    implementation(project(":suggestions:api"))
    implementation(project(":suggestions:core"))
    implementation(project(":suggestions:sqlite"))
    implementation(project(":features:emoji"))
    implementation(project(":features:voice"))
    implementation(project(":features:app-shell"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    api(libs.androidx.lifecycle.runtime)
    api(libs.androidx.savedstate)
    testImplementation(project(":language:amharic"))
    testImplementation(libs.junit)
}
