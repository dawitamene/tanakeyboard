package com.addiyon.keyboard.runtime

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleBoundaryContractTest {
    @Test fun `runtime and shared UI import no concrete language implementation`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val root = generateSequence(File(workingDirectory).canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val forbidden = Regex("import com\\.addiyon\\.keyboard\\.(?:language\\.(?:amharic|english|oromo)|transliteration)")
        val violations = listOf("keyboard/runtime/src/main", "keyboard/ui/src/main")
            .flatMap { File(root, it).walkTopDown().filter(File::isFile).filter { file -> file.extension == "kt" }.toList() }
            .filter { forbidden.containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).path }
        assertTrue("Shared modules import concrete languages: $violations", violations.isEmpty())
    }
}
