package com.addiyon.keyboard.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.addiyon.keyboard"
internal const val TARGET_IME =
    "$TARGET_PACKAGE/.AddiyonKeyboardService"
internal const val TARGET_HOST =
    "$TARGET_PACKAGE/com.addiyon.keyboard.benchmarkhost.ImeTestHostActivity"
internal const val TARGET_RECEIVER =
    "$TARGET_PACKAGE/com.addiyon.keyboard.debug.ImeTestCommandReceiver"

internal fun MacrobenchmarkScope.selectTargetIme() {
    device.executeShellCommand(
        "am start -W -n $TARGET_HOST --es field normal"
    )
    device.waitForIdle()
    device.executeShellCommand(
        "settings put secure show_ime_with_hard_keyboard 1"
    )
    val enableResult = device.executeShellCommand("ime enable $TARGET_IME")
    val setResult = device.executeShellCommand("ime set $TARGET_IME")
    val selectedIme =
        device.executeShellCommand("settings get secure default_input_method").trim()
    check(selectedIme == TARGET_IME) {
        val available = device.executeShellCommand("ime list -s").trim()
        "Could not select Addiyon IME; selected=$selectedIme; " +
            "enable=${enableResult.trim()}; set=${setResult.trim()}; " +
            "available=$available"
    }
}

internal fun MacrobenchmarkScope.startEditor(field: String = "normal") {
    device.executeShellCommand(
        "am start -W -n $TARGET_HOST --es field $field --ez clear true"
    )
    device.waitForIdle()
    val editor = device.wait(
        Until.findObject(By.desc("IME ${field.lowercase()} field")),
        UI_RESULT_TIMEOUT_MS
    )
    checkNotNull(editor) { "Timed out waiting for the $field benchmark editor" }
    editor.click()
    waitForImeReady()
}

internal fun MacrobenchmarkScope.command(action: String, value: String? = null) {
    val valueArgument = value?.let { " --es value $it" }.orEmpty()
    device.executeShellCommand(
        "am broadcast -n $TARGET_RECEIVER " +
            "-a com.addiyon.keyboard.TEST_COMMAND --es command $action$valueArgument"
    )
}

internal fun MacrobenchmarkScope.typeCharacters(value: String) {
    value.forEach { command("character", it.toString()) }
}

internal fun MacrobenchmarkScope.waitForUiText(value: String) {
    check(device.wait(Until.hasObject(By.text(value)), UI_RESULT_TIMEOUT_MS)) {
        "Timed out waiting for IME text: $value"
    }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.waitForEditorText(value: String) {
    check(
        device.wait(
            Until.hasObject(
                By.desc("IME normal field").textContains(value)
            ),
            UI_RESULT_TIMEOUT_MS
        )
    ) {
        "Timed out waiting for editor text containing: $value"
    }
}

private fun MacrobenchmarkScope.waitForImeReady() {
    val deadline = SystemClock.uptimeMillis() + UI_RESULT_TIMEOUT_MS
    var result = ""
    while (SystemClock.uptimeMillis() < deadline) {
        result = device.executeShellCommand(
            "am broadcast -n $TARGET_RECEIVER " +
                "-a com.addiyon.keyboard.TEST_COMMAND --es command ready"
        )
        if ("result=1" in result) return
        SystemClock.sleep(100)
    }
    val selectedIme =
        device.executeShellCommand("settings get secure default_input_method").trim()
    error(
        "Timed out waiting for the Addiyon IME; " +
            "selected=$selectedIme; ready=${result.trim()}"
    )
}

internal fun MacrobenchmarkScope.runImeCriticalJourney() {
    startEditor()
    command("language_amharic")
    command("shift_reset")
    typeCharacters("selam")
    waitForUiText("ሰላም")
    command("space")
    command("language_english")
    command("shift_reset")
    typeCharacters("info")
    waitForUiText("information")
    command("suggestion", "information")
    waitForEditorText("information")
    waitForUiText("about")
    command("language_amharic")
    typeCharacters("bet")
    command("delete_start")
    repeat(3) { command("delete") }
    command("delete_end")
    command("emoji_open")
    command("emoji", "🙂")
    command("emoji_close")
    command("number")
    typeCharacters("123")
    command("number")
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.runEnglishPredictionJourney() {
    command("shift_reset")
    typeCharacters("info")
    waitForUiText("information")
    command("suggestion", "information")
    waitForEditorText("information")
    waitForUiText("about")
}

internal fun MacrobenchmarkScope.runAmharicPredictionJourney() {
    command("shift_reset")
    typeCharacters("endiet")
    command("space")
    waitForEditorText("እንዴት ")
    waitForUiText("ነው")
}

private const val UI_RESULT_TIMEOUT_MS = 10_000L
