import java.util.zip.ZipFile
import com.addiyon.buildlogic.configureVerifiedReleaseSigning
import com.addiyon.buildlogic.loadProductReleaseConfig

plugins {
    id("addiyon.android.application")
    id("addiyon.android.compose")
    alias(libs.plugins.baselineprofile)
}

val productRelease = loadProductReleaseConfig(file("version.properties"))

android {
    namespace = "com.addiyon.keyboard"

    defaultConfig {
        applicationId = "com.textrevamp.keyboard"
        versionCode = productRelease.versionCode.get()
        versionName = productRelease.versionName
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    sourceSets {
        val benchmarkSupportManifest = "src/benchmarkSupport/AndroidManifest.xml"
        getByName("benchmark").apply {
            kotlin.directories += "src/debug/java"
            manifest.srcFile(benchmarkSupportManifest)
        }
    }
    configureVerifiedReleaseSigning(project, productRelease.expectedReleaseCertificateSha256)
}

dependencies {

    implementation(project(":keyboard:contracts"))
    implementation(project(":keyboard:ui"))
    implementation(project(":keyboard:runtime"))
    implementation(project(":language:english"))
    implementation(project(":features:ai"))
    implementation(project(":features:app-shell"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(project(":language:amharic"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(project(":keyboard:preferences"))
    androidTestImplementation(project(":suggestions:api"))
    androidTestImplementation(project(":features:voice"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.google.id)
    baselineProfile(project(":benchmark"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.appcompat)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

androidComponents {
    onVariants(selector().withName("benchmarkRelease")) {
        it.sources.kotlin?.addStaticSourceDirectory("src/debug/java")
        it.sources.manifests.addStaticManifestFile(
            "src/benchmarkSupport/AndroidManifest.xml"
        )
    }
    onVariants(selector().withName("nonMinifiedRelease")) {
        it.sources.kotlin?.addStaticSourceDirectory("src/debug/java")
        it.sources.manifests.addStaticManifestFile(
            "src/benchmarkSupport/AndroidManifest.xml"
        )
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose-reports")
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
    filter {
        exclude("com.addiyon.keyboard.benchmarkhost.**")
        exclude("com.addiyon.keyboard.debug.**")
    }
}

val verifyDebugProductContents = tasks.register("verifyDebugProductContents") {
    group = "verification"
    description = "Verify TextRevamp packages English and AI, without Ethiopian language packs."
    dependsOn("assembleDebug")
    doLast {
        val apk = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
            .walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "apk" }
            .single()
        ZipFile(apk).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            listOf(
                "assets/english.db",
                "assets/english_dictionary_manifest.properties"
            ).forEach { required ->
                check(required in entries) { "Missing language-pack asset: $required" }
            }
            check(entries.none { it.endsWith("_words.dat") || it.endsWith("_ngrams.dat") }) {
                "TextRevamp must not package dictionary build inputs"
            }
            check(entries.none { it.contains("amharic", ignoreCase = true) }) {
                "TextRevamp unexpectedly packages Amharic assets"
            }
            check(entries.none { it.endsWith("hornmorpho_LICENSE.txt") }) {
                "TextRevamp unexpectedly packages HornMorpho assets"
            }
        }
        val components = configurations.getByName("debugRuntimeClasspath")
            .incoming.resolutionResult.allComponents.map { it.id.displayName }
        check(components.any { it.contains("features:ai") }) {
            "TextRevamp must include the AI feature module"
        }
        check(components.none { it.contains("language:amharic") }) {
            "TextRevamp runtime must be English-only"
        }
        val manifest = layout.buildDirectory.file(
            "intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
        ).get().asFile.readText()
        check("android.permission.INTERNET" in manifest) {
            "TextRevamp must have network access for the TextRevamp backend"
        }
    }
}

tasks.configureEach {
    if (name == "testDebugUnitTest") {
        dependsOn(
            ":language:amharic:generateDictionaryDbs",
            ":language:english:generateDictionaryDbs"
        )
    }
}

tasks.named("check") {
    dependsOn(verifyDebugProductContents)
}

tasks.register<Exec>("verifyReleaseArtifact") {
    group = "verification"
    description = "Build and verify the signed production AAB and release metadata."
    dependsOn("bundleRelease")
    commandLine(rootProject.file("plans/verify-release-artifact.sh"))
}
