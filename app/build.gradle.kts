import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.compose")


}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) load(FileInputStream(versionPropsFile))
}
val releaseVersionName = versionProps.getProperty("versionName", "1.0.0")

val autoVersionCode: Int by lazy {
    try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText().trim().toInt() }
    } catch (_: Exception) {
        (System.currentTimeMillis() / 1000).toInt()
    }
}

// Release signing is driven by a gitignored keystore.properties in the module
// root (never committed). When it's absent -- e.g. a fresh checkout or CI
// without the secret -- we skip the signing config so debug builds still work;
// only `bundleRelease`/`assembleRelease` require it.
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "com.addiyon.keyboard"
    compileSdk = 36

    buildFeatures {
        compose = true
    }


    defaultConfig {
        applicationId = "com.addiyon.keyboard"
        minSdk = 24
        targetSdk = 36
        versionCode = autoVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Only materialized when keystore.properties is present.
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
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
            // Falls back to unsigned when keystore.properties is missing, so a
            // fresh checkout can still `assembleRelease` (just not upload it).
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    applicationVariants.all {
        val variant = this

        variant.outputs.all {
        }

        variant.assembleProvider.configure {
            doLast {
                // Local sideload convenience: drop a timestamped APK into a
                // shared folder for the test device. No-op on any host that
                // doesn't have that folder (CI, another machine, Play builds),
                // so it never breaks the build there.
                val targetDir = file("/Users/dev/Shared")
                if (targetDir.isDirectory) {
                    val timeFormat = SimpleDateFormat("hh-mm-a", Locale.getDefault())
                    val fileName = "${timeFormat.format(Date())}.apk"

                    copy {
                        from(file("$buildDir/outputs/apk/${variant.name}"))
                        include("*.apk")
                        into(targetDir)
                        rename { fileName }
                    }
                }
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose.v191)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.play.review)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
