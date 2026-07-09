package com.addiyon.keyboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.ui.i18n.LocalAppStrings
import com.addiyon.keyboard.ui.theme.CustomKeyboardTheme
import com.addiyon.keyboard.ui.theme.KeyboardPalette
import com.addiyon.keyboard.ui.theme.PaletteCategory

/**
 * Themes screen: pick the keyboard color palette. Each theme is shown as a
 * skeleton-keyboard card (a rough miniature of the real keyboard rendered in
 * that palette), laid out three per row. The choice only affects the
 * keyboard -- it's persisted to prefs and the IME service picks it up; the
 * app's own UI is not recolored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Invoked right after a palette is chosen. When the screen was opened from
    // the keyboard toolbar, the host uses this to return to the keyboard.
    onPaletteChosen: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var selected by remember { mutableStateOf(KeyboardPrefs.palette(context)) }
    // The skeleton reflects what the keyboard will actually look like, which
    // follows the system light/dark setting.
    val isDark = isSystemInDarkTheme()

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        topBar = {
            TopAppBar(
                title = { Text(strings.themes) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                // innerPadding OUTSIDE the scroll so the top-bar area is
                // reserved and content scrolls beneath it, not over the title.
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaletteCategory.entries.forEach { category ->
                val palettes = KeyboardPalette.entries.filter { it.category == category }
                if (palettes.isEmpty()) return@forEach

                SectionLabel(category.displayName)
                palettes.chunked(3).forEach { rowPalettes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowPalettes.forEach { p ->
                            ThemeCard(
                                palette = p,
                                isDark = isDark,
                                selected = p == selected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selected = p
                                    KeyboardPrefs.setPalette(context, p)
                                    onPaletteChosen()
                                }
                            )
                        }
                        // Keep the last row's cells the same width as full rows.
                        repeat(3 - rowPalettes.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun ThemeCard(
    palette: KeyboardPalette,
    isDark: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Render the skeleton in the palette's own colors, reusing the same
        // role contract the real keyboard uses.
        CustomKeyboardTheme(isDarkTheme = isDark, palette = palette) {
            SkeletonKeyboard()
        }
        Text(
            text = palette.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A rough, letter-less miniature of the keyboard: a tray with three rows of
 * key bars, a couple of special keys, and one accent key. Colors come from
 * the surrounding [CustomKeyboardTheme] so it matches the palette exactly.
 */
@Composable
private fun SkeletonKeyboard() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.background)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: ten letter keys.
        SkeletonRow {
            repeat(10) { KeyBar(Modifier.weight(1f), scheme.surface) }
        }
        // Row 2: nine letter keys, slightly inset.
        SkeletonRow {
            Spacer(Modifier.weight(0.5f))
            repeat(9) { KeyBar(Modifier.weight(1f), scheme.surface) }
            Spacer(Modifier.weight(0.5f))
        }
        // Row 3: shift (special), letters, delete (special).
        SkeletonRow {
            KeyBar(Modifier.weight(1.4f), scheme.surfaceVariant)
            repeat(7) { KeyBar(Modifier.weight(1f), scheme.surface) }
            KeyBar(Modifier.weight(1.4f), scheme.surfaceVariant)
        }
        // Row 4: special, wide space, special. The space bar is a normal key
        // surface in the real keyboard (not the accent), so match that.
        SkeletonRow {
            KeyBar(Modifier.weight(1.4f), scheme.surfaceVariant)
            KeyBar(Modifier.weight(5f), scheme.surface)
            KeyBar(Modifier.weight(1.4f), scheme.surfaceVariant)
        }
    }
}

@Composable
private fun SkeletonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content
    )
}

@Composable
private fun KeyBar(modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = modifier
            .height(9.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}
