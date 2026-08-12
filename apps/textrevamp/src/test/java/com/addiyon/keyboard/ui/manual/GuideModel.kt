package com.addiyon.keyboard.ui.manual

import com.addiyon.keyboard.transliteration.AmharicTable

/** One syllabic form in the guide: the fidel glyph and the Latin that types it. */
data class GuideCell(val fidel: Char, val latin: String)

/**
 * One card in the typing guide: a consonant family with its 7 ordered forms
 * plus the labialized "ua" form where the scheme defines one.
 *
 * [label] is the primary Latin spelling; [aliases] are other spellings that
 * reach the SAME family (SERA's single-letter aliases: "c" for "ch", "x" for
 * "sh", "N" for "gn", "P" for "ph") -- the guide used to render each alias as
 * its own duplicate card.
 */
data class GuideFamily(
    val label: String,
    val aliases: List<String>,
    val cells: List<GuideCell>,
    val ua: GuideCell?,
    val searchText: String
)

/**
 * Builds the typing guide's rows from [AmharicTable] -- pure Kotlin (no
 * Android/Compose) so the derivation is JVM-testable. Deriving from the real
 * table means the guide can never drift from what typing actually produces.
 */
object GuideModel {

    private val indexToVowel: Map<Int, String> =
        AmharicTable.vowels
            .filter { it.second != AmharicTable.UA_INDEX }
            .associate { it.second to it.first } + (AmharicTable.BARE_FORM_INDEX to "")

    /**
     * Traditional ፊደል chart order, extended with the ቐ and ቨ series (the old
     * hardcoded list omitted them, which silently dumped those cards at the
     * end of the guide).
     */
    private val ethiopianOrder = listOf(
        'ሀ', 'ለ', 'ሐ', 'መ', 'ሠ', 'ረ', 'ሰ', 'ሸ',
        'ቀ', 'ቐ', 'በ', 'ቨ', 'ተ', 'ቸ', 'ኀ', 'ነ', 'ኘ', 'አ',
        'ከ', 'ኸ', 'ወ', 'ዐ', 'ዘ', 'ዠ', 'የ', 'ደ',
        'ጀ', 'ገ', 'ጠ', 'ጨ', 'ጰ', 'ጸ', 'ፀ', 'ፈ', 'ፐ'
    )

    fun build(): List<GuideFamily> {
        // Alias spellings share equal Family values; group them onto one card
        // instead of rendering a duplicate card per spelling. Insertion order
        // puts the digraph before its alias, so the digraph stays the label.
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
                // Bare-vowel spellings where they exist; the forms without one
                // (ኣ/ዓ/ዔ) are labeled with just the vowel -- the SERA '/`
                // prefix isn't on the keyboard, and homoglyph folding makes
                // the plain vowel reach these forms in practice anyway.
                AmharicTable.bareVowels
                    .firstOrNull { it.index == i && it.familyKey == primary }
                    ?.spelling
                    ?: vowel
            } else {
                // Every spelling that reaches this family, slash-joined, so a
                // duplicate family shows both ways to type each form ("she/xe",
                // "chu/cu"). Single-key families are unchanged (no slash).
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

    /**
     * The velar ኀ series: not directly typeable (it's offered as an alternate
     * reading of h in the suggestion strip), but part of the chart. No shift
     * badge -- the real "h" card carries that.
     */
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
