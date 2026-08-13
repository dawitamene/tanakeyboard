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
import org.junit.Assume.assumeTrue
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

    @Test
    fun predictsAnalyzerValidatedInflectedSurfacesAfterSpace() {
        ActivityScenario.launch(AddiyonImeHostActivity::class.java).use { scenario ->
            waitUntil { PackKeyboardService.currentInstance != null }
            moveToLanguage("am-ET")
            scenario.clearAndFocus()

            runService { it.commitText("ብዙ") }
            runService(PackKeyboardService::onSpace)
            waitUntil {
                requireService().suggestionsArePredictions &&
                    setOf("መምህራን", "ሰዎች", "ቤቶች").all(requireService().suggestions::contains)
            }

            assertEquals("ብዙ ", scenario.text())
            assertTrue("የነው" !in requireService().suggestions)
            assertTrue("ሃሃሃ" !in requireService().suggestions)
        }
    }

    @Test
    fun warmNominalSuggestionPublicationMeetsLatencyBudget() {
        ActivityScenario.launch(AddiyonImeHostActivity::class.java).use { scenario ->
            waitUntil { PackKeyboardService.currentInstance != null }
            moveToLanguage("am-ET")
            val cases = listOf(
                "sewn" to "ሰውን",
                "yesewn" to "የሰውን",
                "betoch" to "ቤቶች",
                "bietu" to "ቤቱ",
            )
            cases.forEach { (raw, expected) ->
                scenario.clearAndFocus()
                typeWord(raw)
                waitUntil { expected in requireService().suggestions }
            }

            val samples = buildList {
                repeat(5) {
                    cases.forEach { (raw, expected) ->
                        scenario.clearAndFocus()
                        typeWord(raw.dropLast(1))
                        waitUntil { requireService().suggestionUiState is SuggestionUiState.WordCompletions }
                        val previousGeneration = requireService().suggestionPublicationGeneration
                        val start = SystemClock.elapsedRealtimeNanos()
                        runService { it.onCharacter(raw.last().toString()) }
                        waitUntilFast {
                            requireService().suggestionPublicationGeneration > previousGeneration &&
                                expected in requireService().suggestions
                        }
                        add((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
                    }
                }
            }.sorted()
            val p95 = samples[((samples.size * 95 + 99) / 100 - 1).coerceIn(samples.indices)]
            val maximum = samples.last()

            println("PHASE4_METRIC connected_nominal_publication_p95_ms=$p95 max_ms=$maximum")
            assertTrue("warm nominal publication p95=${p95}ms samples=$samples", p95 <= 75.0)
            assertTrue("warm nominal publication max=${maximum}ms", maximum <= 200.0)
        }
    }

    @Test
    fun warmVerbSuggestionPublicationMeetsLatencyAndStabilityBudgets() {
        ActivityScenario.launch(AddiyonImeHostActivity::class.java).use { scenario ->
            waitUntil { PackKeyboardService.currentInstance != null }
            moveToLanguage("am-ET")
            val raw = "lemede"
            val expected = "ለመደ"
            scenario.clearAndFocus()
            typeWord(raw)
            waitUntil { expected in requireService().suggestions }

            val samples = buildList {
                repeat(20) {
                    scenario.clearAndFocus()
                    typeWord(raw.dropLast(1))
                    waitUntil { requireService().suggestionUiState is SuggestionUiState.WordCompletions }
                    val previousGeneration = requireService().suggestionPublicationGeneration
                    val start = SystemClock.elapsedRealtimeNanos()
                    runService { it.onCharacter(raw.last().toString()) }
                    waitUntilFast {
                        requireService().suggestionPublicationGeneration > previousGeneration &&
                            expected in requireService().suggestions
                    }
                    add((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
                }
            }.sorted()
            val p95 = samples[((samples.size * 95 + 99) / 100 - 1).coerceIn(samples.indices)]
            val maximum = samples.last()

            scenario.clearAndFocus()
            val shortPrefixGeneration = requireService().suggestionPublicationGeneration
            val shortPrefixStart = SystemClock.elapsedRealtimeNanos()
            typeWord("l")
            waitUntilFast {
                requireService().suggestionPublicationGeneration > shortPrefixGeneration &&
                    requireService().suggestionUiState is SuggestionUiState.WordCompletions
            }
            val shortPrefixMillis = (SystemClock.elapsedRealtimeNanos() - shortPrefixStart) / 1_000_000.0

            scenario.clearAndFocus()
            typeWord(raw)
            waitUntil { expected in requireService().suggestions }
            repeat(20) {
                val deleteGeneration = requireService().suggestionPublicationGeneration
                runService(PackKeyboardService::onDelete)
                waitUntilFast {
                    requireService().suggestionPublicationGeneration > deleteGeneration
                }
                val retypeGeneration = requireService().suggestionPublicationGeneration
                runService { it.onCharacter(raw.last().toString()) }
                waitUntilFast {
                    requireService().suggestionPublicationGeneration > retypeGeneration &&
                        expected in requireService().suggestions
                }
            }
            runService(PackKeyboardService::toggleLanguage)
            waitForLanguage("en-US")
            runService(PackKeyboardService::toggleLanguage)
            waitForLanguage("am-ET")
            scenario.clearAndFocus()
            typeWord(raw)
            waitUntil { expected in requireService().suggestions }

            println(
                "PHASE8_METRIC connected_verb_publication_p95_ms=$p95 " +
                    "max_ms=$maximum short_prefix_ms=$shortPrefixMillis"
            )
            assertTrue("warm verb publication p95=${p95}ms samples=$samples", p95 <= 75.0)
            assertTrue("warm verb publication max=${maximum}ms", maximum <= 200.0)
            assertTrue("one-character verb prefix=${shortPrefixMillis}ms", shortPrefixMillis <= 200.0)
        }
    }

    @Test
    fun sustainedMorphologyTypingKeepsMemoryBounded() {
        val requestedDuration = InstrumentationRegistry.getArguments().getString("phase9SoakMillis")
            ?.toLongOrNull()
            ?.coerceAtLeast(1L)
        assumeTrue("Run with phase9SoakMillis=600000 for the release soak", requestedDuration != null)
        ActivityScenario.launch(AddiyonImeHostActivity::class.java).use { scenario ->
            waitUntil { PackKeyboardService.currentInstance != null }
            moveToLanguage("am-ET")
            val cases = listOf(
                "lemede" to "ለመደ",
                "sewn" to "ሰውን",
                "yesewn" to "የሰውን",
            )
            cases.forEach { (raw, expected) ->
                scenario.clearAndFocus()
                typeWord(raw)
                waitUntil { expected in requireService().suggestions }
            }
            scenario.clearAndFocus()
            Runtime.getRuntime().gc()
            SystemClock.sleep(100)
            val initialHeap = usedHeapBytes()
            val initialPss = android.os.Debug.getPss() * 1_024L
            var maximumHeap = initialHeap
            var maximumPss = initialPss
            var iterations = 0
            val deadline = SystemClock.uptimeMillis() + requireNotNull(requestedDuration)
            while (SystemClock.uptimeMillis() < deadline) {
                val (raw, expected) = cases[iterations % cases.size]
                if (iterations > 0 && iterations % 50 == 0) scenario.clearAndFocus()
                typeWord(raw)
                waitUntil { expected in requireService().suggestions }
                runService(PackKeyboardService::onDelete)
                runService { it.onCharacter(raw.last().toString()) }
                waitUntil { expected in requireService().suggestions }
                runService(PackKeyboardService::onSpace)
                if (iterations % 20 == 0) {
                    maximumHeap = maxOf(maximumHeap, usedHeapBytes())
                    maximumPss = maxOf(maximumPss, android.os.Debug.getPss() * 1_024L)
                }
                iterations++
            }
            Runtime.getRuntime().gc()
            SystemClock.sleep(100)
            val finalHeap = usedHeapBytes()
            val finalPss = android.os.Debug.getPss() * 1_024L

            println(
                "PHASE9_METRIC soak_ms=$requestedDuration iterations=$iterations " +
                    "heap_initial=$initialHeap heap_max=$maximumHeap heap_final=$finalHeap " +
                    "pss_initial=$initialPss pss_max=$maximumPss pss_final=$finalPss"
            )
            assertTrue("heap grew from $initialHeap to $finalHeap", finalHeap <= initialHeap + 8L * 1_024 * 1_024)
            assertTrue("PSS grew from $initialPss to $finalPss", finalPss <= initialPss + 32L * 1_024 * 1_024)
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

    private fun waitUntilFast(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(2)
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

    private fun usedHeapBytes(): Long =
        Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

    private companion object {
        const val POLL_MILLIS = 50L
        const val INPUT_SETTLE_MILLIS = 75L
        const val WAIT_TIMEOUT_MILLIS = 20_000L
    }
}
