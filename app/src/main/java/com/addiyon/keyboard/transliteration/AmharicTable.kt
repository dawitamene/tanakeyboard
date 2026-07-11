package com.addiyon.keyboard.transliteration

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
     * A bare (unprefixed) vowel: its Latin spelling, the consonant family it
     * resolves against, and the index into that [Family.forms].
     */
    data class BareVowel(val spelling: String, val familyKey: String, val index: Int)

    /**
     * Bare (unprefixed) vowel spellings, used when a vowel appears with no
     * preceding consonant match (e.g. word-initial "aster" -> አስተር).
     *
     * Two parallel sets, distinguished by CASE:
     *
     * - LOWERCASE (a/u/i/o/e/ie) resolve against the glottal ("'") family --
     *   አ/ኡ/ኢ/ኦ/እ/ኤ -- the everyday standalone-vowel spelling.
     * - UPPERCASE (A/U/I/O/E) resolve against the pharyngeal ("`") ዐ family --
     *   ዐ/ዑ/ዒ/ዖ/ዕ -- so a shifted vowel reaches the ayn series that otherwise
     *   only had the awkward backtick spelling. Same per-letter index mapping
     *   as the lowercase set, just a different family.
     *
     * The index mapping is a DIFFERENT one than [vowels]. [vowels] encodes
     * "consonant + vowel" combinations, where order 1 (index 0) spells the
     * "e" vowel by SERA convention (e.g. "l" + "e" -> ለ). But order 1 of a
     * bare vowel family spells "a" (አ / ዐ) -- these standalone series don't
     * follow the same vowel-per-order labeling as regular consonant families.
     * Hence a separate table instead of reusing [vowels].
     *
     * ORDER MATTERS: longest-first, so "ie" wins over "i". Case is resolved by
     * the Transliterator (exact-case first, then case-insensitive), so the
     * lowercase and uppercase groups can't shadow each other.
     */
    val bareVowels: List<BareVowel> = listOf(
        // Glottal አ family (lowercase).
        BareVowel("ie", "'", 4),
        BareVowel("a", "'", 0),
        BareVowel("i", "'", 2),
        BareVowel("u", "'", 1),
        BareVowel("o", "'", 6),
        BareVowel("e", "'", BARE_FORM_INDEX),
        // Pharyngeal ዐ family (uppercase).
        BareVowel("A", "`", 0),
        BareVowel("I", "`", 2),
        BareVowel("U", "`", 1),
        BareVowel("O", "`", 6),
        BareVowel("E", "`", BARE_FORM_INDEX)
    )

    /**
     * Every consonant family in the scheme, keyed by its (case-sensitive)
     * Latin spelling. Forms are copied verbatim, row by row, from the
     * source transliteration list.
     *
     * The map is [baseFamilies] plus keyboard ALIASES appended below it:
     * SERA also assigns single letters to two digraph families -- "x" spells
     * ሸ (= "sh") and "c" spells ቸ (= "ch") -- and those are the spellings
     * the keyboard's X and C keys rely on, since every key carries exactly
     * one Latin letter. Aliases share the SAME [Family] instance as their
     * digraph spelling rather than repeating the forms literal, so the two
     * spellings can never drift apart. Note "c" (ቸ) vs "C" (ጨ) is a real
     * case distinction, like h/H and t/T: the exact-case-first matching rule
     * in Transliterator is what keeps shift+C reaching ጨ.
     */
    private val baseFamilies: Map<String, Family> = mapOf(
        // ----- ሀ series -------------------------------------------------
        "h" to Family("ሀሁሂሃሄህሆ"),
        "H" to Family("ሐሑሒሓሔሕሖ"),

        // ----- ለ / መ / ሠ-block ------------------------------------------
        "l" to Family("ለሉሊላሌልሎ", ua = 'ሏ'),
        "m" to Family("መሙሚማሜምሞ", ua = 'ሟ'),
        "s" to Family("ሰሱሲሳሴስሶ", ua = 'ሷ'),
        "S" to Family("ሠሡሢሣሤሥሦ"),
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
        "Ts" to Family("ፀፁፂፃፄፅፆ"),
        "C" to Family("ጨጩጪጫጬጭጮ", ua = 'ጯ'),
        "ph" to Family("ጰጱጲጳጴጵጶ"),
        "P" to Family("ጰጱጲጳጴጵጶ"),

        // ----- ፈ / ፐ ----------------------------------------------------
        "f" to Family("ፈፉፊፋፌፍፎ", ua = 'ፏ'),
        "p" to Family("ፐፑፒፓፔፕፖ")
    )

    val families: Map<String, Family> = baseFamilies + mapOf(
        // Keyboard aliases -- see the doc above [baseFamilies].
        "x" to baseFamilies.getValue("sh"),
        "c" to baseFamilies.getValue("ch")
    )

    /**
     * Consonant spellings sorted longest-first, for greedy matching:
     * "sh" must be tried before "s", "gn" before "g", "kh" before "k",
     * "zh" before "z", "ts" before "t", "ch" before "c", "ph" before "p".
     *
     * (Within the same length, order is irrelevant. Distinct spellings of
     * equal length can't shadow each other in the exact-case pass; "c"/"C"
     * could in principle shadow each other in the case-INSENSITIVE pass,
     * but never reach it -- an input starting with either letter always
     * matches its own exact-case entry first.)
     */
    val consonantsByLength: List<String> =
        families.keys.sortedByDescending { it.length }

    /**
     * The single-letter subset of [consonantsByLength], used by
     * [Transliterator.candidates] for the "separated" reading at a digraph
     * position that resolves an ambiguous digraph like "sh" as two
     * consonants (ስ+ህ) instead of one (ሽ). Every multi-letter spelling
     * breaks down into single letters that all exist here ("sh"->s+h,
     * "ts"->t+s, "gn"->g+n, ...), so a split reading is always producible.
     */
    val singleCharConsonants: List<String> =
        consonantsByLength.filter { it.length == 1 }

    /**
     * The velar ኀ series (ኀኁኂኃኄኅኆ). Not a keyboard-typeable family of its
     * own -- there's no key and no Latin spelling that reaches it directly --
     * it exists only as a *secondary* alternate reading of h/H (see
     * [consonantAlternates]), so a user who wants ኅ can pick it from the
     * suggestion strip without an extra dead key. Kept out of [families] on
     * purpose so it never enters [consonantsByLength] and starts matching
     * real input.
     */
    private val velarH = Family("ኀኁኂኃኄኅኆ")

    /** Velar ኀ series — alternate reading of h/H, not directly typeable. */
    val velarFamily: Family get() = velarH

    /**
     * Consonant spelling -> the ORDERED list of alternate families offered in
     * the suggestion strip so the user doesn't have to reach for shift (or a
     * spelling they don't know). Reading N of the strip flips every consonant
     * to its Nth alternate (1-based); a consonant with fewer alternates than
     * that just renders as itself and dedupes away.
     *
     *   - h/H -> [the other case form, then the velar ኀ series]: typing "h"
     *     surfaces ሀ… , ሐ… and ኀ… readings.
     *   - s/S -> [the other case form]: ሰ… and ሠ….
     *   - ts/Ts -> [the other case form]: ጸ… and ፀ….
     *   - k -> [the ቀ (q) series]: typing "k" primarily writes ከ/ክ but also
     *     offers the ቅ family as a secondary reading, since the two are easy
     *     to confuse and share a key in the user's head.
     *   - t -> [the ጠ (T) series]: typing "t" primarily writes ተ/ት but also
     *     offers the ጥ family as a secondary reading.
     *
     * c/C (ቸ/ጨ) is still left out: it'd flip on nearly every common word,
     * burying the useful readings under noise. It stays reachable the old way,
     * by pressing shift. Bare vowels a/A etc. have the same duality but are
     * handled through [bareVowels] directly, not this map.
     */
    val consonantAlternates: Map<String, List<Family>> = mapOf(
        "h" to listOf(families.getValue("H"), velarH),
        "H" to listOf(families.getValue("h"), velarH),
        "s" to listOf(families.getValue("S")),
        "S" to listOf(families.getValue("s")),
        "ts" to listOf(families.getValue("Ts")),
        "Ts" to listOf(families.getValue("ts")),
        "k" to listOf(families.getValue("q")),
        "t" to listOf(families.getValue("T"))
    )

    /**
     * How many alternate readings the strip can offer beyond the greedy one --
     * the longest alternate list in [consonantAlternates], but at least 1 so
     * the glottal<->pharyngeal bare-vowel flip (handled directly in
     * [Transliterator], not via the map) always gets its own reading too.
     */
    val maxAlternateLevel: Int =
        maxOf(1, consonantAlternates.values.maxOfOrNull { it.size } ?: 0)

    /**
     * Multi-tap alternate cycles: pressing the SAME key again within the
     * multi-tap window (see AddiyonKeyboardService.MULTI_TAP_TIMEOUT_MS)
     * REPLACES the just-typed Latin with the next spelling in its cycle,
     * instead of appending a new letter -- so a user can reach the alternate
     * families without shift or SERA spellings they may not know:
     *
     *   a -> አ ዓ ዐ ኣ      (glottal bare, pharyngeal+a, pharyngeal bare-A,
     *                        glottal+a -- the four "a"-looking standalones)
     *   u/i/o -> ኡ/ኢ/ኦ then ዑ/ዒ/ዖ   e -> እ then ዕ
     *   k -> ክ ቅ    h -> ህ ሕ    s -> ስ ሥ    t -> ት ጥ
     *   c -> ች ጭ    p -> ፕ ጵ    (+ uppercase inverses, so shift-first
     *                              taps can cycle back the other way)
     *
     * Keyed by the shift-RESOLVED output of the keypress; entries are Latin
     * spellings substituted into the raw buffer, so the whole-buffer
     * transliteration keeps working unchanged (after a consonant, "`a" makes
     * "se" + double-tapped "a" read ሰዓ -- the ሰዓት case). The service skips a
     * cycle step when it wouldn't change the rendered word in the current
     * context (e.g. "A" after a consonant is just the same vowel again).
     */
    val multiTapCycles: Map<String, List<String>> = mapOf(
        "a" to listOf("a", "`a", "A", "'a"),
        "u" to listOf("u", "`u"),
        "i" to listOf("i", "`i"),
        "o" to listOf("o", "`o"),
        "e" to listOf("e", "`"),
        "k" to listOf("k", "q"),
        "h" to listOf("h", "H"),
        "H" to listOf("H", "h"),
        "s" to listOf("s", "S"),
        "S" to listOf("S", "s"),
        "t" to listOf("t", "T"),
        "T" to listOf("T", "t"),
        "c" to listOf("c", "C"),
        "C" to listOf("C", "c"),
        "p" to listOf("p", "P"),
        "P" to listOf("P", "p")
    )

    /**
     * Ethiopic punctuation for the two punctuation keys on the Amharic
     * letter layout: "," -> ፣ (comma) and "." -> ። (full stop). Applied by
     * Transliterator's pass-through step, so the same mapping serves both
     * actual typing and the keys' corner previews.
     */
    val punctuation: Map<Char, Char> = mapOf(
        ',' to '፣',
        '.' to '።'
    )

    // ----------------------------------------------------------------------
    // Reverse fidel index + fuzzy-suggestion cost model
    // ----------------------------------------------------------------------

    /**
     * Fuzzy-suggestion substitution costs (see
     * [com.addiyon.keyboard.suggestion.WordTrie.fuzzySuggestions]).
     *
     * Two fidel in the SAME consonant family differ only by vowel order (ይ vs
     * ያ vs የ) -- overwhelmingly the "user hasn't added the vowel yet / picked
     * the wrong vowel" case, so it's cheap. A DIFFERENT consonant is a genuine
     * spelling error and costs enough to fall outside a 1-edit budget, so a
     * near-miss like የተለይ still surfaces የተለያ… words without every unrelated
     * word one keystroke away flooding the strip. Named so they stay tunable.
     */
    const val SAME_FAMILY_SUBSTITUTION_COST = 1
    const val DIFFERENT_CONSONANT_SUBSTITUTION_COST = 2

    /**
     * Every fidel char that belongs to a consonant family -> a stable id for
     * that family, so two fidel can be tested for "same consonant, different
     * vowel". Built once from [families] (deduped by Family identity, since
     * aliases like x/sh and c/ch share one instance) plus [velarH], which is a
     * real family that intentionally isn't in [families]. Collisions from
     * families that duplicate glyph content (ph/P) resolve to one id -- they
     * are the same glyphs, so that's correct.
     */
    val fidelFamilyId: Map<Char, Int> = buildMap {
        val idByFamily = HashMap<Family, Int>()
        fun register(family: Family) {
            val id = idByFamily.getOrPut(family) { idByFamily.size }
            for (form in family.forms) put(form, id)
            family.ua?.let { put(it, id) }
        }
        for (family in families.values) register(family)
        register(velarH)
    }

    /**
     * Substitution cost for the Amharic fuzzy pass: 0 for identical fidel,
     * [SAME_FAMILY_SUBSTITUTION_COST] when both are forms of the same consonant
     * family, else [DIFFERENT_CONSONANT_SUBSTITUTION_COST]. A char not in
     * [fidelFamilyId] (e.g. punctuation) only matches itself.
     */
    fun fidelSubstitutionCost(a: Char, b: Char): Int = when {
        a == b -> 0
        fidelFamilyId[a]?.let { it == fidelFamilyId[b] } == true ->
            SAME_FAMILY_SUBSTITUTION_COST
        else -> DIFFERENT_CONSONANT_SUBSTITUTION_COST
    }
}