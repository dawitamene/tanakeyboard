package com.addiyon.keyboard.ui.i18n

import android.content.res.Configuration
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.settings.LanguagePrefs
import java.util.Locale

interface AppLanguageController {
    val current: AppLanguage
    fun set(language: AppLanguage)
    fun toggle()
}

val LocalAppLanguage = staticCompositionLocalOf<AppLanguageController> {
    error("No AppLanguageController provided; wrap content in ProvideAppLocalization")
}

@Composable
fun ProvideAppLocalization(content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val languageState = rememberSaveable { mutableStateOf(LanguagePrefs.language(baseContext)) }
    val language = languageState.value

    val controller = remember {
        object : AppLanguageController {
            override val current: AppLanguage get() = languageState.value
            override fun set(language: AppLanguage) {
                LanguagePrefs.setLanguage(baseContext, language)
                languageState.value = language
            }
            override fun toggle() =
                set(if (current == AppLanguage.ENGLISH) AppLanguage.AMHARIC else AppLanguage.ENGLISH)
        }
    }

    val baseConfiguration = LocalConfiguration.current
    val locale = remember(language) { Locale.forLanguageTag(language.code) }
    val localizedConfiguration = remember(language, baseConfiguration) {
        Configuration(baseConfiguration).apply {
            setLocale(locale)
        }
    }
    val localizedContext = remember(language, localizedConfiguration) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        LocalAppLanguage provides controller
    ) {
        content()
    }
}

@Composable
fun LanguageToggle(modifier: Modifier = Modifier, compact: Boolean = false) {
    val controller = LocalAppLanguage.current
    val textStyle = if (compact) {
        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
    } else {
        MaterialTheme.typography.labelLarge
    }
    val hPadding = if (compact) 10.dp else 14.dp
    val vPadding = if (compact) 4.dp else 6.dp
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AddiyonRadii.pill))
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
                    .clip(RoundedCornerShape(AddiyonRadii.pill))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { controller.set(lang) }
                    .padding(horizontal = hPadding, vertical = vPadding)
            )
        }
    }
}
