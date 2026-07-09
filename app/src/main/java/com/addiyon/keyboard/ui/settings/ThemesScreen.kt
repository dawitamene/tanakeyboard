package com.addiyon.keyboard.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    val isDark = isSystemInDarkTheme()
    val paletteSections = remember {
        PaletteCategory.entries.mapNotNull { category ->
            KeyboardPalette.entries
                .filter { it.category == category }
                .takeIf { it.isNotEmpty() }
                ?.let { category to it.chunked(3) }
        }
    }

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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            paletteSections.forEach { (category, rows) ->
                item(key = "section-${category.name}") {
                    SectionLabel(category.displayName)
                }
                items(
                    items = rows,
                    key = { row -> row.joinToString(separator = "-") { it.id } }
                ) { rowPalettes ->
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
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.background)
    ) {
        val padding = 6.dp.toPx()
        val gap = 3.dp.toPx()
        val rowGap = 4.dp.toPx()
        val keyHeight = 9.dp.toPx()
        val corner = 2.dp.toPx()

        fun drawRow(y: Float, segments: List<Pair<Float, Color?>>) {
            val contentWidth = size.width - padding * 2
            val unit = (contentWidth - gap * (segments.size - 1)) / segments.sumOf { it.first.toDouble() }.toFloat()
            var x = padding

            segments.forEach { (weight, color) ->
                val width = unit * weight
                if (color != null) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(width, keyHeight),
                        cornerRadius = CornerRadius(corner, corner)
                    )
                }
                x += width + gap
            }
        }

        drawRow(
            padding,
            List(10) { 1f to scheme.surface }
        )
        drawRow(
            padding + keyHeight + rowGap,
            listOf(0.5f to null) + List(9) { 1f to scheme.surface } + listOf(0.5f to null)
        )
        drawRow(
            padding + (keyHeight + rowGap) * 2,
            listOf(1.4f to scheme.surfaceVariant) +
                List(7) { 1f to scheme.surface } +
                listOf(1.4f to scheme.surfaceVariant)
        )
        drawRow(
            padding + (keyHeight + rowGap) * 3,
            listOf(
                1.4f to scheme.surfaceVariant,
                5f to scheme.surface,
                1.4f to scheme.surfaceVariant
            )
        )
    }
}
