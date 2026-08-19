package com.addiyon.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.addiyon.keyboard.features.appshell.KeyboardAppShellCustomization
import com.addiyon.keyboard.features.appshell.KeyboardSetupActivity
import com.addiyon.keyboard.features.appshell.R as AppShellR
import com.addiyon.keyboard.ui.i18n.LanguageToggle
import com.addiyon.keyboard.ui.i18n.ProvideAppLocalization
import com.addiyon.keyboard.ui.manual.ManualScreen

class MainActivity : KeyboardSetupActivity() {
    override val keyboardProduct = AddiyonKeyboardProduct

    @Composable
    override fun ProvideProductComposition(content: @Composable () -> Unit) {
        ProvideAppLocalization(content)
    }

    @Composable
    override fun keyboardAppShellCustomization() = KeyboardAppShellCustomization(
        headerActions = { LanguageToggle(compact = true) },
        guideScreen = { onBack ->
            ManualScreen(
                title = stringResource(AppShellR.string.menu_typing_guide),
                backContentDescription = stringResource(AppShellR.string.back),
                searchPlaceholder = stringResource(R.string.typing_guide_search_placeholder),
                guideHowItWorks = stringResource(R.string.typing_guide_how_it_works),
                onBack = onBack
            )
        }
    )
}
