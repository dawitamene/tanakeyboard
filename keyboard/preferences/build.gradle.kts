plugins { id("addiyon.android.library") }

android { namespace = "com.addiyon.keyboard.preferences" }

dependencies {
    implementation(project(":language:api"))
    implementation(project(":keyboard:ui"))
    testImplementation(libs.junit)
}
