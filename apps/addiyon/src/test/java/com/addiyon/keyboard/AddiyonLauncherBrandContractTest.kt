package com.addiyon.keyboard

import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddiyonLauncherBrandContractTest {

    @Test
    fun addiyonUsesClassicAmharicLogoAndLauncherResources() {
        val root = projectRoot()
        val addiyonRes = root.resolve("apps/addiyon/src/main/res")

        val background = addiyonRes.resolve("drawable/ic_addiyon_background.xml").readText()
        assertTrue(background.contains("#FFEE4D2D"))

        val foreground = addiyonRes.resolve("drawable/ic_addiyon_foreground.xml").readText()
        assertTrue(foreground.contains("M166.57 378.09L229.84"))
        assertTrue(foreground.contains("#FFFFF6F0"))
        assertTrue(foreground.contains("#FFEE4D2D"))

        val appShellIcon = addiyonRes.resolve("drawable/ic_addiyon_app_shell.xml").readText()
        assertTrue(appShellIcon.contains("M166.57 378.09L229.84"))

        val standaloneIcon = addiyonRes.resolve("drawable/ic_addiyon_icon.xml").readText()
        assertTrue(standaloneIcon.contains("M166.57 378.09L229.84"))

        val adaptiveLauncher = addiyonRes.resolve("mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue(adaptiveLauncher.contains("@drawable/ic_addiyon_background"))
        assertTrue(adaptiveLauncher.contains("@drawable/ic_addiyon_foreground"))

        val adaptiveLauncherRound = addiyonRes.resolve("mipmap-anydpi-v26/ic_launcher_round.xml").readText()
        assertTrue(adaptiveLauncherRound.contains("@drawable/ic_addiyon_background"))
        assertTrue(adaptiveLauncherRound.contains("@drawable/ic_addiyon_foreground"))

        val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        densities.forEach { density ->
            val dir = addiyonRes.resolve("mipmap-$density")
            assertTrue(dir.resolve("ic_launcher.webp").isFile)
            assertTrue(dir.resolve("ic_launcher_round.webp").isFile)
        }

        val manifest = root.resolve("apps/addiyon/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
    }

    private fun projectRoot(): File {
        val start = Paths.get(System.getProperty("user.dir")).toFile().canonicalFile
        return generateSequence(start) { it.parentFile }
            .firstOrNull {
                File(it, "apps/addiyon/src/main/java").isDirectory &&
                    (File(it, "settings.gradle.kts").isFile || File(it, "settings.gradle").isFile)
            }
            ?: error("Could not locate Addiyon project root from ${start.path}.")
    }
}
