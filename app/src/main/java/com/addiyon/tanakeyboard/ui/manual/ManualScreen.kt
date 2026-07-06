package com.addiyon.tanakeyboard.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.addiyon.tanakeyboard.transliteration.AmharicTable

private data class ManualEntry(val spelling: String, val glyph: Char)
private data class ManualRow(val latin: String, val bare: Char, val entries: List<ManualEntry>)

/**
 * Digraph families whose keyboard key is a single different letter -- the
 * table is keyed by spelling ("sh"), but the key the user actually taps is
 * "x". Mirrors AmharicTable's (private) keyboardToFamilyKey mapping.
 */
private val keyboardAliases = mapOf("sh" to "x", "ch" to "c")

private val indexToVowelLabel: Map<Int, String> =
    AmharicTable.vowels.filter { it.second != AmharicTable.UA_INDEX }
        .associate { (label, idx) -> idx to label }

private fun buildManualRows(): List<ManualRow> =
    AmharicTable.families.entries
        .sortedBy { it.key.lowercase() }
        .map { (latin, family) ->
            val entries = family.forms.mapIndexed { index, glyph ->
                val label = indexToVowelLabel[index].orEmpty()
                ManualEntry(latin + label, glyph)
            } + listOfNotNull(family.ua?.let { ManualEntry(latin + "ua", it) })
            ManualRow(latin, family.bare, entries)
        }

private fun buildStandaloneVowelEntries(): List<ManualEntry> =
    AmharicTable.bareVowels.map { bare ->
        val family = AmharicTable.families.getValue(bare.familyKey)
        ManualEntry(bare.spelling, family.forms[bare.index])
    }

@Composable
fun ManualScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember { buildManualRows() }
    val standaloneVowels = remember { buildStandaloneVowelEntries() }
    var query by rememberSaveable { mutableStateOf("") }

    val visibleRows = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) rows
        else rows.filter { row ->
            row.latin.startsWith(q, ignoreCase = true) ||
                keyboardAliases[row.latin]?.startsWith(q, ignoreCase = true) == true ||
                row.entries.any { it.spelling.startsWith(q, ignoreCase = true) || it.glyph in q }
        }
    }
    val showVowels = query.isBlank() ||
        standaloneVowels.any { it.spelling.startsWith(query.trim(), ignoreCase = true) || it.glyph in query }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Transliteration Manual",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search: be, sh, ላ ...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showVowels) {
                item {
                    ManualCard(title = "Standalone vowels", bare = null, entries = standaloneVowels)
                }
            }
            items(visibleRows) { row ->
                ManualCard(
                    title = row.latin,
                    bare = row.bare,
                    keyHint = keyboardAliases[row.latin],
                    entries = row.entries
                )
            }
        }
    }
}

@Composable
private fun ManualCard(
    title: String,
    bare: Char?,
    entries: List<ManualEntry>,
    keyHint: String? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (bare != null) "$title  ($bare)" else title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (keyHint != null) {
                Text(
                    text = "On the keyboard: tap \"$keyHint\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            EntryFlow(entries)
        }
    }
}

@Composable
private fun EntryFlow(entries: List<ManualEntry>) {
    // Simple wrapping rows of fixed chunk size -- avoids pulling in a
    // FlowRow dependency for a small, fixed-size (<= 8 items) list.
    val chunkSize = 4
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        entries.chunked(chunkSize).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                chunk.fastForEach { entry ->
                    Column(modifier = Modifier.size(56.dp)) {
                        Text(text = entry.glyph.toString(), style = MaterialTheme.typography.headlineSmall)
                        Text(text = entry.spelling, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
