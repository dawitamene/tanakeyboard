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
     * Convenience lookup used by the UI (e.g. a key's corner label showing
     * the bare form for its letter): the 6th-order glyph for [latin], or
     * null if the spelling isn't a consonant in the scheme.
     */
    fun bareFormOf(latin: String): Char? = families[latin]?.bare
}