package com.addiyon.buildlogic.dictionary

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class LanguageDictionariesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val extension = extensions.create<LanguageDictionariesExtension>("languageDictionaries")
        extension.manifestFile.convention(
            layout.buildDirectory.file(
                "intermediates/dictionaryAssets/dictionary_manifest.properties"
            )
        )
        val manifestTask = tasks.register<GenerateDictionaryManifest>(
            "generateDictionaryManifest"
        ) {
            group = "build"
            description = "Generate metadata for the configured SQLite dictionaries."
            manifestFile.set(extension.manifestFile)
        }
        val packageTask = tasks.register<PackageDictionaryAssets>("packageDictionaryAssets") {
            group = "build"
            description = "Package generated SQLite dictionaries and metadata as Android assets."
            inputFiles.from(manifestTask.flatMap { it.manifestFile })
            outputDirectory.set(layout.buildDirectory.dir("generated/dictionaryAssets"))
        }
        val aggregateTask = tasks.register("generateDictionaryDbs") {
            group = "build"
            description = "Generate all configured SQLite dictionaries and their metadata."
            dependsOn(packageTask)
        }
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryAndroidComponentsExtension> {
                onVariants(selector().all()) { variant ->
                    variant.sources.assets?.addGeneratedSourceDirectory(
                        packageTask,
                        PackageDictionaryAssets::outputDirectory,
                    )
                }
            }
        }
        extension.dictionaries.all {
            val specification = this
            normalization.convention(GenerateDictionaryDatabase.NORMALIZATION_LATIN_LOWERCASE)
            maxPrefixLength.convention(2)
            val taskName = "generate${name.replaceFirstChar(Char::uppercase)}DictionaryDb"
            val databaseTask = tasks.register<GenerateDictionaryDatabase>(taskName) {
                group = "build"
                description = "Generate the ${specification.name} SQLite dictionary."
                languageId.set(specification.name)
                wordsDat.set(specification.wordsDat)
                lexemesDat.set(specification.lexemesDat)
                ngramsDat.set(specification.ngramsDat)
                outputDb.set(specification.outputDb)
                normalization.set(specification.normalization)
                maxPrefixLength.set(specification.maxPrefixLength)
            }
            manifestTask.configure {
                databaseFiles.from(databaseTask.flatMap { it.outputDb })
            }
            packageTask.configure {
                inputFiles.from(databaseTask.flatMap { it.outputDb })
            }
        }
        tasks.configureEach {
            if (
                (name.startsWith("merge") && name.endsWith("Assets")) ||
                name.contains("Lint", ignoreCase = true)
            ) {
                dependsOn(aggregateTask)
            }
        }
    }
}
