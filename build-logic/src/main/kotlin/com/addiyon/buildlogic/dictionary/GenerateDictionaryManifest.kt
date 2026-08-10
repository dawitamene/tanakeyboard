package com.addiyon.buildlogic.dictionary

import java.io.File
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateDictionaryManifest : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val databaseFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val databases = databaseFiles.files.sortedBy(File::getName)
        require(databases.isNotEmpty()) { "At least one dictionary database is required" }
        require(databases.map(File::getName).distinct().size == databases.size) {
            "Dictionary database filenames must be unique"
        }
        val output = manifestFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("schemaVersion=${DictionaryDatabaseFormat.SCHEMA_VERSION}")
                appendLine("applicationId=${DictionaryDatabaseFormat.APPLICATION_ID}")
                for (database in databases) {
                    require(database.isFile) { "Missing dictionary database: $database" }
                    appendLine("${database.name}.length=${database.length()}")
                    appendLine("${database.name}.sha256=${sha256(database)}")
                }
            }
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
