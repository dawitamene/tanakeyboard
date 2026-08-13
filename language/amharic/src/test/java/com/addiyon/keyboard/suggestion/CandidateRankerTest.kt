package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.Transliterator
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRankerTest {

    private inner class FakeDict(vararg words: Pair<String, Int>) {
        private val byWord: Map<String, Int> = words.toMap()
        private val byKey: Map<String, List<Pair<String, Int>>> = words
            .groupBy { (w, _) -> w }
            .mapValues { (_, v) -> v }

        fun frequencyOf(word: String): Int? = byWord[word]
        fun entriesFor(prefix: String, limit: Int): List<CandidateRanker.AmharicCandidate> {
            if (limit <= 0) return emptyList()
            val p = prefix
            return byWord.entries.asSequence()
                .filter { it.key.startsWith(p) }
                .sortedByDescending { it.value }
                .take(limit)
                .map { candidate(it.key, it.value) }
                .toList()
        }
    }

    private fun dict(vararg words: Pair<String, Int>) = FakeDict(*words)

    private fun candidate(
        word: String,
        lexicalFrequency: Int,
        source: CandidateRanker.CandidateSource = CandidateRanker.CandidateSource.EXACT_LEXEME,
        surfaceFrequency: Int? = null,
        morphologyCost: Int = 0,
    ) = CandidateRanker.AmharicCandidate(
        word = word,
        source = source,
        lexicalFrequency = lexicalFrequency,
        surfaceFrequency = surfaceFrequency,
        morphologyCost = morphologyCost,
    )

    private fun rankAmharic(
        readings: List<String>,
        dict: FakeDict,
        visibleReadings: List<String> = emptyList(),
        fuzzyWords: List<CandidateRanker.FuzzyWord> = emptyList(),
        quirkReadings: Set<String> = emptySet(),
        ngramNext: Map<String, Int> = emptyMap()
    ): List<String> =
        CandidateRanker.rankAmharic(
            readings = readings,
            limit = 10,
            frequencyOf = dict::frequencyOf,
            completionsForPrefix = dict::entriesFor,
            visibleReadings = visibleReadings,
            fuzzyWords = fuzzyWords,
            quirkReadings = quirkReadings,
            ngramNext = ngramNext,
            normalize = EthiopicNormalizer::normalize
        )

    @Test
    fun promotesAnExactDictionaryMatchAheadOfAGreedyNonWord() {
        // The "fkr" scenario: ፍቅር is a real word, the structurally-greedy
        // ፍክር isn't -- ፍቅር must win the commit slot.
        val words = setOf("ፍቅር")
        val ranked = CandidateRanker.rank(listOf("ፍክር", "ፍቅር", "ፍኩር"), words::contains)
        assertEquals("ፍቅር", ranked.first())
    }

    @Test
    fun keepsStructuralOrderWhenNothingMatches() {
        val ranked = CandidateRanker.rank(listOf("ሽ", "ስህ"), isWord = { false })
        assertEquals(listOf("ሽ", "ስህ"), ranked)
    }

    @Test
    fun keepsStructuralOrderAmongMultipleExactMatches() {
        // No frequency reordering -- if two candidates are both real words,
        // the engine's own (structural) order among them is preserved.
        val words = setOf("ሰላም", "ሠላም")
        val ranked = CandidateRanker.rank(listOf("ሰላም", "ሠላም"), words::contains)
        assertEquals(listOf("ሰላም", "ሠላም"), ranked)
    }

    @Test
    fun isStableWithinEachPartition() {
        val words = setOf("b", "d")
        val ranked = CandidateRanker.rank(listOf("a", "b", "c", "d", "e"), words::contains)
        assertEquals(listOf("b", "d", "a", "c", "e"), ranked)
    }

    @Test
    fun emptyInputIsEmptyOutput() {
        assertEquals(emptyList<String>(), CandidateRanker.rank(emptyList(), isWord = { true }))
    }

    @Test
    fun bestCommitCandidatePromotesTheHighestScoringExactWord() {
        val words = dict("መስጠት" to 900)
        val readings = listOf("መስተት", "መስጠጥ", "መስጠት", "መስተጥ")
        assertEquals("መስጠት", CandidateRanker.bestCommitCandidate(readings, words::frequencyOf))
    }

    @Test
    fun bestCommitCandidateFallsBackToTheGreedyReadingWithoutExactWords() {
        val words = dict("ስህተት" to 800)
        assertEquals("ሽ", CandidateRanker.bestCommitCandidate(listOf("ሽ", "ስህ"), words::frequencyOf))
    }

    @Test
    fun explicitUppercaseFamilyKeepsGreedyReadingAheadOfDictionaryAlternates() {
        val readings = Transliterator.candidates("aCe")
        val words = dict("አቸ" to 900, "አቼ" to 800)
        val ranked = CandidateRanker.rankAmharic(
            readings = readings,
            limit = 10,
            frequencyOf = words::frequencyOf,
            completionsForPrefix = { _, _ -> emptyList() },
            preferGreedy = Transliterator.hasExplicitFamilySelection("aCe")
        )
        val committed = CandidateRanker.bestCommitCandidate(
            readings,
            words::frequencyOf,
            preferGreedy = Transliterator.hasExplicitFamilySelection("aCe")
        )
        assertEquals("አጨ", ranked.first())
        assertEquals("አጨ", committed)
        assertTrue("አቸ" in ranked)
        assertTrue("አቼ" in readings)
    }

    @Test
    fun hidesDeepAlternatesButKeepsTheGreedyLiteralWhenAnExactWordExists() {
        // The exact word wins, and the DEEP structural alternates (መስጠጥ, መስተጥ)
        // are hidden -- but the greedy literal (መስተት, index 0, what's shown
        // inline while typing) is always kept as the second chip so the user
        // can still commit exactly what they typed.
        val words = dict("መስጠት" to 900)
        val ranked = rankAmharic(
            listOf("መስተት", "መስጠጥ", "መስጠት", "መስተጥ"),
            words
        )
        assertEquals(listOf("መስጠት", "መስተት"), ranked)
    }

    @Test
    fun prefixOnlyReadingsStayAliveForCompletionsWithoutBeingShownAsWords() {
        val words = dict("ስህተት" to 800)
        val ranked = rankAmharic(listOf("ሽ", "ስህ"), words)
        assertEquals(listOf("ሽ", "ስህተት"), ranked)
        assertFalse("ስህ" in ranked)
    }

    @Test
    fun exactDictionaryWordLeadsWithTheGreedyLiteralKeptSecond() {
        // "fkr": the real word ፍቅር leads, but the greedy literal ፍክር (shown
        // inline) is kept as the second chip, ahead of any completion.
        val words = dict("ፍቅር" to 700, "ፍቅረኛ" to 200)
        val ranked = rankAmharic(listOf("ፍክር", "ፍቅር"), words)
        assertEquals("ፍቅር", ranked[0])
        assertEquals("ፍክር", ranked[1])
    }

    @Test
    fun exactDictionaryWordBeatsTwoStructuralAlternates() {
        val words = dict("ጴንጤ" to 850)
        val ranked = rankAmharic(Transliterator.candidates("pientie"), words)
        assertEquals("ጴንጤ", ranked.first())
    }

    @Test
    fun consonantVowelSplitsAreHiddenWithoutDictionaryBacking() {
        mapOf(
            "la" to "ላ",
            "le" to "ለ",
            "li" to "ሊ",
            "lo" to "ሎ",
            "lu" to "ሉ"
        ).forEach { (latin, greedy) ->
            val candidates = Transliterator.candidateReadings(latin)
            val readings = candidates.map { it.text }
            val quirks = candidates.filter { it.isQuirk }.mapTo(mutableSetOf()) { it.text }
            assertEquals(
                latin,
                listOf(greedy),
                rankAmharic(
                    readings = readings,
                    dict = dict(),
                    visibleReadings = readings,
                    quirkReadings = quirks
                )
            )
        }
    }

    @Test
    fun nonDictionaryStructuralReadingsDoNotContaminateSuggestions() {
        val readings = listOf("አለካሽን", "አለቃሽን")
        val ranked = rankAmharic(
            readings = readings,
            dict = dict(),
            visibleReadings = readings
        )
        assertEquals(listOf(readings.first()), ranked)
    }

    @Test
    fun generatedLexicalConstructCanSurfaceAnExactAlternateReading() {
        val ranked = CandidateRanker.rankAmharic(
            readings = listOf("ሰወን", "ሰውን"),
            limit = 10,
            frequencyOf = { null },
            completionsForPrefix = { reading, _ ->
                if (reading == "ሰውን") {
                    listOf(candidate("ሰውን", 100, CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY))
                } else {
                    emptyList()
                }
            },
        )

        assertEquals(listOf("ሰወን", "ሰውን"), ranked)
    }

    @Test
    fun orderFiveEAlternateRequiresAnExactDictionaryMatch() {
        val readings = Transliterator.candidates("le")
        assertEquals(
            listOf("ለ"),
            rankAmharic(readings, dict(), visibleReadings = readings)
        )
        assertEquals(
            listOf("ሌ", "ለ"),
            rankAmharic(readings, dict("ሌ" to 500), visibleReadings = readings)
        )
    }

    @Test
    fun splitVowelReadingAppearsOnlyForARealLexeme() {
        val candidates = Transliterator.candidateReadings("rEs")
        val readings = candidates.map { it.text }
        val quirks = candidates.filter { it.isQuirk }.mapTo(mutableSetOf()) { it.text }
        val ranked = rankAmharic(
            readings = readings,
            dict = dict("ርዕስ" to 900),
            visibleReadings = readings,
            quirkReadings = quirks
        )
        assertEquals(listOf("ረስ", "ርዕስ"), ranked)
        assertFalse("ርእስ" in ranked)
    }

    @Test
    fun splitVowelPrefixCanStillProduceARealDictionaryCompletion() {
        val candidates = Transliterator.candidateReadings("rE")
        val readings = candidates.map { it.text }
        val quirks = candidates.filter { it.isQuirk }.mapTo(mutableSetOf()) { it.text }
        val ranked = rankAmharic(
            readings = readings,
            dict = dict("ርዕስ" to 900),
            visibleReadings = readings,
            quirkReadings = quirks
        )
        assertEquals(listOf("ረ", "ርዕስ"), ranked)
        assertFalse("ርዕ" in ranked)
    }

    @Test
    fun exactWordsSuppressVisibleQuirkReadings() {
        val ranked = rankAmharic(
            readings = listOf("ሰላም", "ሰልአም"),
            dict = dict("ሰላም" to 900),
            visibleReadings = listOf("ሰልአም")
        )
        assertEquals(listOf("ሰላም"), ranked)
    }

    @Test
    fun aQuirkSplitDictionaryWordDoesNotHijackTheGreedyDefault() {
        // "me": greedy መ isn't a dictionary word, but the structural split ም+እ
        // (ምእ) is. The split must NOT become the default -- greedy መ stays the
        // literal default, and ምእ only rides along as a quirk/completion chip.
        val ranked = rankAmharic(
            readings = listOf("መ", "ምእ", "ምዕ"),
            dict = dict("ምእ" to 900, "ምዕ" to 900, "ምእመናን" to 500),
            visibleReadings = listOf("ምእ", "ምዕ"),
            quirkReadings = setOf("ምእ", "ምዕ")
        )
        assertEquals("መ", ranked.first())
        assertTrue("ምእ" in ranked)      // still offered as a chip
        assertTrue("ምእመናን" in ranked)   // split-prefix completion still works
    }

    @Test
    fun bestCommitCandidateIgnoresQuirkSplitWords() {
        // Same "me" case for the commit slot: space must land መ, not the split
        // dictionary word ምእ.
        val commit = CandidateRanker.bestCommitCandidate(
            listOf("መ", "ምእ", "ምዕ"),
            frequencyOf = mapOf("ምእ" to 900, "ምዕ" to 900)::get,
            quirkReadings = setOf("ምእ", "ምዕ")
        )
        assertEquals("መ", commit)
    }

    @Test
    fun bestCommitCandidateStillPromotesNonQuirkExactWords() {
        // "fkr": greedy ፍክር isn't a word, ፍቅር (a same-segmentation family swap,
        // not a quirk) is -- it must still win the commit.
        val commit = CandidateRanker.bestCommitCandidate(
            listOf("ፍክር", "ፍቅር"),
            frequencyOf = mapOf("ፍቅር" to 900)::get,
            quirkReadings = emptySet()
        )
        assertEquals("ፍቅር", commit)
    }

    @Test
    fun fuzzyWordsRankBelowTheLiteralFallback() {
        val ranked = CandidateRanker.rankAmharic(
            readings = listOf("የተለይ"),
            limit = 10,
            frequencyOf = { null },
            completionsForPrefix = { _, _ -> emptyList() },
            fuzzyWords = listOf(CandidateRanker.FuzzyWord("የተለያዩ", 500, 1))
        )
        assertEquals(listOf("የተለይ", "የተለያዩ"), ranked)
    }

    @Test
    fun ngramBoostReordersWithinTheExactTier() {
        // Both readings are words with frequencies 9_000 vs 10_000; a
        // context boost (max 10_000 > the 1_000 gap) flips the order.
        // (ሰላም/ሰላሳ, not a homoglyph pair like ሰላም/ሠላም -- boost keys are
        // folded, so homoglyph variants would collect the SAME boost.)
        val words = dict("ሰላም" to 9_000, "ሰላሳ" to 10_000)
        val ranked = rankAmharic(
            listOf("ሰላም", "ሰላሳ"), words,
            ngramNext = mapOf("ሰላም" to 255)
        )
        assertEquals(listOf("ሰላም", "ሰላሳ"), ranked)
        // Same input without context keeps the frequency order -- proving
        // the flip above came from the boost, and that an empty map is a
        // strict no-op.
        assertEquals(
            listOf("ሰላሳ", "ሰላም"),
            rankAmharic(listOf("ሰላም", "ሰላሳ"), words)
        )
    }

    @Test
    fun ngramBoostMatchesVariantSpellingsOfTheSameWord() {
        // The boost map is keyed by folded spelling; a candidate typed in a
        // variant spelling (ሠላም) must still collect the boost for ሰላም.
        val words = dict("ሠላም" to 9_000, "ሰላሳ" to 10_000)
        val ranked = rankAmharic(
            listOf("ሠላም", "ሰላሳ"), words,
            ngramNext = mapOf("ሰላም" to 255)
        )
        assertEquals(listOf("ሠላም", "ሰላሳ"), ranked)
    }

    @Test
    fun ngramBoostAppliesToCompletions() {
        val words = dict("ቤተሰብ" to 9_000, "ቤተመንግስት" to 10_000)
        val ranked = rankAmharic(
            listOf("ቤተ"), words,
            ngramNext = mapOf("ቤተሰብ" to 200)
        )
        assertEquals("ቤተሰብ", ranked.first { it != "ቤተ" })
    }

    @Test
    fun ngramBoostCannotPromoteAcrossTiers() {
        // ቤቱ is an exact reading (weakest possible frequency); ቤተሰብ is only
        // a completion (of the alternate reading ቤተ). Even a maximal context
        // boost on a maximal-frequency completion must not displace the
        // exact word from the top.
        val words = dict("ቤቱ" to 1, "ቤተሰብ" to 30_000)
        val ranked = rankAmharic(
            listOf("ቤተ", "ቤቱ"), words,
            ngramNext = mapOf("ቤተሰብ" to 255)
        )
        assertEquals("ቤቱ", ranked.first())
    }

    // ---- rankByContext (English completion reordering) ----

    private val lower: (String) -> String = { it.lowercase() }

    @Test
    fun rankByContextKeepsFrequencyOrderWithoutContext() {
        // No context map -> a strict frequency ranking, capped to the limit.
        val pool = listOf(
            CandidateRanker.DictionaryWord("lot", 50_000),
            CandidateRanker.DictionaryWord("love", 40_000),
            CandidateRanker.DictionaryWord("look", 35_000)
        )
        assertEquals(
            listOf("lot", "love", "look"),
            CandidateRanker.rankByContext(pool, emptyMap(), lower, 3)
        )
    }

    @Test
    fun rankByContextPromotesAPredictedCommonCompletion() {
        // All three saturate frequencyScore (>= 30k), so a context boost breaks
        // the tie: "love" rises over the more frequent but unpredicted "lot".
        val pool = listOf(
            CandidateRanker.DictionaryWord("lot", 50_000),
            CandidateRanker.DictionaryWord("love", 40_000),
            CandidateRanker.DictionaryWord("look", 35_000)
        )
        assertEquals(
            "love",
            CandidateRanker.rankByContext(pool, mapOf("love" to 255), lower, 3).first()
        )
    }

    @Test
    fun rankByContextCannotPromoteARareCompletionOverACommonOne() {
        // The capped boost (<= 10k) can't lift a genuinely rare word (freq 50)
        // over a common one whose frequencyScore saturates at 30k.
        val pool = listOf(
            CandidateRanker.DictionaryWord("the", 1_000_000),
            CandidateRanker.DictionaryWord("thistle", 50)
        )
        assertEquals(
            "the",
            CandidateRanker.rankByContext(pool, mapOf("thistle" to 255), lower, 2).first()
        )
    }

    @Test
    fun rankByContextMatchesContextCaseInsensitively() {
        // The boost map is keyed lowercase; a capitalized candidate ("England")
        // still collects its boost.
        val pool = listOf(
            CandidateRanker.DictionaryWord("English", 45_000),
            CandidateRanker.DictionaryWord("England", 40_000)
        )
        assertEquals(
            "England",
            CandidateRanker.rankByContext(pool, mapOf("england" to 255), lower, 2).first()
        )
    }

    @Test
    fun rankByContextRespectsLimitAndEmptyInputs() {
        assertTrue(CandidateRanker.rankByContext(emptyList(), emptyMap(), lower, 3).isEmpty())
        val pool = listOf(
            CandidateRanker.DictionaryWord("apple", 100),
            CandidateRanker.DictionaryWord("apply", 90)
        )
        assertEquals(listOf("apple"), CandidateRanker.rankByContext(pool, emptyMap(), lower, 1))
    }

    @Test
    fun invalidInputsReturnNoRankedCandidate() {
        assertNull(CandidateRanker.bestCommitCandidate(emptyList(), frequencyOf = { 1 }))
        assertTrue(
            CandidateRanker.rankAmharic(
                readings = emptyList(),
                limit = 3,
                frequencyOf = { null },
                completionsForPrefix = { _, _ -> emptyList() }
            ).isEmpty()
        )
        assertTrue(
            CandidateRanker.rankAmharic(
                readings = listOf("ሀ"),
                limit = 0,
                frequencyOf = { null },
                completionsForPrefix = { _, _ -> emptyList() }
            ).isEmpty()
        )
        assertTrue(
            CandidateRanker.rankByContext(
                candidates = listOf(CandidateRanker.DictionaryWord("hello", 100)),
                ngramNext = emptyMap(),
                normalize = lower,
                limit = 0
            ).isEmpty()
        )
    }

    @Test
    fun bestCommitCandidateKeepsEarlierHigherScoringWord() {
        val frequencies = mapOf("ሀ" to 500, "ሁ" to 100)

        assertEquals(
            "ሀ",
            CandidateRanker.bestCommitCandidate(
                candidates = listOf("ሀ", "ሁ"),
                frequencyOf = frequencies::get
            )
        )
    }

    @Test
    fun duplicateCompletionRowsKeepTheHighestScoringCopy() {
        val ranked = CandidateRanker.rankAmharic(
            readings = listOf("ሀ"),
            limit = 10,
            frequencyOf = { null },
            completionsForPrefix = { _, _ ->
                listOf(
                    candidate("ሀሁ", 100),
                    candidate("ሀሁ", 900),
                    candidate("ሀሁ", 900),
                    candidate("ሀሁ", 100)
                )
            }
        )

        assertEquals(listOf("ሀ", "ሀሁ"), ranked)
    }

    @Test
    fun preferGreedyLeavesAnAlreadyLeadingReadingUnchanged() {
        val ranked = CandidateRanker.rankAmharic(
            readings = listOf("ሀ", "ሁ"),
            limit = 10,
            frequencyOf = mapOf("ሀ" to 500, "ሁ" to 100)::get,
            completionsForPrefix = { _, _ -> emptyList() },
            preferGreedy = true
        )

        assertEquals(listOf("ሀ", "ሁ"), ranked)
    }

    @Test
    fun exactReadingBeatsEveryCompletionSource() {
        val ranked = CandidateRanker.rankAmharicDetailed(
            readings = listOf("ሰው"),
            limit = 10,
            frequencyOf = mapOf("ሰው" to 1)::get,
            completionsForPrefix = { _, _ ->
                listOf(
                    candidate("ሰውነት", 30_000),
                    candidate(
                        "ሰውን",
                        30_000,
                        CandidateRanker.CandidateSource.ATTESTED_SURFACE,
                        surfaceFrequency = 30_000,
                    ),
                    candidate(
                        "ሰዎች",
                        30_000,
                        CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                    ),
                )
            },
            fuzzyWords = listOf(CandidateRanker.FuzzyWord("ሰዋ", 30_000, 1)),
            ngramNext = mapOf("ሰውነት" to 255, "ሰውን" to 255, "ሰዎች" to 255),
            personalEvidence = mapOf(
                "ሰውነት" to CandidateRanker.PersonalEvidence(100, 500),
                "ሰውን" to CandidateRanker.PersonalEvidence(100, 500),
                "ሰዎች" to CandidateRanker.PersonalEvidence(100, 500),
            ),
        )

        assertEquals("ሰው", ranked.first().candidate.word)
        assertEquals(CandidateRanker.CandidateSource.EXACT_LEXEME, ranked.first().candidate.source)
        assertTrue(ranked.first().exactReading)
    }

    @Test
    fun attestedSurfaceBeatsUnattestedGenerationWithoutBorrowingStemFrequency() {
        val ranked = CandidateRanker.rankAmharicDetailed(
            readings = listOf("ቤ"),
            limit = 10,
            frequencyOf = { null },
            completionsForPrefix = { _, _ ->
                listOf(
                    candidate(
                        "ቤቶች",
                        1,
                        CandidateRanker.CandidateSource.ATTESTED_SURFACE,
                        surfaceFrequency = 2,
                    ),
                    candidate(
                        "ቤታችሁ",
                        1_000_000,
                        CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                    ),
                )
            },
        )

        val completions = ranked.filter { it.candidate.source != CandidateRanker.CandidateSource.GREEDY_LITERAL }
        assertEquals("ቤቶች", completions.first().candidate.word)
        assertEquals(2, completions.first().candidate.surfaceFrequency)
        assertEquals(null, completions.last().candidate.surfaceFrequency)
    }

    @Test
    fun personalEvidenceReordersOnlyCandidatesAlreadyValidated() {
        val ranked = CandidateRanker.rankAmharicDetailed(
            readings = listOf("የሰ"),
            limit = 10,
            frequencyOf = { null },
            completionsForPrefix = { _, _ ->
                listOf(
                    candidate(
                        "የሰው",
                        500,
                        CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                    ),
                    candidate(
                        "የሰላም",
                        500,
                        CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                    ),
                )
            },
            personalEvidence = mapOf(
                "የሰላም" to CandidateRanker.PersonalEvidence(20, 500),
                "የሰውው" to CandidateRanker.PersonalEvidence(100, 1_000),
            ),
        )

        val generated = ranked.filter {
            it.candidate.source == CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY
        }
        assertEquals("የሰላም", generated.first().candidate.word)
        assertTrue(CandidateRanker.CandidateSource.PERSONAL in generated.first().candidate.evidenceSources)
        assertFalse(ranked.any { it.candidate.word == "የሰውው" })
    }

    @Test
    fun fuzzyCannotCrossAValidGeneratedCompletion() {
        val ranked = CandidateRanker.rankAmharicDetailed(
            readings = listOf("ቤ"),
            limit = 10,
            frequencyOf = { null },
            completionsForPrefix = { _, _ ->
                listOf(
                    candidate(
                        "ቤታችን",
                        1,
                        CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                    )
                )
            },
            fuzzyWords = listOf(CandidateRanker.FuzzyWord("ቤተሰብ", Int.MAX_VALUE, 1)),
        )

        assertTrue(
            ranked.indexOfFirst { it.candidate.word == "ቤታችን" } <
                ranked.indexOfFirst { it.candidate.word == "ቤተሰብ" }
        )
    }

    @Test
    fun canonicalMorphologyWinsTiesAndOrderingIsDeterministic() {
        val rank = {
            CandidateRanker.rankAmharicDetailed(
                readings = listOf("ቤ"),
                limit = 10,
                frequencyOf = { null },
                completionsForPrefix = { _, _ ->
                    listOf(
                        candidate(
                            "ቤትዋ",
                            500,
                            CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                            morphologyCost = 1,
                        ),
                        candidate(
                            "ቤቷ",
                            500,
                            CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                            morphologyCost = 0,
                        ),
                    )
                },
            )
        }
        val first = rank()

        assertEquals("ቤቷ", first.first { it.candidate.source == CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY }.candidate.word)
        repeat(20) { assertEquals(first, rank()) }
    }
}
