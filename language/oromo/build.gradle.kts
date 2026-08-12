plugins { id("addiyon.android.library") }

android { namespace = "com.addiyon.keyboard.language.oromo" }

dependencies {
    api(project(":language:api"))
    api(project(":language:android-api"))
    implementation(project(":keyboard:core"))
    testImplementation(libs.junit)
}
