plugins {
    id("addiyon.android.library")
    id("addiyon.android.compose")
}

android { namespace = "com.addiyon.keyboard.features.emoji" }

dependencies {
    api(project(":features:api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
}
