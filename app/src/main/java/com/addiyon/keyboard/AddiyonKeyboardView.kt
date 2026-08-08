package com.addiyon.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.AbstractComposeView
import com.addiyon.keyboard.ui.KeyboardScreen
import com.addiyon.keyboard.ui.i18n.AmharicStrings
import com.addiyon.keyboard.ui.i18n.AppLanguage
import com.addiyon.keyboard.ui.i18n.EnglishStrings
import com.addiyon.keyboard.ui.i18n.LanguagePrefs
import com.addiyon.keyboard.ui.i18n.LocalAppStrings
import com.addiyon.keyboard.ui.theme.CustomKeyboardTheme

class AddiyonKeyboardView(
    private val service: AddiyonKeyboardService
) : AbstractComposeView(service) {

    @Composable
    override fun Content() {
        val strings = if (LanguagePrefs.language(service) == AppLanguage.AMHARIC) {
            AmharicStrings
        } else {
            EnglishStrings
        }
        CompositionLocalProvider(LocalAppStrings provides strings) {
            CustomKeyboardTheme(
                isDarkTheme = service.isDarkTheme,
                palette = service.palette,
                isLowRam = service.isLowRam,
            ) {
                KeyboardScreen(service)
            }
        }
    }
}
