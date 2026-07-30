package com.addiyon.keyboard

import com.addiyon.keyboard.benchmarkhost.ImeFaultMode
import com.addiyon.keyboard.benchmarkhost.ImeMutationSnapshot
import com.addiyon.keyboard.benchmarkhost.ImeTestField
import com.addiyon.keyboard.benchmarkhost.ImeTestHostActivity
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.addiyon.keyboard.ui.SuggestionTap
import com.addiyon.keyboard.ui.SuggestionUiState
import com.addiyon.keyboard.telemetry.StoredTelemetryConsent
import com.addiyon.keyboard.telemetry.Telemetry
import com.addiyon.keyboard.telemetry.TelemetryBackend
import com.addiyon.keyboard.telemetry.TelemetryConsentStore
import com.addiyon.keyboard.telemetry.TelemetryEvent
import com.addiyon.keyboard.telemetry.TelemetryLayout
import com.addiyon.keyboard.telemetry.TelemetryLanguage
import com.addiyon.keyboard.telemetry.SanitizedNonFatal
import com.addiyon.keyboard.voice.isVoiceMode
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealImeCrashTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val imeId =
        "${context.packageName}/com.addiyon.keyboard.AddiyonKeyboardService"
    private var originalIme = ""
    private var originalHardKeyboardSetting = ""
    private var imeWasEnabled = false

    @Before
    fun selectTestIme() {
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
    fun restoreIme() {
        Telemetry.initialize(context, runtimeAllowed = false)
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
    fun realServiceTypesAcrossFieldKindsAndEditorActions() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            val samples = mapOf(
                ImeTestField.NORMAL to "normal",
                ImeTestField.MULTILINE to "multi",
                ImeTestField.SEARCH to "search",
                ImeTestField.SEND to "send",
                ImeTestField.DONE to "done",
                ImeTestField.EMAIL to "test@example.com",
                ImeTestField.URI to "https://example.com",
                ImeTestField.PASSWORD to "secret",
                ImeTestField.NUMBER to "12345",
                ImeTestField.PHONE to "251911234567"
            )
            samples.forEach { (kind, value) ->
                clearAndFocus(scenario, kind)
                invokeService { it.commitText(value) }
                waitUntil {
                    fieldText(scenario, kind) == value
                }
            }

            clearAndFocus(scenario, ImeTestField.PASSWORD)
            assertTrue(requireService().isPrivateField)
            invokeService(AddiyonKeyboardService::onVoiceInput)
            assertFalse(requireService().voiceUiState.isVoiceMode)
            assertTrue(requireService().suggestions.isEmpty())

            listOf(
                ImeTestField.SEARCH,
                ImeTestField.SEND,
                ImeTestField.DONE
            ).forEach { kind ->
                clearAndFocus(scenario, kind)
                val before = actionCount(scenario, kind)
                invokeService(AddiyonKeyboardService::onEnter)
                waitUntil { actionCount(scenario, kind) == before + 1 }
            }

            clearAndFocus(scenario, ImeTestField.MULTILINE)
            invokeService { it.commitText("line") }
            invokeService(AddiyonKeyboardService::onEnter)
            waitUntil {
                fieldText(scenario, ImeTestField.MULTILINE).contains('\n')
            }

            clearAndFocus(scenario, ImeTestField.NORMAL)
            val service = requireService()
            if (!service.isAmharic) invokeService(AddiyonKeyboardService::toggleLanguage)
            "selam".forEach { char -> invokeService { it.onCharacter(char.toString()) } }
            invokeService(AddiyonKeyboardService::onSpace)
            val amharicLength = fieldText(scenario, ImeTestField.NORMAL).length
            assertTrue(amharicLength > 1)
            invokeService(AddiyonKeyboardService::toggleLanguage)
            "hello".forEach { char -> invokeService { it.onCharacter(char.toString()) } }
            invokeService(AddiyonKeyboardService::onSpace)
            assertTrue(fieldText(scenario, ImeTestField.NORMAL).length > amharicLength)

            clearAndFocus(scenario, ImeTestField.NORMAL)
            invokeService(AddiyonKeyboardService::resetShift)
            invokeService(AddiyonKeyboardService::toggleShift)
            invokeService { it.onCharacter("a") }
            invokeService(AddiyonKeyboardService::toggleShift)
            invokeService(AddiyonKeyboardService::toggleShift)
            invokeService { it.onCharacter("b") }
            invokeService { it.onCharacter("c") }
            invokeService(AddiyonKeyboardService::toggleShift)
            invokeService { it.onCharacter(".") }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "ABC."
            }

            clearAndFocus(scenario, ImeTestField.NORMAL)
            invokeService(AddiyonKeyboardService::toggleNumberMode)
            invokeService { it.onCharacter("1") }
            invokeService(AddiyonKeyboardService::toggleSymbolsPage)
            invokeService { it.onCharacter("#") }
            invokeService(AddiyonKeyboardService::toggleSymbolsPage)
            invokeService { it.onCharacter("€") }
            invokeService(AddiyonKeyboardService::openKeypad)
            invokeService { it.onCharacter("2") }
            invokeService(AddiyonKeyboardService::toggleNumberMode)
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "1#€2"
            }

            clearAndFocus(scenario, ImeTestField.NORMAL)
            invokeService(AddiyonKeyboardService::resetShift)
            "cana".forEach { char -> invokeService { it.onCharacter(char.toString()) } }
            waitUntil { "Canada" in requireService().suggestions }
            invokeService { it.onSuggestionTapped("Canada") }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "Canada "
            }

            setTextAndSelection(scenario, ImeTestField.NORMAL, "abcdef", 2, 4)
            invokeService(AddiyonKeyboardService::onDelete)
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "abef"
            }

            clearAndFocus(scenario, ImeTestField.NORMAL)
            invokeService(AddiyonKeyboardService::openEmojiPanel)
            invokeService { it.commitEmoji("🙂") }
            invokeService(AddiyonKeyboardService::closeEmojiPanel)
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "🙂"
            }

            setTextAndSelection(scenario, ImeTestField.NORMAL, "abc", 3, 3)
            invokeService(AddiyonKeyboardService::onDeleteRepeatStart)
            repeat(3) {
                invokeService(AddiyonKeyboardService::onDelete)
            }
            invokeService(AddiyonKeyboardService::onDeleteRepeatEnd)
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL).isEmpty()
            }
        }
    }

    @Test
    fun insertingInsideComposingWordKeepsWholeWordAsSuggestionQuery() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)
            "inorm".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "inorm"
            }

            moveSelection(scenario, ImeTestField.NORMAL, 2)
            invokeService { it.onCharacter("f") }

            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "inform"
            }
            waitUntil {
                requireService().suggestions.contains("information")
            }

            invokeService { it.onSuggestionTapped("information") }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "information "
            }
        }
    }

    /**
     * Regression: tapping a completion worked once, then silently stopped
     * replacing the word after the field was cleared without refocusing (a host
     * app's clear button, select-all-delete). Taps go through the UI path here
     * -- generation captured from the composed state, the way a chip's onClick
     * lambda captures it -- because the String overload re-reads the generation
     * at tap time and so can never observe a staleness bug.
     */
    @Test
    fun suggestionTapStillReplacesTheWordAfterTheFieldIsCleared() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)

            repeat(3) { round ->
                "infor".forEach { char ->
                    invokeService { it.onCharacter(char.toString()) }
                }
                waitUntil {
                    requireService().suggestionUiState is
                        SuggestionUiState.WordCompletions
                }

                // Tap whichever completion the strip actually offers, so the
                // test measures the commit mechanism rather than dictionary
                // ranking.
                lateinit var tap: SuggestionTap
                var word = ""
                invokeService { service ->
                    val state =
                        service.suggestionUiState as SuggestionUiState.WordCompletions
                    word = state.words.first()
                    tap = SuggestionTap(word, state.actionGeneration)
                }
                invokeService { it.onSuggestionTapped(tap) }
                SystemClock.sleep(600)
                instrumentation.waitForIdleSync()
                assertEquals(
                    "round $round did not replace the composing word",
                    "$word ",
                    fieldText(scenario, ImeTestField.NORMAL)
                )

                // Clear the way a host app's own clear button does: mutate the
                // field directly, with no refocus and so no new input session.
                scenario.onActivity { it.field(ImeTestField.NORMAL).text.clear() }
                instrumentation.waitForIdleSync()
            }
        }
    }

    /**
     * Regression: an editor whose getSurroundingText reply can't say where its
     * window sits (offset -1) -- the framework default, so every Compose text
     * field, including this app's own test field. That -1 was being read back
     * as a real document position, which made every absolute offset we derived
     * short by the before-window, so the gateway rejected every read: the
     * composer abandoned the word on each keystroke and suggestions restarted
     * from the last letter typed ("inf" offering words starting with "f"),
     * chips had nothing to replace, and no n-gram context was ever captured for
     * next-word predictions. One word typed must stay one word.
     */
    @Test
    fun typingStaysOnOneWordWhenTheEditorCannotPlaceItsSurroundingText() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            focusFault(scenario, ImeFaultMode.UNKNOWN_SURROUNDING_OFFSET)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)

            "inf".forEachIndexed { index, char ->
                invokeService { it.onCharacter(char.toString()) }
                settle()
                assertEquals(
                    "buffer after ${index + 1} keystrokes",
                    "inf".take(index + 1),
                    composingRaw()
                )
            }
            assertEquals("inf", fieldText(scenario, ImeTestField.FAULT))

            waitUntil {
                requireService().suggestionUiState is SuggestionUiState.WordCompletions
            }
            val completions = completionWords()
            assertTrue(
                "completions must answer the whole word, got $completions",
                completions.isNotEmpty() &&
                    completions.all { it.lowercase().startsWith("inf") }
            )

            // Tap-to-replace, then the next-word predictions that follow it.
            lateinit var tap: SuggestionTap
            var word = ""
            invokeService { service ->
                val state =
                    service.suggestionUiState as SuggestionUiState.WordCompletions
                word = state.words.first()
                tap = SuggestionTap(word, state.actionGeneration)
            }
            invokeService { it.onSuggestionTapped(tap) }
            waitUntil { fieldText(scenario, ImeTestField.FAULT) == "$word " }

            waitUntil {
                val state = requireService().suggestionUiState
                state is SuggestionUiState.NextWordPredictions && state.words.isNotEmpty()
            }
        }
    }

    /**
     * Regression: on an editor that applies setComposingText but never echoes
     * SPAN_COMPOSING back through its reads, the composer's self-check treated
     * "the editor didn't tell me" as "the editor disagrees with me" and rejected
     * every chip tap -- silently, so the typed word just sat there. The IME set
     * that region and is the authority on it; absent bounds must not veto.
     */
    @Test
    fun suggestionTapReplacesTheWordOnAnEditorThatHidesComposingSpans() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            focusFault(scenario, ImeFaultMode.PLAIN_TEXT_READS)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)

            // Repeat across a field clear: the first tap can be rescued by the
            // committed-word fallback, but once the field is cleared that
            // snapshot's offsets are stale, so only a working composing path
            // keeps later taps alive.
            repeat(3) { round ->
                "infor".forEach { char ->
                    invokeService { it.onCharacter(char.toString()) }
                }
                waitUntil {
                    requireService().suggestionUiState is
                        SuggestionUiState.WordCompletions
                }

                lateinit var tap: SuggestionTap
                var word = ""
                invokeService { service ->
                    val state =
                        service.suggestionUiState as SuggestionUiState.WordCompletions
                    word = state.words.first()
                    tap = SuggestionTap(word, state.actionGeneration)
                }
                invokeService { it.onSuggestionTapped(tap) }

                waitUntil { fieldText(scenario, ImeTestField.FAULT) == "$word " }
                assertEquals(
                    "round $round did not replace the composing word",
                    "$word ",
                    fieldText(scenario, ImeTestField.FAULT)
                )

                scenario.onActivity { it.field(ImeTestField.FAULT).text.clear() }
                instrumentation.waitForIdleSync()
            }
        }
    }

    @Test
    fun suggestionTapSurvivesASelectionCallbackArrivingBeforeTheFinger() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)

            "infor".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }
            waitUntil {
                requireService().suggestionUiState is SuggestionUiState.WordCompletions
            }

            // The chip's onClick captured this generation when the strip last
            // composed.
            lateinit var tap: SuggestionTap
            var word = ""
            invokeService { service ->
                val state =
                    service.suggestionUiState as SuggestionUiState.WordCompletions
                word = state.words.first()
                tap = SuggestionTap(word, state.actionGeneration)
            }

            // The platform delivers a selection callback between the strip
            // composing and the finger landing -- routine, and it does not
            // change the field at all.
            invokeService { service ->
                service.onUpdateSelection(5, 5, 5, 5, 0, 5)
            }
            waitUntil {
                requireService().suggestionUiState is SuggestionUiState.WordCompletions
            }

            invokeService { it.onSuggestionTapped(tap) }
            waitUntil { fieldText(scenario, ImeTestField.NORMAL) == "$word " }
            assertEquals("$word ", fieldText(scenario, ImeTestField.NORMAL))
        }
    }

    @Test
    fun staleSuggestionTapCannotMutateANewerStateOrPrivateField() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)
            "info".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }
            waitUntil {
                val state = requireService().suggestionUiState
                state is SuggestionUiState.WordCompletions &&
                    "information" in state.words
            }

            lateinit var firstTap: SuggestionTap
            invokeService { service ->
                val state =
                    service.suggestionUiState as SuggestionUiState.WordCompletions
                firstTap = SuggestionTap("information", state.actionGeneration)
            }
            invokeService { it.onCharacter("r") }
            waitUntil {
                val state = requireService().suggestionUiState
                state is SuggestionUiState.WordCompletions &&
                    "information" in state.words &&
                    state.actionGeneration != firstTap.actionGeneration
            }
            val newerText = fieldText(scenario, ImeTestField.NORMAL)
            invokeService { it.onSuggestionTapped(firstTap) }
            assertEquals(newerText, fieldText(scenario, ImeTestField.NORMAL))

            lateinit var fieldTap: SuggestionTap
            invokeService { service ->
                val state =
                    service.suggestionUiState as SuggestionUiState.WordCompletions
                fieldTap = SuggestionTap("information", state.actionGeneration)
            }
            setTextAndSelection(
                scenario = scenario,
                kind = ImeTestField.PASSWORD,
                value = "secret",
                start = 6,
                end = 6
            )
            assertTrue(requireService().isPrivateField)
            invokeService { it.onSuggestionTapped(fieldTap) }
            assertEquals("secret", fieldText(scenario, ImeTestField.PASSWORD))
        }
    }

    @Test
    fun realEnglishAndAmharicPredictionsPublishAndCommitOnce() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)
            invokeService { it.onCharacter("a") }
            invokeService(AddiyonKeyboardService::onSpace)
            waitUntil {
                val state = requireService().suggestionUiState
                state is SuggestionUiState.NextWordPredictions && state.words.isNotEmpty()
            }

            lateinit var englishTap: SuggestionTap
            val englishPrefix = fieldText(scenario, ImeTestField.NORMAL)
            invokeService { service ->
                val state =
                    service.suggestionUiState as SuggestionUiState.NextWordPredictions
                englishTap = SuggestionTap(state.words.first(), state.actionGeneration)
            }
            invokeService { it.onSuggestionTapped(englishTap) }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) ==
                    englishPrefix + englishTap.word + " "
            }

            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (!requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            "endiet".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }
            invokeService(AddiyonKeyboardService::onSpace)
            waitUntil {
                val state = requireService().suggestionUiState
                state is SuggestionUiState.NextWordPredictions && "ነው" in state.words
            }
            lateinit var amharicTap: SuggestionTap
            invokeService { service ->
                val state =
                    service.suggestionUiState as SuggestionUiState.NextWordPredictions
                amharicTap = SuggestionTap("ነው", state.actionGeneration)
            }
            invokeService { it.onSuggestionTapped(amharicTap) }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "እንዴት ነው "
            }
        }
    }

    @Test
    fun sameCaretHostReplacementWhilePredictionIsPendingRejectsOldPublication() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)
            "aaron".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }

            scenario.onActivity { activity ->
                val service = requireNotNull(AddiyonKeyboardService.currentInstance)
                service.onSpace()
                assertTrue(service.suggestionUiState is SuggestionUiState.LoadingPredictions)
                val beforeRewrite = requireNotNull(service.editorGateway.currentToken())
                val field = activity.field(ImeTestField.NORMAL)
                field.editableText.replace(0, field.length(), "abide ")
                field.setSelection("abide ".length)
                assertTrue(
                    requireNotNull(service.editorGateway.currentToken())
                        .sameEditorState(beforeRewrite)
                )
            }
            waitUntil {
                val state = requireService().suggestionUiState
                assertFalse(
                    state is SuggestionUiState.NextWordPredictions &&
                        "carter" in state.words
                )
                state is SuggestionUiState.NextWordPredictions && "by" in state.words
            }

            assertEquals("abide ", fieldText(scenario, ImeTestField.NORMAL))
        }
    }

    @Test
    fun sameCaretHostReplacementWithoutASelectionCallbackRejectsPredictionTap() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)
            "aaron".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }
            invokeService(AddiyonKeyboardService::onSpace)
            waitUntil {
                val state = requireService().suggestionUiState
                state is SuggestionUiState.NextWordPredictions && "carter" in state.words
            }

            val sessionGeneration = requireService().editorGateway.sessionGeneration
            lateinit var staleTap: SuggestionTap
            invokeService { service ->
                val state =
                    service.suggestionUiState as SuggestionUiState.NextWordPredictions
                staleTap = SuggestionTap("carter", state.actionGeneration)
            }
            scenario.onActivity { activity ->
                val service = requireNotNull(AddiyonKeyboardService.currentInstance)
                val beforeRewrite = requireNotNull(service.editorGateway.currentToken())
                val field = activity.field(ImeTestField.NORMAL)
                field.editableText.replace(0, field.length(), "abide ")
                field.setSelection("abide ".length)
                assertTrue(
                    requireNotNull(service.editorGateway.currentToken())
                        .sameEditorState(beforeRewrite)
                )
                service.onSuggestionTapped(staleTap)
                assertEquals("abide ", field.text.toString())
            }
            waitUntil {
                val state = requireService().suggestionUiState
                state is SuggestionUiState.NextWordPredictions &&
                    "by" in state.words
            }

            assertEquals(
                sessionGeneration,
                requireService().editorGateway.sessionGeneration
            )
            assertEquals("abide ", fieldText(scenario, ImeTestField.NORMAL))
        }
    }

    @Test
    fun composingPrefixesAndBackspacesCapturePriorContextOnlyOncePerWord() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            focusFault(scenario, ImeFaultMode.NONE)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            setFaultText(scenario, "prior ", start = 6)
            scenario.onActivity {
                it.faultField.contextSnapshotReads.set(0)
            }

            "about".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }
            repeat(3) {
                invokeService(AddiyonKeyboardService::onDelete)
            }
            "out".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }

            var contextReads = 0
            scenario.onActivity {
                contextReads = it.faultField.contextSnapshotReads.get()
            }
            assertEquals(1, contextReads)
            assertEquals(
                "prior about",
                fieldText(scenario, ImeTestField.FAULT)
            )
        }
    }

    @Test
    fun deletingAfterAmharicSuggestionKeepsFidelAndDeletesOneLetter() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (!requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            "selam".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }
            waitUntil { "ሰላም" in requireService().suggestions }
            invokeService { it.onSuggestionTapped("ሰላም") }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "ሰላም "
            }

            invokeService(AddiyonKeyboardService::onDelete)
            assertEquals("ሰላም", fieldText(scenario, ImeTestField.NORMAL))
            invokeService(AddiyonKeyboardService::onDelete)

            assertEquals("ሰላ", fieldText(scenario, ImeTestField.NORMAL))
        }
    }

    @Test
    fun deletingFromCommittedAmharicWordShowsSuggestionsForRemainingPrefix() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (!requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            "endiet".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }
            invokeService(AddiyonKeyboardService::onSpace)
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "እንዴት "
            }

            invokeService(AddiyonKeyboardService::onDelete)
            invokeService(AddiyonKeyboardService::onDelete)

            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "እንዴ"
            }
            waitUntil {
                requireService().suggestions.any { it.startsWith("እንዴ") }
            }
            invokeService { it.onSuggestionTapped("እንዴት") }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "እንዴት "
            }
        }
    }

    @Test
    fun tappingCommittedAmharicNeverRestoresItsLatinBuffer() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (!requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            "selam".forEach { char ->
                invokeService { it.onCharacter(char.toString()) }
            }
            waitUntil { "ሰላም" in requireService().suggestions }
            invokeService { it.onSuggestionTapped("ሰላም") }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "ሰላም "
            }

            moveSelection(scenario, ImeTestField.NORMAL, 3)
            assertEquals("ሰላም ", fieldText(scenario, ImeTestField.NORMAL))

            moveSelection(scenario, ImeTestField.NORMAL, 1)
            assertEquals("ሰላም ", fieldText(scenario, ImeTestField.NORMAL))
        }
    }

    @Test
    fun selectionChangesNeverMutateCommittedUnicodeText() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            val block =
                "English  mixed\tline\nሰላም\u00A0don't,  punctuation! 🙂 e\u0301 እንዴት\n"
            val original = block.repeat(LARGE_UNICODE_BLOCKS)
            setTextAndSelection(
                scenario = scenario,
                kind = ImeTestField.NORMAL,
                value = original,
                start = original.length,
                end = original.length
            )
            scenario.onActivity {
                it.resetMutationLedger(ImeTestField.NORMAL)
            }

            assertTrue(original.length >= 4_000)
            val middleBlockStart = block.length * (LARGE_UNICODE_BLOCKS / 2)
            val lastBlockStart = original.length - block.length
            val emojiStart = middleBlockStart + block.indexOf("🙂")
            val combiningStart = middleBlockStart + block.indexOf("e\u0301")
            val amharicStart = lastBlockStart + block.indexOf("ሰላም")
            val whitespaceStart = middleBlockStart + block.indexOf("  mixed")
            val forwardCarets = (0..8).map { original.length * it / 8 }
            val selectionChanges =
                forwardCarets.map { it to it } +
                    forwardCarets.asReversed().map { it to it } +
                    listOf(
                        whitespaceStart to whitespaceStart + 2,
                        whitespaceStart + 2 to whitespaceStart,
                        emojiStart to emojiStart + "🙂".length,
                        emojiStart + "🙂".length to emojiStart,
                        combiningStart to combiningStart + "e\u0301".length,
                        combiningStart + "e\u0301".length to combiningStart,
                        amharicStart to amharicStart + "ሰላም".length,
                        amharicStart + "ሰላም".length to amharicStart,
                        0 to original.length,
                        original.length to 0
                    )

            selectionChanges.forEach { (start, end) ->
                scenario.onActivity {
                    it.field(ImeTestField.NORMAL).setSelection(start, end)
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(SELECTION_SETTLE_MILLIS)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(INPUT_SETTLE_MILLIS)

            val actual = fieldText(scenario, ImeTestField.NORMAL)
            var snapshot: ImeMutationSnapshot? = null
            scenario.onActivity {
                snapshot = it.mutationSnapshot(ImeTestField.NORMAL)
            }
            val mutations = requireNotNull(snapshot)
            assertArrayEquals(
                original.toByteArray(StandardCharsets.UTF_16LE),
                actual.toByteArray(StandardCharsets.UTF_16LE)
            )
            assertEquals(mutations.operations.toString(), 0, mutations.contentMutationCount)
            assertEquals(mutations.operations.toString(), 0, mutations.selectionMutationCount)
        }
    }

    @Test
    fun composingRegionFaultsInsertExplicitCharacterAtLiveCaret() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            focusFault(scenario, ImeFaultMode.NONE)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }

            composingRegionFaultModes.forEach { mode ->
                focusFault(scenario, mode)
                setFaultText(scenario, "abcd", start = 2)
                invokeService(AddiyonKeyboardService::resetShift)
                invokeService { it.onCharacter("x") }
                waitUntil {
                    fieldText(scenario, ImeTestField.FAULT) == "abxcd"
                }
            }
        }
    }

    @Test
    fun composingRegionFaultsDeleteBeforeLiveCaret() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            focusFault(scenario, ImeFaultMode.NONE)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }

            composingRegionFaultModes.forEach { mode ->
                focusFault(scenario, mode)
                setFaultText(scenario, "abcd", start = 2)
                invokeService(AddiyonKeyboardService::onDelete)
                waitUntil {
                    fieldText(scenario, ImeTestField.FAULT) == "acd"
                }
            }
        }
    }

    @Test
    fun hostileInputConnectionsFailClosedWithoutCrashingOrLooping() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            focusFault(scenario, ImeFaultMode.NONE)
            invokeService { it.onCharacter("a") }
            waitUntil { fieldText(scenario, ImeTestField.FAULT).length == 1 }

            val beforeRejected = fieldText(scenario, ImeTestField.FAULT)
            focusFault(scenario, ImeFaultMode.REJECT_MUTATIONS)
            invokeService { it.onCharacter("b") }
            invokeService { it.onCharacter("c") }
            assertEquals(beforeRejected, fieldText(scenario, ImeTestField.FAULT))
            assertNotNull(AddiyonKeyboardService.currentInstance)

            focusFault(scenario, ImeFaultMode.NULL_READS)
            setFaultText(scenario, "x")
            invokeService(AddiyonKeyboardService::onDelete)
            waitUntil { fieldText(scenario, ImeTestField.FAULT).isEmpty() }

            focusFault(scenario, ImeFaultMode.THROW_ALL)
            invokeService { it.onCharacter("x") }
            invokeService(AddiyonKeyboardService::onSpace)
            invokeService(AddiyonKeyboardService::onDelete)
            invokeService(AddiyonKeyboardService::onEnter)
            invokeService { it.commitText("ignored") }
            assertNotNull(AddiyonKeyboardService.currentInstance)

            focusFault(scenario, ImeFaultMode.SLOW_READS)
            setFaultText(scenario, "slow")
            invokeService(AddiyonKeyboardService::onSpace)
            assertFalse(requireService().editorGateway.allowsOptionalReads)

            focusFault(scenario, ImeFaultMode.NONE)
            setFaultText(scenario, String(charArrayOf('\uD83D')))
            invokeService(AddiyonKeyboardService::onDelete)
            waitUntil { fieldText(scenario, ImeTestField.FAULT).isEmpty() }

            invokeService { it.onCharacter("a") }
            invokeService { it.onCharacter("b") }
            scenario.onActivity { it.finalizeFaultComposition() }
            invokeService(AddiyonKeyboardService::onDelete)
            assertNotNull(AddiyonKeyboardService.currentInstance)
        }
    }

    @Test
    fun serviceSurvivesVisibilityFieldSwitchRecreationAndImeSwitching() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            repeat(VISIBILITY_CYCLES) {
                scenario.onActivity { activity ->
                    val field = activity.field(ImeTestField.NORMAL)
                    val manager = activity.getSystemService(InputMethodManager::class.java)
                    manager?.hideSoftInputFromWindow(field.windowToken, 0)
                    manager?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            repeat(FIELD_SWITCH_CYCLES) { index ->
                focus(
                    scenario,
                    if (index % 2 == 0) ImeTestField.NORMAL else ImeTestField.EMAIL
                )
            }
            assertNotNull(AddiyonKeyboardService.currentInstance)

            scenario.recreate()
            clearAndFocus(scenario, ImeTestField.NORMAL)
            invokeService { it.commitText("after-recreate") }
            waitUntil {
                fieldText(scenario, ImeTestField.NORMAL) == "after-recreate"
            }

            val alternate = shell("ime list -s")
                .lineSequence()
                .map(String::trim)
                .firstOrNull { it.isNotBlank() && it != imeId }
            if (alternate != null) {
                shell("ime set $alternate")
                waitUntil {
                    shell("settings get secure default_input_method").trim() == alternate
                }
                shell("ime set $imeId")
                waitUntil {
                    shell("settings get secure default_input_method").trim() == imeId
                }
                clearAndFocus(scenario, ImeTestField.NORMAL)
                invokeService { it.commitText("after-switch") }
                waitUntil {
                    fieldText(scenario, ImeTestField.NORMAL) == "after-switch"
                }
            }
        }
    }

    @Test
    fun telemetryEmitsOncePerNonRestartingNonPrivateSessionAndOnlyTypedActions() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            val backend = RecordingTelemetryBackend()
            Telemetry.installForTesting(
                store = FixedTelemetryStore(),
                backend = backend,
                runtimeAllowed = true
            )

            focusCurrentIme(scenario, ImeTestField.EMAIL)
            waitUntil {
                backend.events.filterIsInstance<TelemetryEvent.ImeSessionStart>().size == 1
            }
            scenario.onActivity { activity ->
                activity.getSystemService(InputMethodManager::class.java)
                    ?.restartInput(activity.field(ImeTestField.EMAIL))
            }
            SystemClock.sleep(INPUT_SETTLE_MILLIS)
            instrumentation.waitForIdleSync()

            assertEquals(
                1,
                backend.events.filterIsInstance<TelemetryEvent.ImeSessionStart>().size
            )
            invokeService(AddiyonKeyboardService::openEmojiPanel)
            assertEquals(
                listOf(TelemetryEvent.LayoutOpen(TelemetryLayout.EMOJI)),
                backend.events.filterIsInstance<TelemetryEvent.LayoutOpen>()
            )

            focusCurrentIme(scenario, ImeTestField.PASSWORD)
            assertEquals(
                1,
                backend.events.filterIsInstance<TelemetryEvent.ImeSessionStart>().size
            )
            assertEquals(
                TelemetryLanguage.ENGLISH,
                backend.events.filterIsInstance<TelemetryEvent.ImeSessionStart>()
                    .single()
                    .language
            )
        }
    }

    /**
     * The composing buffer must accumulate across keystrokes: typing i then n is
     * the word "in", so the strip must be answering the whole buffer, not just
     * the last key.
     */
    @Test
    fun suggestionsAnswerTheWholeTypedWordNotJustTheLastKey() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            useEnglish(scenario)

            invokeService { it.onCharacter("i") }
            invokeService { it.onCharacter("n") }
            waitUntil { fieldText(scenario, ImeTestField.NORMAL) == "in" }
            settle()

            assertEquals("in", composingRaw())
            val words = completionWords()
            assertTrue(
                "expected completions of 'in', got $words",
                words.isNotEmpty() && words.all { it.startsWith("in", ignoreCase = true) }
            )
        }
    }

    /**
     * Backspacing back to empty and typing a fresh letter must leave the buffer
     * holding exactly that letter -- no residue from the abandoned word. The
     * field going empty re-arms auto-capitalization (deliberate: see
     * InputTypePolicy.allowsAutoCap), so the letter lands capitalized and the
     * strip must be answering that capitalized buffer, not a stale one.
     */
    @Test
    fun clearingWithBackspaceThenTypingStartsACleanWord() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            useEnglish(scenario)

            invokeService { it.onCharacter("i") }
            waitUntil { fieldText(scenario, ImeTestField.NORMAL).isNotEmpty() }
            invokeService(AddiyonKeyboardService::onDelete)
            waitUntil { fieldText(scenario, ImeTestField.NORMAL).isEmpty() }
            settle()

            invokeService { it.onCharacter("n") }
            settle()

            val field = fieldText(scenario, ImeTestField.NORMAL)
            assertEquals("field must hold exactly the new letter", 1, field.length)
            assertTrue("expected n or N, got '$field'", field.equals("n", ignoreCase = true))
            assertEquals("buffer must match the field", field, composingRaw())

            val words = completionWords()
            assertTrue(
                "expected completions of '$field', got $words",
                words.isNotEmpty() && words.all { it.startsWith(field, ignoreCase = true) }
            )
        }
    }

    /**
     * The buffer the strip answers must track the field exactly, keystroke by
     * keystroke, through inserts, backspaces and caret moves. Drift here is what
     * makes suggestions look "not cursor aware" -- the strip answers a word that
     * is no longer what is under the caret.
     */
    @Test
    fun composingBufferTracksTheFieldThroughEditsAndCaretMoves() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            useEnglish(scenario)

            "keyboard".forEachIndexed { index, char ->
                invokeService { it.onCharacter(char.toString()) }
                settle()
                val field = fieldText(scenario, ImeTestField.NORMAL)
                assertEquals(
                    "field length after ${index + 1} keystrokes",
                    index + 1,
                    field.length
                )
                assertEquals(
                    "buffer must equal the field at keystroke ${index + 1}",
                    field,
                    composingRaw()
                )
            }

            repeat(3) {
                invokeService(AddiyonKeyboardService::onDelete)
                settle()
                assertEquals(
                    "buffer must equal the field after backspace",
                    fieldText(scenario, ImeTestField.NORMAL),
                    composingRaw()
                )
            }

            // Caret into the middle of the word, then insert.
            moveSelection(scenario, ImeTestField.NORMAL, 3)
            settle()
            invokeService { it.onCharacter("z") }
            settle()
            assertEquals(
                "buffer must equal the field after a mid-word insert",
                fieldText(scenario, ImeTestField.NORMAL),
                composingRaw()
            )

            val words = completionWords()
            val field = fieldText(scenario, ImeTestField.NORMAL)
            assertTrue(
                "strip must answer the whole word '$field', got $words",
                words.isEmpty() || words.all { it.startsWith(field, ignoreCase = true) }
            )
        }
    }

    /**
     * Clearing the field from the app side (a clear button) must not leave the
     * composer holding the old word: the next keystroke starts fresh.
     */
    @Test
    fun clearingTheFieldFromTheAppThenTypingStartsACleanWord() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            useEnglish(scenario)

            "in".forEach { char -> invokeService { it.onCharacter(char.toString()) } }
            waitUntil { fieldText(scenario, ImeTestField.NORMAL) == "in" }

            scenario.onActivity { it.field(ImeTestField.NORMAL).text.clear() }
            settle()

            invokeService { it.onCharacter("n") }
            waitUntil { fieldText(scenario, ImeTestField.NORMAL) == "n" }
            settle()

            assertEquals("n", composingRaw())
            assertEquals("n", fieldText(scenario, ImeTestField.NORMAL))
        }
    }

    /**
     * Moving the caret into the middle of the word being typed and inserting
     * must keep the WHOLE word as the lookup key, not the fragment left of the
     * caret.
     */
    @Test
    fun insertingMidWordKeepsTheWholeWordAsTheSuggestionKey() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            useEnglish(scenario)

            "inorm".forEach { char -> invokeService { it.onCharacter(char.toString()) } }
            waitUntil { fieldText(scenario, ImeTestField.NORMAL) == "inorm" }

            moveSelection(scenario, ImeTestField.NORMAL, 2)
            invokeService { it.onCharacter("f") }
            waitUntil { fieldText(scenario, ImeTestField.NORMAL) == "inform" }
            settle()

            assertEquals("inform", composingRaw())
            val words = completionWords()
            assertTrue(
                "expected completions of 'inform', got $words",
                words.any { it.startsWith("inform", ignoreCase = true) }
            )
        }
    }

    /**
     * Walking the caret back onto an already-committed word must adopt it, so
     * the strip answers that word rather than going blank or answering the
     * previous one.
     */
    @Test
    fun caretMovingOntoACommittedWordMakesTheStripAnswerThatWord() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            useEnglish(scenario)

            "inform".forEach { char -> invokeService { it.onCharacter(char.toString()) } }
            invokeService(AddiyonKeyboardService::onSpace)
            waitUntil { fieldText(scenario, ImeTestField.NORMAL) == "inform " }
            settle()

            moveSelection(scenario, ImeTestField.NORMAL, 6)
            settle()

            val words = completionWords()
            assertTrue(
                "expected completions of 'inform' after caret walk-back, got $words",
                words.any { it.startsWith("inform", ignoreCase = true) }
            )
        }
    }

    /**
     * Same continuity contract in Amharic, where the composer transliterates and
     * is discard-on-exit: the raw Latin buffer must still track what the caret
     * is sitting on through inserts, backspaces and a clear.
     */
    @Test
    fun amharicComposingBufferTracksEditsAndSurvivesAClear() {
        ActivityScenario.launch(ImeTestHostActivity::class.java).use { scenario ->
            clearAndFocus(scenario, ImeTestField.NORMAL)
            if (!requireService().isAmharic) {
                invokeService(AddiyonKeyboardService::toggleLanguage)
            }
            waitUntil { !requireService().isLanguageSwitching }
            invokeService(AddiyonKeyboardService::resetShift)

            "selam".forEachIndexed { index, char ->
                invokeService { it.onCharacter(char.toString()) }
                settle()
                assertEquals(
                    "raw buffer after ${index + 1} Amharic keystrokes",
                    "selam".take(index + 1),
                    composingRaw()
                )
            }

            invokeService(AddiyonKeyboardService::onDelete)
            settle()
            assertEquals("sela", composingRaw())

            // Host app clears the field out from under us.
            scenario.onActivity { it.field(ImeTestField.NORMAL).text.clear() }
            settle()

            invokeService { it.onCharacter("n") }
            settle()
            assertEquals(
                "a clear must not leave Amharic residue in the buffer",
                "n",
                composingRaw()
            )
        }
    }

    private fun useEnglish(scenario: ActivityScenario<ImeTestHostActivity>) {
        clearAndFocus(scenario, ImeTestField.NORMAL)
        if (requireService().isAmharic) {
            invokeService(AddiyonKeyboardService::toggleLanguage)
        }
        waitUntil { !requireService().isLanguageSwitching }
        invokeService(AddiyonKeyboardService::resetShift)
    }

    private fun settle() {
        SystemClock.sleep(400)
        instrumentation.waitForIdleSync()
    }

    private fun composingRaw(): String {
        var raw = ""
        invokeService { raw = it.composingBufferForTest }
        return raw
    }

    private fun completionWords(): List<String> {
        var words = emptyList<String>()
        invokeService { service ->
            words = when (val state = service.suggestionUiState) {
                is SuggestionUiState.WordCompletions -> state.words
                else -> emptyList()
            }
        }
        return words
    }

    private fun clearAndFocus(
        scenario: ActivityScenario<ImeTestHostActivity>,
        kind: ImeTestField
    ) {
        scenario.onActivity { activity ->
            activity.field(kind).text.clear()
        }
        focus(scenario, kind)
    }

    private fun focus(
        scenario: ActivityScenario<ImeTestHostActivity>,
        kind: ImeTestField
    ) {
        var previousSessionGeneration = 0L
        var fieldWasFocused = false
        scenario.onActivity { activity ->
            previousSessionGeneration =
                AddiyonKeyboardService.currentInstance
                    ?.editorGateway
                    ?.sessionGeneration
                    ?: 0L
            fieldWasFocused = activity.field(kind).hasFocus()
        }
        shell("ime enable $imeId")
        val imeWasSelected =
            shell("settings get secure default_input_method").trim() == imeId
        if (!imeWasSelected) {
            shell("ime set $imeId")
        }
        scenario.onActivity { it.focusField(kind) }
        val requiresFreshSession = !imeWasSelected || !fieldWasFocused
        waitUntil {
            var sessionReady = false
            scenario.onActivity { activity ->
                val gateway = AddiyonKeyboardService.currentInstance?.editorGateway
                sessionReady =
                    activity.field(kind).hasFocus() &&
                    gateway != null &&
                    gateway.currentToken() != null &&
                    (!requiresFreshSession ||
                        gateway.sessionGeneration > previousSessionGeneration)
            }
            sessionReady
        }
        SystemClock.sleep(INPUT_SETTLE_MILLIS)
        instrumentation.waitForIdleSync()
    }

    private fun focusCurrentIme(
        scenario: ActivityScenario<ImeTestHostActivity>,
        kind: ImeTestField
    ) {
        var previousSessionGeneration = 0L
        var fieldWasFocused = false
        scenario.onActivity { activity ->
            previousSessionGeneration =
                AddiyonKeyboardService.currentInstance
                    ?.editorGateway
                    ?.sessionGeneration
                    ?: 0L
            fieldWasFocused = activity.field(kind).hasFocus()
        }
        scenario.onActivity { it.focusField(kind) }
        waitUntil {
            var sessionReady = false
            scenario.onActivity { activity ->
                val gateway = AddiyonKeyboardService.currentInstance?.editorGateway
                sessionReady =
                    activity.field(kind).hasFocus() &&
                    gateway != null &&
                    gateway.currentToken() != null &&
                    (fieldWasFocused ||
                        gateway.sessionGeneration > previousSessionGeneration)
            }
            sessionReady
        }
        SystemClock.sleep(INPUT_SETTLE_MILLIS)
        instrumentation.waitForIdleSync()
    }

    private fun focusFault(
        scenario: ActivityScenario<ImeTestHostActivity>,
        mode: ImeFaultMode
    ) {
        var previousCreations = 0
        var previousSessionGeneration = -1L
        scenario.onActivity { activity ->
            previousCreations = activity.faultField.connectionCreations.get()
            previousSessionGeneration =
                AddiyonKeyboardService.currentInstance
                    ?.editorGateway
                    ?.sessionGeneration
                    ?: -1L
            activity.configureFault(mode)
            activity.focusField(ImeTestField.FAULT)
        }
        waitUntil {
            var sessionReady = false
            scenario.onActivity { activity ->
                val gateway = AddiyonKeyboardService.currentInstance?.editorGateway
                val token = gateway?.currentToken()
                sessionReady =
                    activity.faultField.connectionCreations.get() > previousCreations &&
                    gateway != null &&
                    gateway.sessionGeneration > previousSessionGeneration &&
                    token != null
            }
            sessionReady
        }
        SystemClock.sleep(INPUT_SETTLE_MILLIS)
    }

    private fun setFaultText(
        scenario: ActivityScenario<ImeTestHostActivity>,
        value: String,
        start: Int = value.length,
        end: Int = start
    ) {
        var previousCreations = 0
        var previousSessionGeneration = -1L
        scenario.onActivity { activity ->
            previousCreations = activity.faultField.connectionCreations.get()
            previousSessionGeneration =
                AddiyonKeyboardService.currentInstance
                    ?.editorGateway
                    ?.sessionGeneration
                    ?: -1L
            activity.faultField.setText(value)
            activity.faultField.setSelection(start, end)
            activity.getSystemService(InputMethodManager::class.java)
                ?.restartInput(activity.faultField)
        }
        waitUntil {
            var sessionReady = false
            scenario.onActivity { activity ->
                val gateway = AddiyonKeyboardService.currentInstance?.editorGateway
                val token = gateway?.currentToken()
                sessionReady =
                    activity.faultField.connectionCreations.get() > previousCreations &&
                    gateway != null &&
                    gateway.sessionGeneration > previousSessionGeneration &&
                    token != null &&
                    token.selectionStart == start &&
                    token.selectionEnd == end
            }
            sessionReady
        }
        SystemClock.sleep(INPUT_SETTLE_MILLIS)
    }

    private fun setTextAndSelection(
        scenario: ActivityScenario<ImeTestHostActivity>,
        kind: ImeTestField,
        value: String,
        start: Int,
        end: Int
    ) {
        focus(scenario, kind)
        scenario.onActivity { activity ->
            val field = activity.field(kind)
            field.setText(value)
            field.setSelection(start, end)
            activity.getSystemService(InputMethodManager::class.java)
                ?.restartInput(field)
        }
        SystemClock.sleep(INPUT_SETTLE_MILLIS)
        instrumentation.waitForIdleSync()
    }

    private fun moveSelection(
        scenario: ActivityScenario<ImeTestHostActivity>,
        kind: ImeTestField,
        position: Int
    ) {
        scenario.onActivity { activity ->
            activity.field(kind).setSelection(position)
        }
        SystemClock.sleep(INPUT_SETTLE_MILLIS)
        instrumentation.waitForIdleSync()
    }

    private fun invokeService(action: (AddiyonKeyboardService) -> Unit) {
        instrumentation.runOnMainSync {
            action(requireNotNull(AddiyonKeyboardService.currentInstance))
        }
        instrumentation.waitForIdleSync()
    }

    private fun requireService(): AddiyonKeyboardService =
        requireNotNull(AddiyonKeyboardService.currentInstance)

    private fun fieldText(
        scenario: ActivityScenario<ImeTestHostActivity>,
        kind: ImeTestField
    ): String {
        var value = ""
        scenario.onActivity { value = it.field(kind).text.toString() }
        return value
    }

    private fun actionCount(
        scenario: ActivityScenario<ImeTestHostActivity>,
        kind: ImeTestField
    ): Int {
        var value = 0
        scenario.onActivity { value = it.actionCount(kind) }
        return value
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Timed out waiting for IME state", predicate())
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor =
            instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }

    private companion object {
        const val VISIBILITY_CYCLES = 100
        const val FIELD_SWITCH_CYCLES = 100
        const val LARGE_UNICODE_BLOCKS = 80
        const val INPUT_SETTLE_MILLIS = 75L
        const val SELECTION_SETTLE_MILLIS = 20L
        const val POLL_MILLIS = 50L
        const val WAIT_TIMEOUT_MILLIS = 8_000L
        val composingRegionFaultModes = listOf(
            ImeFaultMode.REJECT_COMPOSING_REGION,
            ImeFaultMode.THROW_COMPOSING_REGION,
            ImeFaultMode.ACCEPT_COMPOSING_REGION_WITHOUT_SPAN
        )
    }

    private class FixedTelemetryStore : TelemetryConsentStore {
        private var value = StoredTelemetryConsent(
            analyticsEnabled = true,
            analyticsFirstEnableLogged = true
        )

        override fun load(): StoredTelemetryConsent = value

        override fun save(consent: StoredTelemetryConsent): Boolean {
            value = consent
            return true
        }

        override fun clear(): Boolean {
            value = StoredTelemetryConsent()
            return true
        }
    }

    private class RecordingTelemetryBackend : TelemetryBackend {
        override val available = true
        val events = Collections.synchronizedList(mutableListOf<TelemetryEvent>())

        override fun setAnalyticsCollectionEnabled(enabled: Boolean) = Unit

        override fun resetAnalyticsData() = Unit

        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) = Unit

        override fun deleteUnsentReports() = Unit

        override fun log(event: TelemetryEvent) {
            events += event
        }

        override fun record(report: SanitizedNonFatal) = Unit
    }
}
