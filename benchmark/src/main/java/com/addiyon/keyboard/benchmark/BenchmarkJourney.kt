package com.addiyon.keyboard.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope

internal const val TARGET_PACKAGE = "com.addiyon.keyboard"
internal const val TARGET_IME =
    "$TARGET_PACKAGE/com.addiyon.keyboard.AddiyonKeyboardService"
internal const val TARGET_HOST =
    "$TARGET_PACKAGE/com.addiyon.keyboard.benchmarkhost.ImeTestHostActivity"
internal const val TARGET_RECEIVER =
    "$TARGET_PACKAGE/com.addiyon.keyboard.debug.ImeTestCommandReceiver"

internal fun MacrobenchmarkScope.selectTargetIme() {
    device.executeShellCommand(
        "settings put secure show_ime_with_hard_keyboard 1"
    )
    device.executeShellCommand("ime enable $TARGET_IME")
    device.executeShellCommand("ime set $TARGET_IME")
}

internal fun MacrobenchmarkScope.startEditor(field: String = "normal") {
    device.executeShellCommand(
        "am start -W -n $TARGET_HOST --es field $field"
    )
    device.waitForIdle()
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

internal fun MacrobenchmarkScope.runImeCriticalJourney() {
    startEditor()
    typeCharacters("selam")
    command("space")
    command("language")
    typeCharacters("hello")
    command("suggestion", "hello")
    command("language")
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
