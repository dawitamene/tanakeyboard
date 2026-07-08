package com.addiyon.tanakeyboard.ui.i18n

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Wraps app UI so every screen beneath it reads its text from the selected
 * [AppLanguage] via [LocalAppStrings], and can flip the language through
 * [LocalAppLanguage]. Each localized entry point (MainActivity and the
 * standalone Activities the keyboard opens) wraps its content in this; the
 * choice is read from and written back to [LanguagePrefs], so it's shared
 * across all of them and survives restarts.
 */
@Composable
fun ProvideAppLocalization(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Held as an explicit MutableState (not a `by` delegate) so the controller
    // object below can capture it and both read and write it.
    val languageState = rememberSaveable { mutableStateOf(LanguagePrefs.language(context)) }
    val language = languageState.value

    val controller = remember {
        object : AppLanguageController {
            override val current: AppLanguage get() = languageState.value
            override fun set(language: AppLanguage) {
                // Mutate the observable state (recomposes the whole tree) and
                // persist, so the choice sticks across launches and Activities.
                LanguagePrefs.setLanguage(context, language)
                languageState.value = language
            }
            override fun toggle() =
                set(if (current == AppLanguage.ENGLISH) AppLanguage.AMHARIC else AppLanguage.ENGLISH)
        }
    }

    val strings = if (language == AppLanguage.AMHARIC) AmharicStrings else EnglishStrings
    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalAppLanguage provides controller
    ) {
        content()
    }
}

/**
 * A compact two-option pill that switches the app UI language in place. Shows
 * both languages in their own script; the active one is highlighted. Placed
 * top-left on the Settings home.
 */
@Composable
fun LanguageToggle(modifier: Modifier = Modifier, compact: Boolean = false) {
    val controller = LocalAppLanguage.current
    val textStyle = if (compact) {
        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal)
    } else {
        MaterialTheme.typography.labelLarge
    }
    val hPadding = if (compact) 10.dp else 14.dp
    val vPadding = if (compact) 4.dp else 6.dp
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp)
    ) {
        AppLanguage.entries.forEach { lang ->
            val selected = controller.current == lang
            Text(
                text = lang.label,
                style = textStyle,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { controller.set(lang) }
                    .padding(horizontal = hPadding, vertical = vPadding)
            )
        }
    }
}
