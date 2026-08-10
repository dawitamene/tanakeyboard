plugins {
    id("addiyon.kotlin.jvm")
}

dependencies {
    api(project(":keyboard:contracts"))
    api(project(":suggestions:api"))
    testImplementation(libs.junit)
}
