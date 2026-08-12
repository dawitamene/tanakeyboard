plugins {
    id("addiyon.android.library")
    id("addiyon.android.compose")
}

android { namespace = "com.addiyon.keyboard.features.ai" }

dependencies {
    api(project(":features:api"))
    implementation(project(":features:app-shell"))
    implementation(project(":keyboard:ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.coroutines.core)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    testImplementation(libs.junit)
}
