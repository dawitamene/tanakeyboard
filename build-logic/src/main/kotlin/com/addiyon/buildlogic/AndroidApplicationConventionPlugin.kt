package com.addiyon.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            applyAddiyonDefaults()
        }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants(selector().all()) { variant ->
                val productName = name
                val variantName = variant.name.replaceFirstChar { it.uppercase() }
                val copyTask = tasks.register<Copy>("copy${variantName}ApkToShared") {
                    from(variant.artifacts.get(SingleArtifact.APK))
                    include("*.apk")
                    into("/Users/dev/Sync/addiyon-keyboard")
                    outputs.upToDateWhen { false }
                    rename {
                        sharedApkFileName(productName, Date())
                    }
                }
                tasks.matching { it.name == "assemble$variantName" }.configureEach {
                    finalizedBy(copyTask)
                }
            }
        }
    }
}

internal fun sharedApkFileName(
    productName: String,
    date: Date,
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    val timestamp = SimpleDateFormat("hh:mma", Locale.US).apply {
        this.timeZone = timeZone
    }.format(date)
    return "$productName-$timestamp.apk"
}
