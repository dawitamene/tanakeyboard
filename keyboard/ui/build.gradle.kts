plugins {
    id("addiyon.android.library")
    id("addiyon.android.compose")
}

android {
    namespace = "com.addiyon.keyboard.ui.shared"
}

dependencies {
    api(project(":keyboard:contracts"))
    api(project(":language:api"))
    api(project(":suggestions:api"))
    api(project(":features:voice"))
    api(project(":features:emoji"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation(project(":language:english"))
    testImplementation(project(":language:amharic"))
    testImplementation(libs.junit)
}
