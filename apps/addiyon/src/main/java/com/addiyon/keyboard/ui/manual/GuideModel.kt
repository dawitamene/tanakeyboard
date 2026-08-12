package com.addiyon.keyboard.ui.manual

import com.addiyon.keyboard.transliteration.AmharicTable

data class GuideCell(val fidel: Char, val latin: String)

data class GuideFamily(
    val label: String,
    val aliases: List<String>,
    val cells: List<GuideCell>,
    val ua: GuideCell?,
    val searchText: String
)

object GuideModel {

    private val indexToVowel: Map<Int, String> =
        AmharicTable.vowels
            .filter { it.second != AmharicTable.UA_INDEX }
            .associate { it.second to it.first } + (AmharicTable.BARE_FORM_INDEX to "")

    private val ethiopianOrder = listOf(
        'ሀ', 'ለ', 'ሐ', 'መ', 'ሠ', 'ረ', 'ሰ', 'ሸ',
        'ቀ', 'ቐ', 'በ', 'ቨ', 'ተ', 'ቸ', 'ኀ', 'ነ', 'ኘ', 'አ',
        'ከ', 'ኸ', 'ወ', 'ዐ', 'ዘ', 'ዠ', 'የ', 'ደ',
        'ጀ', 'ገ', 'ጠ', 'ጨ', 'ጰ', 'ጸ', 'ፀ', 'ፈ', 'ፐ'
    )

    fun build(): List<GuideFamily> {
        val grouped = LinkedHashMap<AmharicTable.Family, MutableList<String>>()
        for ((key, family) in AmharicTable.families) {
            grouped.getOrPut(family) { mutableListOf() }.add(key)
        }
        val rows = grouped.map { (family, keys) -> guideFamily(family, keys) } + velarRow()
        val orderMap = ethiopianOrder.withIndex().associate { it.value to it.index }
        return rows.sortedWith(
            compareBy<GuideFamily> { orderMap[it.cells.first().fidel] ?: Int.MAX_VALUE }
                .thenBy { it.label }
        )
    }

    private fun displayLabel(key: String) = when (key) {
        "'" -> "a"
        "`" -> "A"
        else -> key
    }

    private fun guideFamily(family: AmharicTable.Family, keys: List<String>): GuideFamily {
        val primary = keys.first()
        val isGlottalPharyngeal = primary == "'" || primary == "`"
        val cells = (0 until 7).map { i ->
            val vowel = indexToVowel[i].orEmpty()
            val latin = if (isGlottalPharyngeal) {
                AmharicTable.bareVowels
                    .firstOrNull { it.index == i && it.familyKey == primary }
                    ?.spelling
                    ?: vowel
            } else {
                keys.joinToString("/") { it + vowel }
            }
            GuideCell(family.forms[i], latin)
        }
        val ua = family.ua?.let { form -> GuideCell(form, keys.joinToString("/") { it + "ua" }) }
        return GuideFamily(
            label = displayLabel(primary),
            aliases = keys.drop(1).map(::displayLabel),
            cells = cells,
            ua = ua,
            searchText = buildSearchText(keys, cells, ua)
        )
    }

    private fun buildSearchText(
        keys: List<String>,
        cells: List<GuideCell>,
        ua: GuideCell?
    ): String {
        val parts = mutableListOf<String>()
        keys.mapTo(parts, ::displayLabel)
        for (cell in cells) {
            parts += cell.latin
            parts += cell.fidel.toString()
        }
        if (ua != null) {
            parts += ua.latin
            parts += ua.fidel.toString()
        }
        return parts.joinToString(" ")
    }

    private fun velarRow(): GuideFamily {
        val family = AmharicTable.velarFamily
        val cells = (0 until 7).map { i ->
            GuideCell(family.forms[i], "h" + indexToVowel[i].orEmpty())
        }
        return GuideFamily(
            label = "h",
            aliases = emptyList(),
            cells = cells,
            ua = null,
            searchText = buildSearchText(listOf("h"), cells, null)
        )
    }
}
