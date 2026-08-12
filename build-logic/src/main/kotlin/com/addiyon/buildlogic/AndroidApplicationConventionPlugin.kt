package com.addiyon.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                val output = variant.outputs.single()
                val productName = name
                val variantName = variant.name.replaceFirstChar { it.uppercase() }
                val copyTask = tasks.register<Copy>("copy${variantName}ApkToShared") {
                    from(variant.artifacts.get(SingleArtifact.APK))
                    include("*.apk")
                    into("/Users/dev/Sync/addiyon-keyboard")
                    rename {
                        val timestamp = SimpleDateFormat(
                            "yyyy-MM-dd-hh-mm-a",
                            Locale.US
                        ).format(Date())
                        "$productName-${variant.name}-v${output.versionName.get()}-" +
                            "${output.versionCode.get()}-$timestamp.apk"
                    }
                }
                tasks.matching { it.name == "assemble$variantName" }.configureEach {
                    finalizedBy(copyTask)
                }
            }
        }
    }
}
