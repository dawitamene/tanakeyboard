package com.addiyon.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.addiyon.keyboard.ui.i18n.AppLanguage
import com.addiyon.keyboard.ui.i18n.AppLanguageController
import com.addiyon.keyboard.ui.i18n.EnglishStrings
import com.addiyon.keyboard.ui.i18n.LocalAppLanguage
import com.addiyon.keyboard.ui.i18n.LocalAppStrings
import com.addiyon.keyboard.ui.theme.AddiyonBrandTheme
import com.addiyon.keyboard.ui.theme.CustomKeyboardTheme
import com.addiyon.keyboard.ui.theme.KeyboardPalette

object StaticEnglishLanguageController : AppLanguageController {
    override val current: AppLanguage = AppLanguage.ENGLISH
    override fun set(language: AppLanguage) = Unit
    override fun toggle() = Unit
}

@Composable
fun TestAppHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppStrings provides EnglishStrings,
        LocalAppLanguage provides StaticEnglishLanguageController
    ) {
        AddiyonBrandTheme(isDarkTheme = false) {
            content()
        }
    }
}

@Composable
fun TestKeyboardHost(content: @Composable () -> Unit) {
    CustomKeyboardTheme(isDarkTheme = false, palette = KeyboardPalette.CLASSIC) {
        content()
    }
}
