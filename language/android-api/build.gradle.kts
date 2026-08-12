plugins {
    id("addiyon.android.library")
}

android {
    namespace = "com.addiyon.keyboard.language.androidapi"
}

dependencies {
    api(project(":language:api"))
}
