package com.addiyon.keyboard.emoji

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiAssetOwnershipContractTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir"))).canonicalFile) {
        it.parentFile
    }.first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `emoji asset is owned and packaged by the emoji feature`() {
        val featureAsset = File(root, "features/emoji/src/main/assets/emoji.dat")
        assertTrue("features:emoji must own emoji.dat.", featureAsset.isFile)
        assertTrue("emoji.dat must not be empty.", featureAsset.length() > 0L)
        assertFalse(
            "TextRevamp must not own the shared emoji asset.",
            File(root, "apps/textrevamp/src/main/assets/emoji.dat").exists()
        )

        val buildFiles = root.walkTopDown()
            .filter(File::isFile)
            .filter { it.name == "build.gradle.kts" || it.name == "build.gradle" }
            .toList()
        val appAssetReferences = buildFiles.filter { buildFile ->
            val build = buildFile.readText()
            build.contains("apps/textrevamp/src/main/assets") ||
                Regex("assets\\.srcDir\\s*\\([^)]*apps/textrevamp")
                    .containsMatchIn(build)
        }.map { it.relativeTo(root).invariantSeparatorsPath }
        assertTrue(
            "Shared features must not reach into a product app's assets: $appAssetReferences",
            appAssetReferences.isEmpty()
        )

        val generator = File(root, "tools/build_emoji_data.py").readText()
        assertTrue(
            "The emoji generator must write into features:emoji.",
            generator.contains("\"features\", \"emoji\", \"src\", \"main\", \"assets\"")
        )
        assertFalse(
            "The emoji generator must not recreate a TextRevamp-owned asset.",
            generator.contains("apps/textrevamp/src/main/assets") ||
                generator.contains("\"apps\", \"textrevamp\", \"src\", \"main\", \"assets\"")
        )
    }
}
