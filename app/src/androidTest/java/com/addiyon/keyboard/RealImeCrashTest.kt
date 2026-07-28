package com.addiyon.keyboard

import com.addiyon.keyboard.benchmarkhost.ImeFaultMode
import com.addiyon.keyboard.benchmarkhost.ImeTestField
import com.addiyon.keyboard.benchmarkhost.ImeTestHostActivity
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.addiyon.keyboard.voice.isVoiceMode
import java.io.FileInputStream
import org.junit.After
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
        shell("ime enable $imeId")
        shell("ime set $imeId")
        scenario.onActivity { it.focusField(kind) }
        waitUntil {
            var focused = false
            scenario.onActivity { focused = it.field(kind).hasFocus() }
            focused && AddiyonKeyboardService.currentInstance != null
        }
        SystemClock.sleep(INPUT_SETTLE_MILLIS)
        instrumentation.waitForIdleSync()
    }

    private fun focusFault(
        scenario: ActivityScenario<ImeTestHostActivity>,
        mode: ImeFaultMode
    ) {
        var previousCreations = 0
        scenario.onActivity { activity ->
            previousCreations = activity.faultField.connectionCreations.get()
            activity.configureFault(mode)
            activity.focusField(ImeTestField.FAULT)
        }
        waitUntil {
            var creations = 0
            scenario.onActivity {
                creations = it.faultField.connectionCreations.get()
            }
            creations > previousCreations && AddiyonKeyboardService.currentInstance != null
        }
        SystemClock.sleep(INPUT_SETTLE_MILLIS)
    }

    private fun setFaultText(
        scenario: ActivityScenario<ImeTestHostActivity>,
        value: String
    ) {
        scenario.onActivity { activity ->
            activity.faultField.setText(value)
            activity.faultField.setSelection(value.length)
            activity.getSystemService(InputMethodManager::class.java)
                ?.restartInput(activity.faultField)
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
        const val INPUT_SETTLE_MILLIS = 75L
        const val POLL_MILLIS = 50L
        const val WAIT_TIMEOUT_MILLIS = 8_000L
    }
}
