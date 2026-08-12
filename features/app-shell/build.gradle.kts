plugins {
    id("addiyon.android.library")
    id("addiyon.android.compose")
}

android { namespace = "com.addiyon.keyboard.features.appshell" }

dependencies {
    api(project(":features:api"))
    api(project(":keyboard:contracts"))
    implementation(project(":keyboard:ui"))
    implementation(project(":keyboard:preferences"))
    implementation(project(":suggestions:core"))
    api(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.play.review)
    implementation(libs.play.app.update)
    testImplementation(libs.junit)
}
