package com.addiyon.keyboard.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

class SharedAppShellContractTest {
    @Test
    fun everyProductUsesTheSharedAppShell() {
        val root = projectRoot()
        val sharedManifest = root.resolve(
            "features/app-shell/src/main/AndroidManifest.xml"
        ).readText()
        assertTrue(sharedManifest.contains("com.addiyon.keyboard.MainActivity"))
        listOf("apps/addiyon", "apps/textrevamp").forEach { module ->
            val buildFile = root.resolve("$module/build.gradle.kts").readText()
            assertTrue(
                "$module must depend on the shared app shell",
                buildFile.contains("project(\":features:app-shell\")")
            )
            val manifest = root.resolve("$module/src/main/AndroidManifest.xml").readText()
            assertFalse(
                "$module must inherit the standard launcher instead of redeclaring it",
                manifest.contains("MainActivity")
            )
        }
    }

    @Test
    fun launchersDelegateLifecycleNavigationAndStatusToTheSharedShell() {
        val root = projectRoot()
        val addiyonMain = root.resolve(
            "apps/addiyon/src/main/java/com/addiyon/keyboard/MainActivity.kt"
        ).readText()
        val textRevampMain = root.resolve(
            "apps/textrevamp/src/main/java/com/addiyon/keyboard/MainActivity.kt"
        ).readText()
        val sharedActivity = root.resolve(
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/" +
                "KeyboardAppShellActivity.kt"
        ).readText()
        val sharedRoot = root.resolve(
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/" +
                "KeyboardAppShell.kt"
        ).readText()
        val sharedStatus = root.resolve(
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/" +
                "KeyboardAppStatus.kt"
        ).readText()

        assertTrue(addiyonMain.contains(": KeyboardSetupActivity()"))
        assertTrue(textRevampMain.contains(": KeyboardAppShellActivity()"))
        assertFalse(textRevampMain.contains("private enum class ScreenKey"))
        assertTrue(sharedActivity.contains("rememberKeyboardAppStatus()"))
        assertTrue(sharedRoot.contains("fun KeyboardAppShell("))
        assertTrue(sharedRoot.contains("KeyboardSettingsMenuScreen("))
        assertTrue(sharedRoot.contains("KeyboardOnboardingScreen("))
        assertTrue(sharedStatus.contains("object KeyboardAppStatusReader"))
    }

    @Test
    fun standardDestinationsStayInParityAndAiRemainsTextRevampOnly() {
        val root = projectRoot()
        val packConfig = root.resolve(
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/" +
                "KeyboardSettingsShell.kt"
        ).readText()
        val textRevampConfig = root.resolve(
            "apps/textrevamp/src/main/java/com/addiyon/keyboard/TextRevampAppShell.kt"
        ).readText()
        listOf(
            "THEMES",
            "GUIDE",
            "PREFERENCES",
            "KEYBOARD_HEIGHT",
            "TEST_KEYBOARD",
            "PERSONAL_DICTIONARY",
            "FEEDBACK",
            "ABOUT"
        ).forEach { id ->
            assertTrue("Shared config is missing $id", packConfig.contains(".$id"))
        }
        assertTrue(textRevampConfig.contains("packKeyboardAppShellConfig("))
        assertTrue(textRevampConfig.contains("KeyboardAppShellCustomization("))
        assertFalse(textRevampConfig.contains("KeyboardShellDestination("))
        assertTrue(textRevampConfig.contains("AiAccountActivity"))
        assertFalse(packConfig.contains("AiAccountActivity"))
        assertFalse(
            root.resolve("apps/addiyon/src/main/java/com/addiyon/keyboard/MainActivity.kt")
                .readText()
                .contains("AiAccountActivity")
        )
    }

    @Test
    fun toolbarLaunchesEachProductsConcreteSharedShell() {
        val root = projectRoot()
        val runtime = root.resolve(
            "keyboard/runtime/src/main/java/com/addiyon/keyboard/PackKeyboardService.kt"
        ).readText()
        assertTrue(runtime.contains("protected abstract val appShellActivityClass"))
        assertTrue(runtime.contains("Intent(this, appShellActivityClass)"))
        assertTrue(runtime.contains("KeyboardAppShellActivity.EXTRA_OPEN_DESTINATION"))
        assertTrue(runtime.contains("KeyboardShellDestinations.FEEDBACK"))
        assertFalse(runtime.contains("Intent(this, KeyboardSetupActivity::class.java)"))
        listOf("apps/addiyon", "apps/textrevamp").forEach { module ->
            val service = root.resolve(
                "$module/src/main/java/com/addiyon/keyboard/AddiyonKeyboardService.kt"
            ).readText()
            assertTrue(
                "$module must route toolbar actions to its concrete shell",
                service.contains("appShellActivityClass") &&
                    service.contains("MainActivity::class.java")
            )
        }
    }

    @Test
    fun statusAndExternalActionImplementationsHaveNoProductLocalCopies() {
        val root = projectRoot()
        assertFalse(
            root.resolve(
                "apps/textrevamp/src/main/java/com/addiyon/keyboard/KeyboardStatus.kt"
            ).exists()
        )
        assertFalse(
            root.resolve(
                "apps/textrevamp/src/main/java/com/addiyon/keyboard/ExternalActions.kt"
            ).exists()
        )
        assertFalse(
            root.resolve(
                "apps/textrevamp/src/main/java/com/addiyon/keyboard/ui/settings/SettingsScreen.kt"
            ).exists()
        )
        assertFalse(
            root.resolve(
                "apps/textrevamp/src/main/java/com/addiyon/keyboard/ui/onboarding/OnboardingScreen.kt"
            ).exists()
        )
        val productUiFiles = root.resolve(
            "apps/textrevamp/src/main/java/com/addiyon/keyboard/ui"
        ).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(
            "TextRevamp must configure shared UI instead of owning app-local UI files: $productUiFiles",
            productUiFiles.isEmpty()
        )
    }

    @Test
    fun productModulesCannotOwnCommonShellCopyOrDestinationMappings() {
        val root = projectRoot()
        val productSources = listOf("apps/addiyon", "apps/textrevamp").flatMap { module ->
            root.resolve("$module/src/main/java").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        }
        val forbiddenCommonConstructors = listOf(
            "KeyboardStandardShellCopy(",
            "KeyboardPreferencesCopy(",
            "KeyboardHeightCopy(",
            "KeyboardPersonalDictionaryCopy(",
            "KeyboardFeedbackCopy(",
            "KeyboardOnboardingCopy("
        )
        productSources.forEach { source ->
            val text = source.readText()
            forbiddenCommonConstructors.forEach { constructor ->
                assertFalse(
                    "${source.relativeTo(root)} must configure the shared shell without " +
                        "rebuilding common copy through $constructor",
                    text.contains(constructor)
                )
            }
            Regex(
                "KeyboardShellDestinations\\.(THEMES|GUIDE|PREFERENCES|KEYBOARD_HEIGHT|" +
                    "TEST_KEYBOARD|PERSONAL_DICTIONARY|FEEDBACK|ABOUT)"
            ).find(text)?.let { match ->
                throw AssertionError(
                    "${source.relativeTo(root)} owns common destination mapping ${match.value}"
                )
            }
        }
        productSources.filter { it.readText().contains("KeyboardTourPage(") }.forEach { source ->
            val text = source.readText()
            assertTrue(
                "${source.relativeTo(root)} may add only AI-specific tour pages",
                source.path.contains("apps/textrevamp") &&
                    text.contains("featureTourPages") &&
                    text.contains("aiTourDescription") &&
                    !text.contains("strings.tour")
            )
        }

        val textCopy = root.resolve(
            "apps/textrevamp/src/main/java/com/addiyon/keyboard/TextRevampStrings.kt"
        ).readText()
        listOf(
            "back",
            "themes",
            "typingGuide",
            "preferences",
            "testKeyboard",
            "personalDictionary",
            "shareApp",
            "rateApp",
            "feedback",
            "keyboardHeight",
            "vibrateOnKeypress",
            "soundOnKeypress",
            "numberRow",
            "testPlaceholder",
            "activateTitle",
            "activateDescription",
            "openSettings",
            "activateFootnote",
            "enableTitle",
            "enableDescription",
            "switchKeyboard",
            "stepFormat",
            "allSet",
            "allSetSubtitle",
            "tourSkip",
            "tourNext",
            "tourStart",
            "tourTypingTitle",
            "tourTypingDescription",
            "tourTypingExample",
            "tourSuggestionsTitle",
            "tourSuggestionsDescription",
            "tourPersonalizeTitle",
            "tourPersonalizeDescription",
            "updateDownloaded",
            "updateRestart"
        ).forEach { field ->
            assertFalse(
                "TextRevampStrings must not own common shell copy field '$field'; " +
                    "use the shared app-shell resource contract",
                Regex("\\bval\\s+$field\\s*:\\s*String\\b").containsMatchIn(textCopy) ||
                    Regex("\\b$field\\s*=").containsMatchIn(textCopy)
            )
        }

        val sharedStrings = root.resolve(
            "features/app-shell/src/main/res/values/strings.xml"
        ).readText()
        listOf("keyboard_activate_description", "keyboard_enable_description").forEach { name ->
            assertTrue("Shared app shell must own $name", sharedStrings.contains("name=\"$name\""))
            listOf("apps/addiyon", "apps/textrevamp").forEach { module ->
                val productStrings = root.resolve("$module/src/main/res/values/strings.xml")
                    .readText()
                assertFalse(
                    "$module must not override common onboarding copy $name",
                    productStrings.contains("name=\"$name\"")
                )
            }
        }
    }

    @Test
    fun tourUpdateAndReviewInfrastructureIsOwnedByTheSharedShell() {
        val root = projectRoot()
        val productMain = root.resolve("apps/textrevamp/src/main/java")
        listOf("update", "review").forEach { packageName ->
            val localFiles = productMain.resolve("com/addiyon/keyboard/$packageName")
                .takeIf(File::exists)
                ?.walkTopDown()
                ?.filter { it.isFile && it.extension == "kt" }
                ?.toList()
                .orEmpty()
            assertTrue(
                "TextRevamp must not own the common $packageName implementation: $localFiles",
                localFiles.isEmpty()
            )
        }

        val sharedRoot = root.resolve(
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell"
        )
        listOf(
            "update/InAppUpdateController.kt",
            "update/PlayUpdatePlatform.kt",
            "update/UpdateLifecycleController.kt",
            "review/ReviewPromptController.kt",
            "review/PlayReviewPlatform.kt"
        ).forEach { relative ->
            assertTrue("Shared app shell is missing $relative", sharedRoot.resolve(relative).isFile)
        }
        assertTrue(
            "Review prompt policy belongs in shared keyboard preferences",
            root.resolve(
                "keyboard/preferences/src/main/java/com/addiyon/keyboard/review/" +
                    "ReviewPromptPolicy.kt"
            ).isFile
        )

        val sharedActivity = sharedRoot.resolve("KeyboardAppShellActivity.kt").readText()
        assertTrue(sharedActivity.contains("InAppUpdateController"))
        assertTrue(sharedActivity.contains("ReviewPromptController"))
        val sharedSetup = sharedRoot.resolve("KeyboardSetupActivity.kt").readText()
        assertTrue(sharedSetup.contains("data class KeyboardTourPage"))
        assertTrue(sharedSetup.contains("fun TourPager("))

        val textBuild = root.resolve("apps/textrevamp/build.gradle.kts").readText()
        val sharedBuild = root.resolve("features/app-shell/build.gradle.kts").readText()
        listOf("libs.play.review", "libs.play.app.update").forEach { dependency ->
            assertFalse("TextRevamp must not own $dependency", textBuild.contains(dependency))
            assertTrue("Shared app shell must own $dependency", sharedBuild.contains(dependency))
        }
    }

    @Test
    fun launcherAndTestHostWindowsShareOneThemeContract() {
        val root = projectRoot()
        val sharedManifest = parseManifest(
            root.resolve("features/app-shell/src/main/AndroidManifest.xml")
        )
        val sharedApplication = sharedManifest.elements("application").single()
        assertEquals(SHARED_CONTENT_THEME, sharedApplication.androidAttribute("theme"))
        val sharedLauncher = sharedManifest.elements("activity").single { it.isLauncher() }
        assertEquals(SHARED_SPLASH_THEME, sharedLauncher.androidAttribute("theme"))
        assertEquals(ADJUST_RESIZE, sharedLauncher.androidAttribute("windowSoftInputMode"))
        val products = listOf(
            ProductWindowContract(
                module = "apps/addiyon",
                testHostActivity = "com.addiyon.keyboard.AddiyonImeHostActivity"
            ),
            ProductWindowContract(
                module = "apps/textrevamp",
                testHostActivity = "com.addiyon.keyboard.benchmarkhost.ImeTestHostActivity"
            )
        )
        products.forEach { product ->
            val mainManifest = parseManifest(
                root.resolve("${product.module}/src/main/AndroidManifest.xml")
            )
            val application = mainManifest.elements("application").single()
            assertTrue(application.androidAttribute("theme").isEmpty())
            assertTrue(mainManifest.elements("activity").none { it.isLauncher() })

            val debugManifest = parseManifest(
                root.resolve("${product.module}/src/debug/AndroidManifest.xml")
            )
            val testHost = debugManifest.elements("activity").single {
                it.androidAttribute("name") == product.testHostActivity
            }
            assertEquals(SHARED_CONTENT_THEME, testHost.androidAttribute("theme"))
            assertEquals(ADJUST_RESIZE, testHost.androidAttribute("windowSoftInputMode"))
        }

        val sharedTheme = root.resolve("features/app-shell/src/main/res/values/themes.xml")
            .readText()
        assertTrue(sharedTheme.contains("name=\"Theme.KeyboardAppShell\""))
        assertTrue(sharedTheme.contains("name=\"Theme.KeyboardAppShell.Splash\""))
        assertTrue(
            root.resolve(
                "features/app-shell/src/main/res/drawable/keyboard_app_shell_splash.xml"
            ).isFile
        )
        val sharedAppShellActivity = root.resolve(
            "features/app-shell/src/main/java/com/addiyon/keyboard/features/appshell/" +
                "KeyboardAppShellActivity.kt"
        ).readText()
        assertTrue(
            "Every product shell must replace the splash theme before Compose renders",
            sharedAppShellActivity.contains(
                "contentThemeResource: Int = R.style.Theme_KeyboardAppShell"
            )
        )
        listOf("apps/addiyon", "apps/textrevamp").forEach { module ->
            val localWindowStyles = root.resolve("$module/src/main/res").walkTopDown()
                .filter { it.isFile && it.extension == "xml" }
                .joinToString("\n") { it.readText() }
            assertFalse(
                "$module must not redefine the shared content or splash style",
                localWindowStyles.contains("name=\"Theme.KeyboardAppShell")
            )
            assertFalse(
                "$module must not redefine the shared window background",
                localWindowStyles.contains("android:windowBackground")
            )
        }
    }

    @Test
    fun productsShareOneBackupAndDeviceTransferPolicy() {
        val root = projectRoot()
        val sharedApplication = parseManifest(
            root.resolve("features/app-shell/src/main/AndroidManifest.xml")
        ).elements("application").single()
        assertEquals("true", sharedApplication.androidAttribute("allowBackup"))
        assertEquals(
            "@xml/backup_rules",
            sharedApplication.androidAttribute("fullBackupContent")
        )
        assertEquals(
            "@xml/data_extraction_rules",
            sharedApplication.androidAttribute("dataExtractionRules")
        )
        listOf("apps/addiyon", "apps/textrevamp").forEach { module ->
            val application = parseManifest(
                root.resolve("$module/src/main/AndroidManifest.xml")
            ).elements("application").single()
            assertTrue(application.androidAttribute("allowBackup").isEmpty())
            assertTrue(application.androidAttribute("fullBackupContent").isEmpty())
            assertTrue(application.androidAttribute("dataExtractionRules").isEmpty())
            assertFalse(
                "$module must not own a product-local backup policy",
                root.resolve("$module/src/main/res/xml/backup_rules.xml").exists()
            )
            assertFalse(
                "$module must not own a product-local transfer policy",
                root.resolve("$module/src/main/res/xml/data_extraction_rules.xml").exists()
            )
        }

        val sharedResources = root.resolve("features/app-shell/src/main/res/xml")
        val backup = sharedResources.resolve("backup_rules.xml")
        val transfer = sharedResources.resolve("data_extraction_rules.xml")
        assertTrue(backup.isFile)
        assertTrue(transfer.isFile)
        assertEquals(1, backup.readText().backupPreferenceIncludes())
        assertEquals(2, transfer.readText().backupPreferenceIncludes())
        assertFalse(backup.readText().contains("textrevamp_ai_prefs.xml"))
        assertFalse(transfer.readText().contains("textrevamp_ai_prefs.xml"))
    }

    private fun String.backupPreferenceIncludes(): Int = Regex(
        "<include domain=\"sharedpref\" path=\"addiyon_keyboard_prefs.xml\""
    ).findAll(this).count()

    private data class ProductWindowContract(
        val module: String,
        val testHostActivity: String
    )

    private fun parseManifest(file: File): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)

    private fun Document.elements(tagName: String): List<Element> =
        getElementsByTagName(tagName).asElements()

    private fun NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun Element.isLauncher(): Boolean =
        getElementsByTagName("intent-filter").asElements().any { filter ->
            filter.getElementsByTagName("action").asElements().any {
                it.androidAttribute("name") == "android.intent.action.MAIN"
            } && filter.getElementsByTagName("category").asElements().any {
                it.androidAttribute("name") == "android.intent.category.LAUNCHER"
            }
        }

    private fun projectRoot(): File {
        var current = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
        while (!current.resolve("settings.gradle.kts").isFile) {
            current = checkNotNull(current.parentFile) { "Unable to locate project root" }
        }
        return current
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val SHARED_CONTENT_THEME = "@style/Theme.KeyboardAppShell"
        const val SHARED_SPLASH_THEME = "@style/Theme.KeyboardAppShell.Splash"
        const val ADJUST_RESIZE = "adjustResize"
    }
}
