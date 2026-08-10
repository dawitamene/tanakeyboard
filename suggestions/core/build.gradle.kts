plugins {
    id("addiyon.kotlin.jvm")
}

dependencies {
    api(project(":suggestions:api"))
    testImplementation(libs.junit)
}
