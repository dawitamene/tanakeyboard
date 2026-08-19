import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import com.addiyon.buildlogic.configureVerifiedReleaseSigning
import com.addiyon.buildlogic.loadProductReleaseConfig

plugins {
    id("addiyon.android.application")
    id("addiyon.android.compose")
}

val productRelease = loadProductReleaseConfig(rootProject.file("version.properties"))

android {
    namespace = "com.addiyon.keyboard"
    androidResources {
        noCompress += "ahrf"
    }
    defaultConfig {
        applicationId = "com.addiyon.keyboard"
        versionCode = productRelease.versionCode.get()
        versionName = productRelease.versionName
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    configureVerifiedReleaseSigning(project, productRelease.expectedReleaseCertificateSha256)
}

dependencies {
    implementation(project(":keyboard:runtime"))
    implementation(project(":language:english"))
    implementation(project(":language:amharic"))
    implementation(project(":features:app-shell"))
    implementation(project(":keyboard:preferences"))
    implementation(project(":keyboard:ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(project(":features:emoji"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val verifyDebugProductContents by tasks.registering {
    group = "verification"
    dependsOn("assembleDebug")
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/debug/addiyon-debug.apk").get().asFile
        require(apk.isFile) { "Addiyon debug APK was not produced" }
        ZipFile(apk).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            require(names.any { it.endsWith("english.db") }) { "English dictionary is missing" }
            require(names.any { it.endsWith("amharic.db") }) { "Amharic dictionary is missing" }
            val verbRuntime = zip.entries().asSequence().firstOrNull {
                it.name.endsWith("amharic_verbs.ahrf")
            }
            require(verbRuntime != null) {
                "Amharic HornMorpho runtime is missing"
            }
            require(verbRuntime.method == ZipEntry.STORED) {
                "Amharic HornMorpho runtime must be uncompressed for memory mapping"
            }
            require(names.none { it.endsWith("amharic_verbs.ahva") }) {
                "Pre-generated Amharic verb surfaces must not be packaged"
            }
            require(names.any { it.endsWith("amharic_verbs_manifest.properties") }) {
                "Amharic verb morphology manifest is missing"
            }
            require(names.any { it.endsWith("hornmorpho_LICENSE.txt") }) {
                "HornMorpho license is missing"
            }
            require(names.none { it.contains("amharic_verb_phase8.tsv") }) {
                "Developer-side verb oracle corpus must not be packaged"
            }
            require(names.none { it.endsWith("_words.dat") || it.endsWith("_ngrams.dat") }) {
                "Addiyon must not package dictionary build inputs"
            }
        }
        val components = configurations.getByName("debugRuntimeClasspath")
            .incoming.resolutionResult.allComponents.map { it.id.displayName }
        setOf("language:amharic", "language:english").forEach { required ->
            require(components.any { it.contains(required) }) { "Addiyon product is missing $required" }
        }
        require(components.none { it.contains("language:oromo") }) {
            "Addiyon product must not include the disabled Oromo language pack"
        }
        require(components.none { it.contains("features:ai") }) {
            "Addiyon product must not include the AI feature module"
        }
        val manifest = layout.buildDirectory.file(
            "intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
        ).get().asFile.readText()
        require("android.permission.INTERNET" !in manifest) {
            "Addiyon product must not request network access"
        }
    }
}

tasks.register<Exec>("sendDebugToPhone") {
    group = "distribution"
    description = "Build and send the Addiyon debug APK to the local APK Drop receiver."
    dependsOn("assembleDebug")

    val apk = layout.buildDirectory.file("outputs/apk/debug/addiyon-debug.apk")
    inputs.file(apk)

    doFirst {
        val apkFile = apk.get().asFile
        check(apkFile.isFile) { "Addiyon debug APK was not built at ${apkFile.absolutePath}." }
        val apkDropCli = System.getenv("APK_DROP_CLI")
            ?: rootProject.file("../apkdrop/bin/apkdrop").absolutePath
        check(file(apkDropCli).canExecute()) {
            "APK Drop CLI is not executable at $apkDropCli. Set APK_DROP_CLI to override it."
        }
        commandLine(
            apkDropCli,
            apkFile.absolutePath
        )
    }
}
