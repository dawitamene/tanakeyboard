package com.addiyon.buildlogic.dictionary

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class PackageDictionaryAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun packageAssets() {
        val output = outputDirectory.get().asFile
        if (output.exists()) output.deleteRecursively()
        require(output.mkdirs())
        val inputs = inputFiles.files.sortedBy { it.name }
        require(inputs.isNotEmpty())
        require(inputs.map { it.name }.distinct().size == inputs.size)
        inputs.forEach { input ->
            require(input.isFile) { "Missing dictionary asset: $input" }
            input.copyTo(output.resolve(input.name))
        }
    }
}
