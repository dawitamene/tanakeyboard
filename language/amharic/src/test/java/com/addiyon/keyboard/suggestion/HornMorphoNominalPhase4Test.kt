package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HornMorphoNominalPhase4Test {
    data class Case(
        val id: String,
        val kind: Int,
        val rawForm: String,
        val lemma: String,
        val sourceFeatures: String,
        val surface: String,
        val normalizedKey: String,
        val classification: String,
        val oracleRecognized: Boolean,
        val oracleAnalysisCount: Int,
        val reason: String,
    ) {
        fun lexeme() = AmharicNounMorphology.Lexeme(
            kind = kind,
            surface = lemma,
            features = sourceFeatures,
            frequency = 800,
            lexemeId = id.hashCode().toLong(),
        )
    }

    @Test
    fun declaredNominalCorpusMatchesAtLeastNinetyFivePercentExactly() {
        val supported = loadCases().filter { it.classification == "supported" }
        val mismatches = supported.filterNot { case ->
            AmharicNounMorphology.completeCandidates(case.surface, listOf(case.lexeme()), 8)
                .any { it.normalizedKey == case.normalizedKey }
        }
        val matchRatio = (supported.size - mismatches.size).toDouble() / supported.size

        assertTrue("expected at least 300 supported forms", supported.size >= 300)
        assertTrue(
            "Phase 4 parity ${(matchRatio * 100).toInt()}%; mismatches=${mismatches.take(30).map { it.id to it.surface }}",
            matchRatio >= 0.95,
        )
    }

    @Test
    fun negativeCorpusHasZeroRuntimeFalsePositives() {
        val negatives = loadCases().filter { it.classification == "negative" }
        val falsePositives = negatives.filter { case ->
            AmharicNounMorphology.completeCandidates(case.surface, listOf(case.lexeme()), 8)
                .any { it.normalizedKey == case.normalizedKey }
        }

        assertTrue("expected at least 90 Phase 4 negatives", negatives.size >= 90)
        assertTrue("false positives=${falsePositives.map { it.id to it.surface }}", falsePositives.isEmpty())
    }

    @Test
    fun everySupportedSurfaceRoundTripsThroughTheSameRuleGraph() {
        val failures = loadCases().filter { it.classification == "supported" }.mapNotNull { case ->
            val lexeme = case.lexeme()
            val forward = AmharicNounMorphology.completeCandidates(case.surface, listOf(lexeme), 8)
                .filter { it.normalizedKey == case.normalizedKey }
            val reverse = AmharicNounMorphology.analyses(case.surface, listOf(lexeme))
            val queryFindsLexeme = AmharicNounMorphology.query(case.surface).exactStemSurfaces
                .map(EthiopicNormalizer::normalize)
                .contains(EthiopicNormalizer.normalize(case.lemma))
            val sharedAnalysis = reverse.any { analysis ->
                forward.any { it.analysis.affixes == analysis.affixes }
            }
            if (forward.isNotEmpty() && sharedAnalysis && queryFindsLexeme) null else {
                "${case.id}:${case.surface}:forward=${forward.isNotEmpty()},reverse=$sharedAnalysis,query=$queryFindsLexeme"
            }
        }

        assertTrue("round-trip failures=${failures.take(30)}", failures.isEmpty())
    }

    @Test
    fun explicitExclusionsRemainOracleRecognizedAndDocumented() {
        val exclusions = loadCases().filter { it.classification == "excluded" }

        assertTrue(exclusions.size >= 3)
        assertTrue(exclusions.all { it.oracleRecognized })
        assertTrue(exclusions.all { it.reason.startsWith("excluded ") })
        assertTrue(exclusions.all { case ->
            AmharicNounMorphology.completeCandidates(case.surface, listOf(case.lexeme()), 8)
                .none { it.normalizedKey == case.normalizedKey }
        })
    }

    @Test
    fun structuredAnalysesRetainAmbiguityAndPreferCanonicalOrthography() {
        val house = AmharicNounMorphology.Lexeme(0, "ቤት", "'' [pos=N,-h]", 800, lexemeId = 7)
        val ambiguous = AmharicNounMorphology.analyses("ቤቱ", listOf(house))
        val canonical = AmharicNounMorphology.completeCandidates("ቤቷ", listOf(house), 8)
            .first { it.word == "ቤቷ" }
        val alternate = AmharicNounMorphology.completeCandidates("ቤትዋ", listOf(house), 8)
            .first { it.word == "ቤትዋ" }

        assertTrue(ambiguous.any { it.affixes.lastOrNull() is NominalAffix.Definite })
        assertTrue(ambiguous.any { it.affixes.lastOrNull() is NominalAffix.Possessive })
        assertTrue(canonical.orthographicCost() < alternate.orthographicCost())
    }

    @Test
    fun completionSearchIsBoundedByTheRankingBuffer() {
        val lexemes = (1..100).map { index ->
            AmharicNounMorphology.Lexeme(0, "ቤት", "'' [pos=N]", index, lexemeId = index.toLong())
        }
        val candidates = AmharicNounMorphology.completeCandidates("ቤ", lexemes, limit = 15)

        assertTrue(candidates.size <= 60)
        assertFalse(candidates.isEmpty())
    }

    private fun MorphCandidate.orthographicCost(): Int = analysis.orthographicCost

    private fun loadCases(): List<Case> {
        val lines = requireNotNull(javaClass.getResourceAsStream("/hornmorpho_nominal_phase4.tsv"))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readLines() }
        val header = parseTsv(lines.first()).withIndex().associate { it.value to it.index }
        fun List<String>.field(name: String): String = get(header.getValue(name))
        return lines.drop(1).filter(String::isNotEmpty).map { line ->
            val fields = parseTsv(line)
            Case(
                id = fields.field("id"),
                kind = fields.field("kind").toInt(),
                rawForm = fields.field("raw_form"),
                lemma = fields.field("lemma"),
                sourceFeatures = fields.field("source_features"),
                surface = fields.field("surface"),
                normalizedKey = fields.field("normalized_key"),
                classification = fields.field("classification"),
                oracleRecognized = fields.field("oracle_recognized").toBooleanStrict(),
                oracleAnalysisCount = fields.field("oracle_analysis_count").toInt(),
                reason = fields.field("reason"),
            ).also { case ->
                assertEquals(case.normalizedKey, EthiopicNormalizer.normalize(case.surface))
            }
        }
    }

    private fun parseTsv(line: String): List<String> {
        val fields = ArrayList<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    value.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == '\t' && !quoted -> {
                    fields += value.toString()
                    value.clear()
                }
                else -> value.append(character)
            }
            index++
        }
        fields += value.toString()
        return fields
    }
}
