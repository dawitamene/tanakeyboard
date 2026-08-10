package com.addiyon.buildlogic.versioning

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

abstract class GitCommitCountValueSource :
    ValueSource<Int, GitCommitCountValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val workingDirectory: DirectoryProperty
        val fallback: Property<Int>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): Int {
        val output = ByteArrayOutputStream()
        return runCatching {
            val result = execOperations.exec {
                commandLine("git", "rev-list", "--count", "HEAD")
                workingDir(parameters.workingDirectory.get().asFile)
                standardOutput = output
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
            if (result.exitValue == 0) output.toString().trim().toIntOrNull() else null
        }.getOrNull() ?: parameters.fallback.get()
    }
}
