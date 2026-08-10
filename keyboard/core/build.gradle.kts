plugins {
    id("addiyon.kotlin.jvm")
}

dependencies {
    implementation(project(":keyboard:contracts"))
    testImplementation(libs.junit)
}
