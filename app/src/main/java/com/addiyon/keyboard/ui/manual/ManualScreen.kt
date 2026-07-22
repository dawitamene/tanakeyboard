package com.addiyon.keyboard.ui.manual

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addiyon.keyboard.transliteration.Transliterator
import com.addiyon.keyboard.ui.AppPageTopBar
import com.addiyon.keyboard.ui.i18n.AppStrings
import com.addiyon.keyboard.ui.i18n.LocalAppStrings
import com.addiyon.keyboard.ui.settings.KeyboardPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The typing guide: a hero card demonstrating transliteration live, then one
 * card per consonant family -- fidel-first, in traditional chart order -- plus a
 * contacts-style fast-scroll rail. Rows come from [GuideModel] (derived from
 * the real [AmharicTable]); accents use the user's selected keyboard palette
 * so the guide matches their keyboard.
 */
@Composable
fun ManualScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val accent = remember(isDark) { KeyboardPrefs.palette(context).scheme(isDark).primary }
    val rows = remember { GuideModel.build() }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visibleRows = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) rows
        else rows.filter { it.searchText.contains(q, ignoreCase = true) }
    }
    val searching = query.trim().isNotEmpty()
    // The hero card occupies list index 0 when it's shown, so rail jumps and
    // the current-position readback must shift by one exactly then.
    val heroCount = if (searching) 0 else 1

    // New search -> jump back to the top of the (filtered) results.
    LaunchedEffect(query) { listState.scrollToItem(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppPageTopBar(
                title = strings.typingGuide,
                onBack = onBack,
                backContentDescription = strings.back
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(percent = 50),
                placeholder = { Text(strings.searchPlaceholder) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!searching) {
                        item(key = "hero") { HeroCard(strings, accent) }
                    }
                    items(visibleRows, key = { it.cells.first().fidel.code }) { row ->
                        FamilyCard(
                            family = row,
                            accent = accent,
                            highlightQuery = query.trim()
                        )
                    }
                }

                val currentRow by remember(heroCount) {
                    derivedStateOf { (listState.firstVisibleItemIndex - heroCount).coerceAtLeast(0) }
                }
                IndexRail(
                    labels = visibleRows.map { it.cells.first().fidel },
                    currentIndex = currentRow,
                    accent = accent,
                    onJump = { index ->
                        scope.launch {
                            listState.scrollToItem(
                                (index + heroCount).coerceIn(0, visibleRows.size + heroCount - 1)
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * "How it works" card: types "selam" letter by letter on a loop and shows the
 * live fidel output of the REAL [Transliterator] -- the demo runs the literal
 * function keypresses run, so it can't demonstrate something typing wouldn't
 * do. BOTH sides reserve their full-word footprint with a transparent
 * placeholder (the untyped Latin tail, and the complete fidel word behind the
 * live output): every element of the row keeps a fixed position for the whole
 * loop, so nothing shifts or flickers as the texts grow and reset.
 */
@Composable
private fun HeroCard(strings: AppStrings, accent: Color) {
    val demo = "selam"
    // The widest fidel the demo ever shows -- the intermediate readings are
    // prefixes-in-width of it -- held invisibly to pin the output slot's size.
    val demoFidel = remember { Transliterator.transliterate(demo) }
    var typedLength by remember { mutableIntStateOf(demo.length) }
    LaunchedEffect(Unit) {
        while (true) {
            for (i in 0..demo.length) {
                typedLength = i
                delay(450L)
            }
            delay(1800L)
        }
    }
    val typed = demo.take(typedLength)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = strings.guideHowItWorks,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(typed)
                        withStyle(SpanStyle(color = Color.Transparent)) {
                            append(demo.drop(typedLength))
                        }
                    },
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "  →  ",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = demoFidel,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Transparent
                    )
                    Text(
                        text = Transliterator.transliterate(typed),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * One family card: a header row (base fidel chip + Latin spellings), then all 7 orders in one row,
 * fidel-first with the Latin caption beneath, plus a chip for the labialized
 * "ua" form when the family has one. Cells matching [highlightQuery] exactly
 * (by Latin or fidel) are tinted. Purely informational -- each cell already
 * shows the keys that type it, so cells are not tappable.
 */
@Composable
private fun FamilyCard(
    family: GuideFamily,
    accent: Color,
    highlightQuery: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f))
                ) {
                    Text(
                        text = family.cells.first().fidel.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = (listOf(family.label) + family.aliases).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                family.cells.forEach { cell ->
                    val highlighted = highlightQuery.isNotEmpty() && (
                        cell.latin.equals(highlightQuery, ignoreCase = true) ||
                            cell.fidel.toString() == highlightQuery
                        )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (highlighted) accent.copy(alpha = 0.25f) else Color.Transparent
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Text(text = cell.fidel.toString(), fontSize = 22.sp)
                        Text(
                            text = cell.latin,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            family.ua?.let { ua ->
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = ua.fidel.toString(), fontSize = 16.sp)
                    Text(
                        text = ua.latin,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Contacts-style fast-scroll rail showing EVERY family's glyph. Tapping or
 * dragging maps the touch's y-fraction onto the card list; the label nearest
 * the current scroll position is tinted with the accent. The glyphs shrink
 * below their 11sp ceiling when the rail is too short to fit them all at full
 * size, so no family ever drops off the rail on small screens.
 *
 * While a finger is on the rail, the glyphs near it swell into a dock-style
 * magnification bubble ([RailMagnification]) and bulge LEFT out of the rail
 * (via graphicsLayer, so nothing reflows) -- big enough to read under the
 * finger. Each glyph animates toward its target scale, so the bubble follows
 * a scrub smoothly and relaxes back on release.
 */
@Composable
private fun IndexRail(
    labels: List<Char>,
    currentIndex: Int,
    accent: Color,
    onJump: (Int) -> Unit
) {
    if (labels.isEmpty()) return

    // The finger's y while it's on the rail (pressed or dragging); null when
    // idle. Drives every glyph's magnification target.
    var touchY by remember { mutableStateOf<Float?>(null) }
    // Each glyph slot's measured center y, filled by onGloballyPositioned --
    // measured rather than derived from SpaceEvenly arithmetic so the bubble
    // stays correct whatever height the arrangement resolves to.
    val slotCenters = remember { mutableStateMapOf<Int, Float>() }
    val density = LocalDensity.current
    val bubbleRadius = with(density) { 72.dp.toPx() }
    val bulgeShift = with(density) { 18.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
    ) {
        val fontSize = with(density) {
            val perGlyph = (maxHeight / labels.size) * 0.72f
            val fit = perGlyph.toSp()
            if (fit.value < 11f) fit else 11.sp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(labels.size) {
                detectTapGestures(
                    onPress = { offset ->
                        touchY = offset.y
                        onJump(railIndexFor(offset.y, size.height, labels.size))
                        tryAwaitRelease()
                        touchY = null
                    }
                )
            }
            .pointerInput(labels.size) {
                detectVerticalDragGestures(
                    onDragStart = { touchY = it.y },
                    onDragEnd = { touchY = null },
                    onDragCancel = { touchY = null }
                ) { change, _ ->
                    change.consume()
                    touchY = change.position.y
                    onJump(railIndexFor(change.position.y, size.height, labels.size))
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        labels.forEachIndexed { slot, label ->
            val active = slot == currentIndex
            val scale by animateFloatAsState(
                targetValue = RailMagnification.scaleFor(touchY, slotCenters[slot], bubbleRadius),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "railGlyphScale"
            )
            Text(
                text = label.toString(),
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) accent
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        slotCenters[slot] = coords.positionInParent().y + coords.size.height / 2f
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        // Bulge left, out of the narrow rail and away from
                        // both the finger and the screen edge.
                        translationX = -(scale - 1f) * bulgeShift
                    }
            )
        }
    }
    }
}

private fun railIndexFor(y: Float, height: Int, count: Int): Int =
    ((y / height.coerceAtLeast(1)) * count).toInt().coerceIn(0, count - 1)
