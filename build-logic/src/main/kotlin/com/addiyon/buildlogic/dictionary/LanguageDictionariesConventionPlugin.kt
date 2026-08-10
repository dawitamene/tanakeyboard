package com.addiyon.buildlogic.dictionary

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class LanguageDictionariesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val extension = extensions.create<LanguageDictionariesExtension>("languageDictionaries")
        extension.manifestFile.convention(
            layout.projectDirectory.file("src/main/assets/dictionary_manifest.properties")
        )
        val manifestTask = tasks.register<GenerateDictionaryManifest>(
            "generateDictionaryManifest"
        ) {
            group = "build"
            description = "Generate metadata for the configured SQLite dictionaries."
            manifestFile.set(extension.manifestFile)
        }
        val aggregateTask = tasks.register("generateDictionaryDbs") {
            group = "build"
            description = "Generate all configured SQLite dictionaries and their metadata."
            dependsOn(manifestTask)
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
                ngramsDat.set(specification.ngramsDat)
                outputDb.set(specification.outputDb)
                normalization.set(specification.normalization)
                maxPrefixLength.set(specification.maxPrefixLength)
            }
            manifestTask.configure {
                databaseFiles.from(databaseTask.flatMap { it.outputDb })
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
