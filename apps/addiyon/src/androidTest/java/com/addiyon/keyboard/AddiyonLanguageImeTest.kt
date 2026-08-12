package com.addiyon.keyboard

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.addiyon.keyboard.ui.SuggestionUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddiyonLanguageImeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val imeId = "${context.packageName}/com.addiyon.keyboard.AddiyonKeyboardService"
    private var originalIme = ""
    private var originalHardKeyboardSetting = ""
    private var imeWasEnabled = false

    @Before
    fun selectAddiyonIme() {
        originalIme = shell("settings get secure default_input_method").trim()
        originalHardKeyboardSetting =
            shell("settings get secure show_ime_with_hard_keyboard").trim()
        imeWasEnabled = shell("ime list -s").lineSequence().any { it.trim() == imeId }
        shell("settings put secure show_ime_with_hard_keyboard 1")
        shell("ime enable $imeId")
        shell("ime set $imeId")
        waitUntil { shell("settings get secure default_input_method").trim() == imeId }
    }

    @After
    fun restoreOriginalIme() {
        if (originalIme.isNotBlank() && originalIme != "null") {
            shell("ime set $originalIme")
        }
        if (!imeWasEnabled && originalIme != imeId) {
            shell("ime disable $imeId")
        }
        if (originalHardKeyboardSetting.isBlank() || originalHardKeyboardSetting == "null") {
            shell("settings delete secure show_ime_with_hard_keyboard")
        } else {
            shell(
                "settings put secure show_ime_with_hard_keyboard " +
                    originalHardKeyboardSetting
            )
        }
    }

    @Test
    fun cyclesAndTypesWithBothInternalLanguagePacks() {
        ActivityScenario.launch(AddiyonImeHostActivity::class.java).use { scenario ->
            waitUntil { PackKeyboardService.currentInstance != null }

            moveToLanguage("am-ET")
            assertLanguage("am-ET")
            scenario.clearAndFocus()
            waitUntil {
                currentLanguageId() == "am-ET" && !requireService().isLanguageSwitching
            }
            typeWord("selam")
            waitUntil {
                requireService().suggestionUiState is SuggestionUiState.WordCompletions
            }
            assertTrue(
                requireService().suggestions.any { suggestion ->
                    suggestion.any { it in 'ሀ'..'፿' }
                }
            )
            runService(PackKeyboardService::onSpace)
            waitUntil { scenario.text().any { it in 'ሀ'..'፿' } }
            runService(PackKeyboardService::onDelete)
            assertEquals("ሰላም", scenario.text())
            runService(PackKeyboardService::onDelete)
            assertEquals("ሰላ", scenario.text())

            runService(PackKeyboardService::toggleLanguage)
            waitForLanguage("en-US")
            assertLanguage("en-US")
            scenario.clearAndFocus()
            typeWord("hello")
            runService(PackKeyboardService::onSpace)
            waitUntil { scenario.text() == "hello " }

            runService(PackKeyboardService::toggleLanguage)
            waitForLanguage("am-ET")
            assertLanguage("am-ET")
        }
    }

    @Test
    fun showsOracleHardenedNominalSuggestions() {
        ActivityScenario.launch(AddiyonImeHostActivity::class.java).use { scenario ->
            waitUntil { PackKeyboardService.currentInstance != null }
            moveToLanguage("am-ET")
            val positiveCases = mapOf(
                "sew" to "ሰው",
                "sewn" to "ሰውን",
                "yesewn" to "የሰውን",
                "betoch" to "ቤቶች",
                "bietu" to "ቤቱ",
            )
            positiveCases.forEach { (raw, expected) ->
                scenario.clearAndFocus()
                typeWord(raw)
                waitUntil { expected in requireService().suggestions }
            }

            scenario.clearAndFocus()
            typeWord("le")
            waitUntil { requireService().suggestionUiState is SuggestionUiState.WordCompletions }
            assertTrue("ሌ" !in requireService().suggestions)

            scenario.clearAndFocus()
            typeWord("rE")
            waitUntil { requireService().suggestionUiState is SuggestionUiState.WordCompletions }
            assertTrue("ርዕ" !in requireService().suggestions)
        }
    }

    private fun moveToLanguage(target: String) {
        repeat(4) {
            if (currentLanguageId() == target) return
            val previous = currentLanguageId()
            runService(PackKeyboardService::toggleLanguage)
            waitUntil { currentLanguageId() != previous }
        }
        assertLanguage(target)
    }

    private fun typeWord(value: String) {
        runService(PackKeyboardService::resetShift)
        value.forEach { character ->
            runService { it.onCharacter(character.toString()) }
        }
    }

    private fun waitForLanguage(expected: String) {
        waitUntil { currentLanguageId() == expected }
    }

    private fun assertLanguage(expected: String) {
        assertEquals(expected, currentLanguageId())
    }

    private fun currentLanguageId(): String? =
        PackKeyboardService.currentInstance?.activeLanguageId?.value

    private fun requireService(): PackKeyboardService =
        requireNotNull(PackKeyboardService.currentInstance)

    private fun runService(action: (PackKeyboardService) -> Unit) {
        instrumentation.runOnMainSync { action(requireService()) }
        instrumentation.waitForIdleSync()
    }

    private fun ActivityScenario<AddiyonImeHostActivity>.clearAndFocus() {
        val previousSessionGeneration = requireService().editorGateway.sessionGeneration
        onActivity(AddiyonImeHostActivity::clearAndFocus)
        instrumentation.waitForIdleSync()
        waitUntil {
            var sessionReady = false
            onActivity { activity ->
                val gateway = PackKeyboardService.currentInstance?.editorGateway
                sessionReady =
                    activity.editor.hasFocus() &&
                    gateway?.currentToken() != null &&
                    gateway.sessionGeneration > previousSessionGeneration
            }
            sessionReady
        }
        SystemClock.sleep(INPUT_SETTLE_MILLIS)
        instrumentation.waitForIdleSync()
    }

    private fun ActivityScenario<AddiyonImeHostActivity>.text(): String {
        var value = ""
        onActivity { value = it.editor.text.toString() }
        return value
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Timed out waiting for Addiyon IME state", predicate())
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor =
            instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }

    private companion object {
        const val POLL_MILLIS = 50L
        const val INPUT_SETTLE_MILLIS = 75L
        const val WAIT_TIMEOUT_MILLIS = 20_000L
    }
}
