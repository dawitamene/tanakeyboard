package com.addiyon.keyboard

import androidx.compose.runtime.Composable
import com.addiyon.keyboard.features.appshell.KeyboardAppShellActivity
import com.addiyon.keyboard.features.appshell.KeyboardAppShellConfig

class MainActivity : KeyboardAppShellActivity() {
    override val keyboardProduct = TextRevampKeyboardProduct

    @Composable
    override fun ProvideProductComposition(content: @Composable () -> Unit) {
        ProvideTextRevampLocalization(content)
    }

    @Composable
    override fun keyboardAppShellConfig(): KeyboardAppShellConfig =
        textRevampAppShellConfig(TextRevampKeyboardProduct)

}
