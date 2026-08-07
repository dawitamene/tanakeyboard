import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import com.android.build.api.variant.BuildConfigField
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.12"
}

fun File.isValidFirebaseConfigFor(packageName: String): Boolean {
    if (!isFile) return false
    val config = runCatching { readText() }.getOrNull() ?: return false
    return config.contains("\"mobilesdk_app_id\"") &&
        Regex("\"package_name\"\\s*:\\s*\"${Regex.escape(packageName)}\"")
            .containsMatchIn(config)
}

fun File.firebaseJsonValue(key: String): String? {
    if (!isFile) return null
    val config = runCatching { readText() }.getOrNull() ?: return null
    return Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]+)\"")
        .find(config)
        ?.groupValues
        ?.get(1)
}

// Master switch for the whole telemetry/crash-reporting stack. While this is
// false the Firebase Gradle plugins and SDK are never applied, every variant
// compiles with TELEMETRY_COLLECTION_ALLOWED=false, and the release Crashlytics
// mapping-upload verification is skipped. Keep it in sync with
// TelemetryFeature.ENABLED, which does the same job on the Kotlin side.
val telemetryEnabled = false

val productionFirebaseConfigs = listOf(
    file("google-services.json"),
    file("src/release/google-services.json")
)
val developmentFirebaseConfig = file("src/debug/google-services.json")
val hasProductionFirebaseConfig = productionFirebaseConfigs.any {
    it.isValidFirebaseConfigFor("com.addiyon.keyboard")
}
val hasDevelopmentFirebaseConfig = developmentFirebaseConfig
    .isValidFirebaseConfigFor("com.addiyon.keyboard.debug")
val hasAnyFirebaseConfig = telemetryEnabled &&
    (hasProductionFirebaseConfig || hasDevelopmentFirebaseConfig)

if (hasAnyFirebaseConfig) {
    pluginManager.apply("com.google.gms.google-services")
    pluginManager.apply("com.google.firebase.crashlytics")
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) load(FileInputStream(versionPropsFile))
}
val releaseVersionName = versionProps.getProperty("versionName", "1.0.0")
val versionCodeFloor = versionProps.getProperty("versionCodeFloor", "1").toInt()
val expectedReleaseCertificate = versionProps
    .getProperty("releaseCertificateSha256", "")
    .lowercase(Locale.US)
val expectedProductionFirebaseAppId = versionProps
    .getProperty("firebaseProductionAppId", "")
    .trim()
val expectedProductionFirebaseProjectId = versionProps
    .getProperty("firebaseProductionProjectId", "")
    .trim()

val autoVersionCode: Int by lazy {
    val gitCount = try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText().trim().toInt() }
    } catch (_: Exception) {
        versionCodeFloor
    }
    maxOf(versionCodeFloor, gitCount)
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

if (keystoreProperties.isNotEmpty() && expectedReleaseCertificate.isNotEmpty()) {
    val releaseKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        FileInputStream(rootProject.file("app/${keystoreProperties.getProperty("storeFile")}")).use {
            load(it, keystoreProperties.getProperty("storePassword").toCharArray())
        }
    }
    val certificate = requireNotNull(
        releaseKeyStore.getCertificate(keystoreProperties.getProperty("keyAlias"))
    )
    val actualCertificate = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { "%02x".format(it) }
    require(actualCertificate == expectedReleaseCertificate) {
        "Release signing certificate does not match version.properties"
    }
}

android {
    namespace = "com.addiyon.keyboard"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    androidResources {
        ignoreAssetsPatterns.addAll(
            listOf(
                "!amharic_words.dat",
                "!amharic_ngrams.dat",
                "!english_words.dat",
                "!english_ngrams.dat",
            )
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val generateDictionaryDbs = tasks.register<DictionaryDbGenerator>("generateDictionaryDbs") {
        group = "build"
        description = "Generate SQLite dictionaries from gzipped .dat assets."
        amharicWordsDat.set(file("src/main/assets/amharic_words.dat"))
        amharicNgramsDat.set(file("src/main/assets/amharic_ngrams.dat"))
        englishWordsDat.set(file("src/main/assets/english_words.dat"))
        englishNgramsDat.set(file("src/main/assets/english_ngrams.dat"))
        amharicDb.set(file("src/main/assets/amharic.db"))
        englishDb.set(file("src/main/assets/english.db"))
        manifestFile.set(file("src/main/assets/dictionary_manifest.properties"))
    }

    afterEvaluate {
        tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
            .configureEach { dependsOn(generateDictionaryDbs) }
        tasks.matching { it.name.contains("Lint", ignoreCase = true) }
            .configureEach { dependsOn(generateDictionaryDbs) }
    }


    defaultConfig {
        applicationId = "com.addiyon.keyboard"
        minSdk = 24
        targetSdk = 36
        versionCode = autoVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "com.addiyon.keyboard.TelemetryTestRunner"
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
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField(
                "boolean",
                "TELEMETRY_COLLECTION_ALLOWED",
                telemetryEnabled.toString()
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField(
                "boolean",
                "TELEMETRY_COLLECTION_ALLOWED",
                telemetryEnabled.toString()
            )
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
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    buildTypes.configureEach {
        if (
            name == "benchmark" ||
            name == "benchmarkRelease" ||
            name == "nonMinifiedRelease"
        ) {
            buildConfigField("boolean", "TELEMETRY_COLLECTION_ALLOWED", "false")
        }
    }
    sourceSets {
        val benchmarkSupportManifest = "src/benchmarkSupport/AndroidManifest.xml"
        getByName("benchmark").apply {
            kotlin.directories += "src/debug/java"
            manifest.srcFile(benchmarkSupportManifest)
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.play.review)
    implementation(libs.play.app.update)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.security.crypto)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.google.id)
    if (hasAnyFirebaseConfig) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }
    baselineProfile(project(":benchmark"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

if (hasAnyFirebaseConfig) {
    tasks.configureEach {
        if (
            name.contains("GoogleServices", ignoreCase = true) &&
            name.contains("Debug", ignoreCase = true) &&
            !hasDevelopmentFirebaseConfig
        ) {
            enabled = false
        }
        if (
            name.contains("GoogleServices", ignoreCase = true) &&
            !name.contains("Debug", ignoreCase = true) &&
            !hasProductionFirebaseConfig
        ) {
            enabled = false
        }
        if (
            name.contains("Crashlytics", ignoreCase = true) &&
            name.contains("Debug", ignoreCase = true) &&
            !hasDevelopmentFirebaseConfig
        ) {
            enabled = false
        }
        if (
            name.contains("Crashlytics", ignoreCase = true) &&
            !name.contains("Debug", ignoreCase = true) &&
            !hasProductionFirebaseConfig
        ) {
            enabled = false
        }
        if (
            name.contains("Crashlytics", ignoreCase = true) &&
            (
                name.contains("Benchmark", ignoreCase = true) ||
                    name.contains("NonMinified", ignoreCase = true) ||
                    name.contains("AndroidTest", ignoreCase = true) ||
                    name.contains("UnitTest", ignoreCase = true)
                )
        ) {
            enabled = false
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        if (
            variant.name == "benchmark" ||
            variant.name == "benchmarkRelease" ||
            variant.name == "nonMinifiedRelease"
        ) {
            requireNotNull(variant.buildConfigFields).put(
                "TELEMETRY_COLLECTION_ALLOWED",
                BuildConfigField("boolean", false, null)
            )
        }
        val output = variant.outputs.single()
        val copyTask = tasks.register<Copy>(
            "copy${variant.name.replaceFirstChar { it.uppercase() }}ApkToShared"
        ) {
            from(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
            include("*.apk")
            into("/Users/dev/Sync")
            onlyIf { file("/Users/dev/Sync").isDirectory }
            rename {
                val timeFormat = SimpleDateFormat("yyyy-MM-dd-hh-mm-a", Locale.US)
                "addiyon-${variant.name}-v${output.versionName.get()}-" +
                    "${output.versionCode.get()}-${timeFormat.format(Date())}.apk"
            }
        }
        tasks.matching {
            it.name == "assemble${variant.name.replaceFirstChar { char -> char.uppercase() }}"
        }.configureEach {
            finalizedBy(copyTask)
        }
    }
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

val telemetryDisabledVariants = listOf("benchmarkRelease", "nonMinifiedRelease")
val verifyBenchmarkTelemetryBuildConfig =
    tasks.register("verifyBenchmarkTelemetryBuildConfig") {
        group = "verification"
        dependsOn(
            telemetryDisabledVariants.map {
                "generate${it.replaceFirstChar { char -> char.uppercase() }}BuildConfig"
            }
        )
        doLast {
            telemetryDisabledVariants.forEach { variantName ->
                val taskName =
                    "generate${variantName.replaceFirstChar { char -> char.uppercase() }}BuildConfig"
                val buildConfigs = tasks.named(taskName)
                    .get()
                    .outputs
                    .files
                    .asFileTree
                    .matching {
                        include("**/com/addiyon/keyboard/BuildConfig.java")
                    }
                    .files
                check(buildConfigs.size == 1) {
                    "$variantName must generate exactly one application BuildConfig.java"
                }
                check(
                    Regex(
                        "TELEMETRY_COLLECTION_ALLOWED\\s*=\\s*false;"
                    ).containsMatchIn(buildConfigs.single().readText())
                ) {
                    "$variantName must compile with telemetry collection disabled"
                }
            }
        }
    }

tasks.named("check").configure {
    dependsOn(verifyBenchmarkTelemetryBuildConfig)
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

val coreCoverageIncludes = listOf(
    "com/addiyon/keyboard/transliteration/**",
    "com/addiyon/keyboard/composing/**",
    "com/addiyon/keyboard/model/**",
    "com/addiyon/keyboard/suggestion/AmharicCommitPolicy*",
    "com/addiyon/keyboard/suggestion/AmharicPrefixCompletion*",
    "com/addiyon/keyboard/suggestion/CandidateRanker*",
    "com/addiyon/keyboard/suggestion/CasePattern*",
    "com/addiyon/keyboard/suggestion/DatabaseFailurePolicy*",
    "com/addiyon/keyboard/suggestion/DictionaryAssetMetadata*",
    "com/addiyon/keyboard/suggestion/EmailSuggestions*",
    "com/addiyon/keyboard/suggestion/FuzzyMatcher*",
    "com/addiyon/keyboard/suggestion/NgramContext*",
    "com/addiyon/keyboard/suggestion/PerWordCache*",
    "com/addiyon/keyboard/suggestion/PredictionCache*",
    "com/addiyon/keyboard/suggestion/SuggestionCache*",
    "com/addiyon/keyboard/suggestion/SuggestionTypes*",
    "com/addiyon/keyboard/telemetry/CoarseThrowableClass*",
    "com/addiyon/keyboard/telemetry/DiagnosticsConsent*",
    "com/addiyon/keyboard/telemetry/NoOpTelemetryBackend*",
    "com/addiyon/keyboard/telemetry/NonFatalCategory*",
    "com/addiyon/keyboard/telemetry/NonFatalRateLimiter*",
    "com/addiyon/keyboard/telemetry/NonFatalSanitizer*",
    "com/addiyon/keyboard/telemetry/SanitizedNonFatal*",
    "com/addiyon/keyboard/telemetry/StoredTelemetryConsent*",
    "com/addiyon/keyboard/telemetry/TelemetryBackend*",
    "com/addiyon/keyboard/telemetry/TelemetryConsentStore*",
    "com/addiyon/keyboard/telemetry/TelemetryController*",
    "com/addiyon/keyboard/telemetry/TelemetryEvent*",
    "com/addiyon/keyboard/telemetry/TelemetryLanguage*",
    "com/addiyon/keyboard/telemetry/TelemetryLayout*",
    "com/addiyon/keyboard/telemetry/TelemetryPolicy*",
    "com/addiyon/keyboard/telemetry/TelemetrySchema*",
    "com/addiyon/keyboard/telemetry/TelemetrySetting*",
    "com/addiyon/keyboard/telemetry/TelemetrySuggestionKind*",
    "com/addiyon/keyboard/telemetry/TelemetryVoiceError*",
    "com/addiyon/keyboard/telemetry/TelemetryVoiceResult*",
    "com/addiyon/keyboard/ui/settings/PreferenceValueSanitizer*"
)

val coreCoverageClassDirectories = files(
    fileTree(
        layout.buildDirectory.dir(
            "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
        )
    ) {
        include(coreCoverageIncludes)
    },
    fileTree(
        layout.buildDirectory.dir(
            "intermediates/javac/debug/compileDebugJavaWithJavac/classes"
        )
    ) {
        include(coreCoverageIncludes)
    }
)

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val jacocoCoreDebugUnitTestReport = tasks.register<JacocoReport>(
    "jacocoCoreDebugUnitTestReport"
) {
    group = "verification"
    description = "Generate XML and HTML coverage for the JVM-testable core."
    dependsOn("testDebugUnitTest")
    doFirst {
        check(!coreCoverageClassDirectories.asFileTree.isEmpty) {
            "Core coverage class set is empty; check the AGP debug class output path."
        }
    }
    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(coreCoverageClassDirectories)
    executionData.setFrom(
        layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")
    )
    reports {
        xml.required.set(true)
        xml.outputLocation.set(
            layout.buildDirectory.file(
                "reports/jacoco/coreDebugUnitTest/coreDebugUnitTest.xml"
            )
        )
        html.required.set(true)
        html.outputLocation.set(
            layout.buildDirectory.dir("reports/jacoco/coreDebugUnitTest/html")
        )
        csv.required.set(false)
    }
}

val verifyCoreDebugUnitTestCoverage = tasks.register<JacocoCoverageVerification>(
    "verifyCoreDebugUnitTestCoverage"
) {
    group = "verification"
    description = "Enforce line and branch coverage for the JVM-testable core."
    dependsOn(jacocoCoreDebugUnitTestReport)
    classDirectories.setFrom(coreCoverageClassDirectories)
    executionData.setFrom(
        layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")
    )
    violationRules {
        rule {
            element = "PACKAGE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyCoreDebugUnitTestCoverage)
}

val verifyProductionFirebaseConfig = tasks.register("verifyProductionFirebaseConfig") {
    group = "verification"
    description = "Verify the production Firebase configuration and application ID."
    doLast {
        // Telemetry is switched off, so there is deliberately no Firebase config
        // to verify. Re-enabling telemetryEnabled restores these checks.
        if (!telemetryEnabled) {
            logger.lifecycle(
                "Telemetry is disabled (telemetryEnabled = false); " +
                    "skipping production Firebase config verification."
            )
            return@doLast
        }
        val presentConfigs = productionFirebaseConfigs.filter(File::isFile)
        check(presentConfigs.size == 1) {
            "Provide exactly one production Firebase config at app/google-services.json " +
                "or app/src/release/google-services.json."
        }
        check(hasProductionFirebaseConfig) {
            "A valid production Firebase config for com.addiyon.keyboard is required at " +
                "app/google-services.json or app/src/release/google-services.json. " +
                "Download it from Firebase; do not fabricate or hand-edit it."
        }
        check(expectedProductionFirebaseAppId.isNotEmpty()) {
            "Pin firebaseProductionAppId in version.properties from the downloaded " +
                "production Firebase config."
        }
        check(expectedProductionFirebaseProjectId.isNotEmpty()) {
            "Pin firebaseProductionProjectId in version.properties from the downloaded " +
                "production Firebase config."
        }
        val config = presentConfigs.single()
        check(
            config.firebaseJsonValue("mobilesdk_app_id") ==
                expectedProductionFirebaseAppId
        ) {
            "The production Firebase app ID does not match version.properties."
        }
        check(
            config.firebaseJsonValue("project_id") ==
                expectedProductionFirebaseProjectId
        ) {
            "The production Firebase project ID does not match version.properties."
        }
    }
}

val verifyReleaseCrashlyticsWiring = tasks.register("verifyReleaseCrashlyticsWiring") {
    group = "verification"
    description = "Verify release Crashlytics mapping injection and upload wiring."
    dependsOn(verifyProductionFirebaseConfig)
    doLast {
        // With telemetry off the Crashlytics plugin is never applied, so its
        // mapping-upload tasks legitimately do not exist.
        if (!telemetryEnabled) {
            logger.lifecycle(
                "Telemetry is disabled (telemetryEnabled = false); " +
                    "skipping release Crashlytics mapping upload verification."
            )
            return@doLast
        }
        check(tasks.findByName("injectCrashlyticsMappingFileIdRelease")?.enabled == true) {
            "Crashlytics release mapping metadata task is missing or disabled."
        }
        check(tasks.findByName("uploadCrashlyticsMappingFileRelease")?.enabled == true) {
            "Crashlytics release mapping upload task is missing or disabled."
        }
    }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    mustRunAfter(verifyReleaseCrashlyticsWiring)
}

tasks.register<Exec>("verifyReleaseArtifact") {
    group = "verification"
    description = "Build and verify the signed production AAB and release metadata."
    dependsOn(verifyReleaseCrashlyticsWiring, "bundleRelease")
    commandLine(rootProject.file("plans/verify-release-artifact.sh"))
}
