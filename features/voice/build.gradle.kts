plugins { id("addiyon.android.library") }

android { namespace = "com.addiyon.keyboard.features.voice" }

dependencies {
    api(project(":features:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
