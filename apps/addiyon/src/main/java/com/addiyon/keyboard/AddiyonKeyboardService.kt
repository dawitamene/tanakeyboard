package com.addiyon.keyboard

import com.addiyon.keyboard.features.appshell.KeyboardAppShellActivity

class AddiyonKeyboardService : PackKeyboardService() {
    override val appShellActivityClass: Class<out KeyboardAppShellActivity> = MainActivity::class.java

    override val configuredKeyboardProduct = AddiyonKeyboardProduct

    override val configuredLanguagePackProviders = AddiyonLanguagePackProviders
}
