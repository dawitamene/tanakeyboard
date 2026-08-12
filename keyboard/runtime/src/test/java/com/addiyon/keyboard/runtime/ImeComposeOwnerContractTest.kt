package com.addiyon.keyboard.runtime

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeComposeOwnerContractTest {
    @Test fun `input view installs compose owners on the ime window tree`() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val root = generateSequence(File(workingDirectory).canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val source = File(
            root,
            "keyboard/runtime/src/main/java/com/addiyon/keyboard/PackKeyboardService.kt"
        ).readText()

        assertTrue(source.contains("decorView.setViewTreeLifecycleOwner(this)"))
        assertTrue(source.contains("decorView.setViewTreeSavedStateRegistryOwner(this)"))
        assertTrue(source.contains("inputView.setViewTreeLifecycleOwner(this)"))
        assertTrue(source.contains("inputView.setViewTreeSavedStateRegistryOwner(this)"))
    }
}
