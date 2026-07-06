package com.addiyon.tanakeyboard.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addiyon.tanakeyboard.transliteration.AmharicTable

private data class TableCell(val fidel: Char, val latin: String)
private data class TableRow(
    val label: String,
    val cells: List<TableCell>,
    val searchText: String
)

private val indexToVowel: Map<Int, String> =
    AmharicTable.vowels
        .filter { it.second != AmharicTable.UA_INDEX }
        .associate { it.second to it.first } + (AmharicTable.BARE_FORM_INDEX to "")

private val ethiopianOrder = listOf(
    'ሀ', 'ለ', 'ሐ', 'መ', 'ሠ', 'ረ', 'ሰ', 'ሸ',
    'ቀ', 'በ', 'ተ', 'ቸ', 'ኀ', 'ነ', 'ኘ', 'አ',
    'ከ', 'ኸ', 'ወ', 'ዐ', 'ዘ', 'ዠ', 'የ', 'ደ',
    'ጀ', 'ገ', 'ጠ', 'ጨ', 'ጰ', 'ጸ', 'ፀ', 'ፈ', 'ፐ'
)

private fun buildTableRows(): List<TableRow> {
    val rows = mutableListOf<TableRow>()

    for ((key, family) in AmharicTable.families) {
        val isGlottalPharyngeal = key == "'" || key == "`"
        val cells = mutableListOf<TableCell>()
        val searchParts = mutableListOf<String>()

        for (i in 0 until 7) {
            val fidel = family.forms[i]
            val vowel = indexToVowel[i].orEmpty()
            val latins = mutableListOf<String>()

            if (isGlottalPharyngeal) {
                for (bv in AmharicTable.bareVowels) {
                    if (bv.index == i && bv.familyKey == key) {
                        latins.add(bv.spelling)
                    }
                }
                if (latins.isEmpty()) {
                    latins.add(key + vowel)
                }
            } else {
                latins.add(key + vowel)
            }

            val latinStr = latins.first()
            cells.add(TableCell(fidel, latinStr))
            searchParts.add(latinStr)
            searchParts.add(fidel.toString())
        }

        if (family.ua != null) {
            cells.add(TableCell(family.ua, key + "ua"))
            searchParts.add(key + "ua")
            searchParts.add(family.ua.toString())
        }

        val label = when (key) {
            "'" -> "a"
            "`" -> "A"
            else -> key
        }
        rows.add(TableRow(label, cells, searchParts.joinToString(" ")))
    }

    val velar = AmharicTable.velarFamily
    val velarCells = mutableListOf<TableCell>()
    val velarSearch = mutableListOf<String>()
    for (i in 0 until 7) {
        val fidel = velar.forms[i]
        val vowel = indexToVowel[i].orEmpty()
        val spelling = "h$vowel"
        velarCells.add(TableCell(fidel, spelling))
        velarSearch.add(spelling)
        velarSearch.add(fidel.toString())
    }
    rows.add(TableRow("h", velarCells, velarSearch.joinToString(" ")))

    val orderMap = ethiopianOrder.withIndex().associate { it.value to it.index }
    return rows.sortedWith(
        compareBy<TableRow> {
            orderMap[it.cells.first().fidel] ?: Int.MAX_VALUE
        }.thenBy { it.label }
    )
}

@Composable
fun ManualScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember { buildTableRows() }
    var query by rememberSaveable { mutableStateOf("") }

    val visibleRows = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) rows
        else rows.filter { it.searchText.contains(q, ignoreCase = true) }
    }

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
                text = "Typing Guide",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            shape = RoundedCornerShape(percent = 50),
            placeholder = { Text("Search: he, sh, ላ ...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visibleRows) { row ->
                FamilyCard(row)
            }
        }
    }
}

@Composable
private fun FamilyCard(row: TableRow) {
    val columnsPerRow = 4

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            row.cells.chunked(columnsPerRow).forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    chunk.forEach { cell ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = cell.fidel.toString(),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = cell.latin,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
