package com.addiyon.keyboard.features.appshell

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.ui.design.AddiyonContentSection
import com.addiyon.keyboard.ui.design.AddiyonInputField
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.AddiyonSpacing
import com.addiyon.keyboard.ui.theme.CustomKeyboardTheme
import com.addiyon.keyboard.ui.theme.KeyboardPalette
import com.addiyon.keyboard.ui.theme.PaletteCategory

data class KeyboardPreferenceToggle(
    val label: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

data class KeyboardPreferenceLink(
    val label: String,
    val onClick: () -> Unit
)

data class KeyboardGuideSection(
    val title: String? = null,
    val body: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardPageTopBar(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backContentDescription)
            }
        },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun KeyboardThemePickerScreen(
    title: String,
    backContentDescription: String,
    selectedPalette: KeyboardPalette,
    onPaletteSelected: (KeyboardPalette) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember(selectedPalette) { mutableStateOf(selectedPalette) }
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
        modifier = modifier.fillMaxSize(),
        topBar = { KeyboardPageTopBar(title, onBack, backContentDescription) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).padding(AddiyonSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.sm)
        ) {
            paletteSections.forEach { (category, rows) ->
                item(key = "section-${category.name}") {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = AddiyonSpacing.xs, top = AddiyonSpacing.xs)
                    )
                }
                items(rows, key = { row -> row.joinToString("-") { it.id } }) { rowPalettes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.md)) {
                        rowPalettes.forEach { palette ->
                            KeyboardThemeCard(
                                palette = palette,
                                isDark = isDark,
                                selected = palette == selected,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selected = palette
                                    onPaletteSelected(palette)
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
private fun KeyboardThemeCard(
    palette: KeyboardPalette,
    isDark: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    AddiyonContentSection(
        modifier = modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(AddiyonRadii.group)
            )
            .semantics(mergeDescendants = true) { contentDescription = palette.displayName }
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentPadding = PaddingValues(AddiyonSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.xs)
    ) {
        CustomKeyboardTheme(isDarkTheme = isDark, palette = palette) { KeyboardPalettePreview() }
        Text(
            text = palette.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun KeyboardPalettePreview() {
    val scheme = MaterialTheme.colorScheme
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(AddiyonRadii.small))
            .background(scheme.background)
    ) {
        val padding = 6.dp.toPx()
        val gap = 3.dp.toPx()
        val rowGap = 4.dp.toPx()
        val keyHeight = 9.dp.toPx()
        val corner = 2.dp.toPx()
        fun drawRow(y: Float, segments: List<Pair<Float, Color?>>) {
            val contentWidth = size.width - padding * 2
            val unit = (contentWidth - gap * (segments.size - 1)) /
                segments.sumOf { it.first.toDouble() }.toFloat()
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
        drawRow(padding, List(10) { 1f to scheme.surface })
        drawRow(padding + keyHeight + rowGap, listOf(0.5f to null) + List(9) { 1f to scheme.surface } + listOf(0.5f to null))
        drawRow(padding + (keyHeight + rowGap) * 2, listOf(1.4f to scheme.surfaceVariant) + List(7) { 1f to scheme.surface } + listOf(1.4f to scheme.surfaceVariant))
        drawRow(padding + (keyHeight + rowGap) * 3, listOf(1.4f to scheme.surfaceVariant, 5f to scheme.surface, 1.4f to scheme.surfaceVariant))
    }
}

@Composable
fun KeyboardPreferencesScreen(
    title: String,
    backContentDescription: String,
    links: List<KeyboardPreferenceLink>,
    toggles: List<KeyboardPreferenceToggle>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { KeyboardPageTopBar(title, onBack, backContentDescription) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(AddiyonSpacing.md)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.md)
        ) {
            AddiyonContentSection(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            ) {
                links.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = AddiyonSpacing.lg, vertical = AddiyonSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.label, style = MaterialTheme.typography.bodyLarge)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                toggles.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.onCheckedChange(!item.checked) }
                            .padding(horizontal = AddiyonSpacing.lg, vertical = AddiyonSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.label, style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = item.checked, onCheckedChange = item.onCheckedChange)
                    }
                }
            }
            footer()
        }
    }
}

@Composable
fun KeyboardTestScreen(
    title: String,
    backContentDescription: String,
    placeholder: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { KeyboardPageTopBar(title, onBack, backContentDescription) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(AddiyonSpacing.md)
        ) {
            AddiyonInputField(
                value = text,
                onValueChange = { text = it },
                placeholder = placeholder,
                singleLine = false,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                minLines = if (isLandscape) 2 else 4,
                maxLines = 10
            )
        }
    }
}

@Composable
fun KeyboardAboutScreen(
    title: String,
    backContentDescription: String,
    productName: String,
    versionText: String,
    description: String,
    privacyPolicyLabel: String,
    madeBy: String,
    onPrivacyPolicy: () -> Unit,
    onBack: () -> Unit,
    logo: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    privacyPolicyTestTag: String? = null
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { KeyboardPageTopBar(title, onBack, backContentDescription) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AddiyonSpacing.xl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(AddiyonSpacing.xl))
            logo()
            Spacer(Modifier.height(AddiyonSpacing.md))
            Text(productName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(versionText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(AddiyonSpacing.xl))
            AddiyonContentSection(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(AddiyonSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.sm)
                ) {
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = privacyPolicyLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .then(if (privacyPolicyTestTag == null) Modifier else Modifier.testTag(privacyPolicyTestTag))
                            .fillMaxWidth()
                            .clickable(onClick = onPrivacyPolicy)
                            .padding(vertical = AddiyonSpacing.xs)
                    )
                }
            }
            Spacer(Modifier.height(AddiyonSpacing.xl))
            Text(madeBy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun KeyboardTextGuideScreen(
    title: String,
    backContentDescription: String,
    sections: List<KeyboardGuideSection>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { KeyboardPageTopBar(title, onBack, backContentDescription) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(AddiyonSpacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AddiyonSpacing.md)
        ) {
            sections.forEach { section ->
                AddiyonContentSection(modifier = Modifier.fillMaxWidth()) {
                    section.title?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
