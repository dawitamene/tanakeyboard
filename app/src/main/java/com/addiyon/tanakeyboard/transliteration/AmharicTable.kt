package com.addiyon.tanakeyboard.transliteration

/**
 * The Amharic transliteration table, as pure data.
 *
 * Structure exploited here: every consonant family in the scheme is exactly
 * 7 syllabic forms in a fixed vowel order --
 *
 *     index:  0    1    2    3    4     5      6
 *     vowel:  e    u    i    a    ie   (none)  o
 *
 * -- plus, for some families, an optional 8th labialized "ua" form
 * (e.g. l + ua -> ሏ). The bare consonant with no vowel is the 6th-order
 * form at [BARE_FORM_INDEX].
 *
 * Design notes:
 *
 * - The 7 forms are stored as literal strings copied from the source table,
 *   NOT computed via Unicode codepoint arithmetic. The Ethiopic block looks
 *   arithmetic (base + vowel offset) and mostly is, but the "ua" forms break
 *   the pattern inconsistently across families (ሏ is base+7 in its row,
 *   while ቋ/ኳ/ጓ sit at +11). Explicit literals are trivially auditable
 *   against the source list and immune to off-by-one bugs.
 *
 * - This file is pure Kotlin with zero Android/Compose imports, so the
 *   transliteration engine built on top of it (and, later, the autocomplete
 *   dictionary) can be unit-tested on the JVM without an emulator.
 *
 * - Keys are CASE-SENSITIVE. The scheme distinguishes h/H (ሀ/ሐ), t/T (ተ/ጠ),
 *   c-family ch/C (ቸ/ጨ), etc., so lookups must never be lowercased blindly;
 *   the key press pipeline decides case (via shift state) before it gets here.
 */
object AmharicTable {

    /** Index of the bare (no-vowel, 6th order) form inside [Family.forms]. */
    const val BARE_FORM_INDEX = 5

    /**
     * Sentinel index meaning "the labialized ua form" -- resolved via
     * [Family.ua] rather than [Family.forms].
     */
    const val UA_INDEX = -1

    /**
     * One consonant family: its 7 ordered syllabic forms, plus the optional
     * labialized "ua" form where the scheme defines one.
     */
    data class Family(
        val forms: String,
        val ua: Char? = null
    ) {
        init {
            require(forms.length == 7) {
                "A family must have exactly 7 forms, got ${forms.length} in \"$forms\""
            }
        }

        /** The bare (6th order) form, e.g. ስ for "s". */
        val bare: Char get() = forms[BARE_FORM_INDEX]
    }

    /**
     * Vowel spelling -> index into [Family.forms] ([UA_INDEX] for the
     * labialized form).
     *
     * ORDER MATTERS: this list is matched greedily front-to-back, so
     * multi-letter vowels come first -- "ie" must win over "i", and "ua"
     * over "u". Keep it longest-first if new vowels are ever added.
     */
    val vowels: List<Pair<String, Int>> = listOf(
        "ie" to 4,
        "ua" to UA_INDEX,
        "e" to 0,
        "u" to 1,
        "i" to 2,
        "a" to 3,
        "o" to 6
    )

    /**
     * Bare (unprefixed) vowel spelling -> index into the glottal ("'")
     * [Family.forms], used when a vowel appears with no preceding consonant
     * match (e.g. word-initial "aster" -> አስተር).
     *
     * This is a DIFFERENT index mapping than [vowels]. [vowels] encodes
     * "consonant + vowel" combinations, where order 1 (index 0) spells the
     * "e" vowel by SERA convention (e.g. "l" + "e" -> ለ). But order 1 of the
     * bare glottal family spells "a" (አ) -- the glottal series is the one
     * everyday standalone-vowel spelling, and it doesn't follow the same
     * vowel-per-order labeling as regular consonant families. Hence a
     * second table instead of reusing [vowels] against the "'" family.
     *
     * ORDER MATTERS: longest-first, so "ie" wins over "i".
     */
    val bareVowels: List<Pair<String, Int>> = listOf(
        "ie" to 4,
        "a" to 0,
        "i" to 2,
        "u" to 1,
        "o" to 6,
        "e" to BARE_FORM_INDEX
    )

    /**
     * Every consonant family in the scheme, keyed by its (case-sensitive)
     * Latin spelling. Forms are copied verbatim, row by row, from the
     * source transliteration list.
     */
    val families: Map<String, Family> = mapOf(
        // ----- ሀ series -------------------------------------------------
        "h" to Family("ሀሁሂሃሄህሆ"),
        "H" to Family("ሐሑሒሓሔሕሖ"),

        // ----- ለ / መ / ሠ-block ------------------------------------------
        "l" to Family("ለሉሊላሌልሎ", ua = 'ሏ'),
        "m" to Family("መሙሚማሜምሞ", ua = 'ሟ'),
        "s" to Family("ሰሱሲሳሴስሶ", ua = 'ሷ'),
        "sh" to Family("ሸሹሺሻሼሽሾ", ua = 'ሿ'),
        "r" to Family("ረሩሪራሬርሮ", ua = 'ሯ'),

        // ----- ቀ / በ / ቨ -------------------------------------------------
        "q" to Family("ቀቁቂቃቄቅቆ", ua = 'ቋ'),
        "b" to Family("በቡቢባቤብቦ", ua = 'ቧ'),
        "v" to Family("ቨቩቪቫቬቭቮ"),

        // ----- ተ / ቸ ----------------------------------------------------
        "t" to Family("ተቱቲታቴትቶ", ua = 'ቷ'),
        "ch" to Family("ቸቹቺቻቼችቾ", ua = 'ቿ'),

        // ----- ነ / ኘ ----------------------------------------------------
        "n" to Family("ነኑኒናኔንኖ", ua = 'ኗ'),
        "gn" to Family("ኘኙኚኛኜኝኞ", ua = 'ኟ'),

        // ----- glottal አ ------------------------------------------------
        "'" to Family("አኡኢኣኤእኦ"),

        // ----- ከ / ኸ ----------------------------------------------------
        "k" to Family("ከኩኪካኬክኮ", ua = 'ኳ'),
        "kh" to Family("ኸኹኺኻኼኽኾ"),

        // ----- ወ / pharyngeal ዐ -----------------------------------------
        "w" to Family("ወዉዊዋዌውዎ"),
        "`" to Family("ዐዑዒዓዔዕዖ"),

        // ----- ዘ / ዠ / የ -------------------------------------------------
        "z" to Family("ዘዙዚዛዜዝዞ", ua = 'ዟ'),
        "zh" to Family("ዠዡዢዣዤዥዦ"),
        "y" to Family("የዩዪያዬይዮ"),

        // ----- ደ / ጀ / ገ -------------------------------------------------
        "d" to Family("ደዱዲዳዴድዶ", ua = 'ዷ'),
        "j" to Family("ጀጁጂጃጄጅጆ", ua = 'ጇ'),
        "g" to Family("ገጉጊጋጌግጎ", ua = 'ጓ'),

        // ----- ejectives ጠ / ጸ / ጨ / ጰ -----------------------------------
        "T" to Family("ጠጡጢጣጤጥጦ", ua = 'ጧ'),
        "ts" to Family("ጸጹጺጻጼጽጾ"),
        "C" to Family("ጨጩጪጫጬጭጮ", ua = 'ጯ'),
        "ph" to Family("ጰጱጲጳጴጵጶ"),
        "P" to Family("ጰጱጲጳጴጵጶ"),

        // ----- ፈ / ፐ ----------------------------------------------------
        "f" to Family("ፈፉፊፋፌፍፎ", ua = 'ፏ'),
        "p" to Family("ፐፑፒፓፔፕፖ")
    )

    /**
     * Consonant spellings sorted longest-first, for greedy matching:
     * "sh" must be tried before "s", "gn" before "g", "kh" before "k",
     * "zh" before "z", "ts" before "t", "ch" before "c", "ph" before "p".
     *
     * (Within the same length, order is irrelevant -- distinct spellings of
     * equal length can never shadow each other.)
     */
    val consonantsByLength: List<String> =
        families.keys.sortedByDescending { it.length }

    /**
     * Maps keyboard Latin letters (single characters) to the multi-letter
     * AmharicTable family key they represent: "x" and "c" are the keyboard
     * spellings for "sh" and "ch" respectively. Vowels are handled
     * separately, via [bareVowels], since they don't all resolve to the
     * same glottal glyph -- see [bareFormOf].
     */
    private val keyboardToFamilyKey: Map<String, String> = mapOf(
        "x" to "sh",
        "c" to "ch"
    )

    /**
     * Convenience lookup used by the UI (e.g. a key's corner label showing
     * a preview of what the key produces): the glyph for [latin], or null
     * if the spelling isn't part of the scheme.
     *
     * Resolution order:
     * 1. Exact match (preserves case distinctions like h/H, t/T, ch/C) ->
     *    the family's bare (6th-order) form.
     * 2. Lowercased match (handles uppercase letters without a distinct
     *    family, e.g. "Q" -> "q") -> the family's bare form.
     * 3. Bare-vowel match (e.g. "a" -> አ, "u" -> ኡ) -- these are NOT the
     *    glottal family's bare form (እ); see [bareVowels] for why standalone
     *    vowels use a different index than consonant+vowel combinations.
     * 4. Keyboard-to-family-key mapping (handles letters that represent
     *    multi-letter spellings: "x" -> "sh", "c" -> "ch") -> the family's
     *    bare form.
     */
    fun bareFormOf(latin: String): Char? {
        families[latin]?.let { return it.bare }
        val lower = latin.lowercase()
        if (lower != latin) {
            families[lower]?.let { return it.bare }
        }
        bareVowels.firstOrNull { (spelling, _) -> spelling == lower }?.let { (_, index) ->
            return families.getValue("'").forms[index]
        }
        val mapped = keyboardToFamilyKey[lower]
        if (mapped != null) return families[mapped]?.bare
        return null
    }
}