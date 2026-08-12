package com.addiyon.buildlogic

import com.addiyon.buildlogic.versioning.GitCommitCountValueSource
import com.android.build.api.dsl.ApplicationExtension
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.provider.Provider

data class ProductReleaseConfig(
    val properties: Properties,
    val versionName: String,
    val versionCode: Provider<Int>,
    val expectedReleaseCertificateSha256: String
)

fun Project.loadProductReleaseConfig(propertiesFile: File): ProductReleaseConfig {
    val properties = Properties().apply {
        if (propertiesFile.isFile) propertiesFile.inputStream().use(::load)
    }
    val versionCodeFloor = properties.getProperty("versionCodeFloor", "1").toInt()
    val gitCommitCount = providers.of(GitCommitCountValueSource::class.java) {
        parameters.workingDirectory.set(rootProject.layout.projectDirectory)
        parameters.fallback.set(versionCodeFloor)
    }
    return ProductReleaseConfig(
        properties = properties,
        versionName = properties.getProperty("versionName", "1.0.0"),
        versionCode = gitCommitCount.map { maxOf(versionCodeFloor, it) },
        expectedReleaseCertificateSha256 = properties
            .getProperty("releaseCertificateSha256", "")
            .lowercase(Locale.US)
    )
}

fun ApplicationExtension.configureVerifiedReleaseSigning(
    project: Project,
    expectedCertificateSha256: String
) {
    val propertiesFile = project.file("keystore.properties")
    if (!propertiesFile.isFile) return
    require(expectedCertificateSha256.isNotEmpty()) {
        "A releaseCertificateSha256 value is required when keystore.properties is present"
    }
    val properties = Properties().apply {
        propertiesFile.inputStream().use(::load)
    }
    val storeFile = project.file(requireNotNull(properties.getProperty("storeFile")))
    val storePassword = requireNotNull(properties.getProperty("storePassword"))
    val keyAlias = requireNotNull(properties.getProperty("keyAlias"))
    val keyPassword = requireNotNull(properties.getProperty("keyPassword"))
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        FileInputStream(storeFile).use { load(it, storePassword.toCharArray()) }
    }
    val certificate = requireNotNull(keyStore.getCertificate(keyAlias))
    val actualCertificate = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { "%02x".format(it) }
    require(actualCertificate == expectedCertificateSha256) {
        "Release signing certificate does not match the configured product certificate"
    }
    val releaseSigning = signingConfigs.findByName("release") ?: signingConfigs.create("release")
    releaseSigning.storeFile = storeFile
    releaseSigning.storePassword = storePassword
    releaseSigning.keyAlias = keyAlias
    releaseSigning.keyPassword = keyPassword
    buildTypes.getByName("release").signingConfig = releaseSigning
}
