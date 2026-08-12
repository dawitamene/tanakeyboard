package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HornMorphoNominalGoldenTest {
    data class Case(
        val id: String,
        val source: String,
        val kind: Int,
        val rawForm: String,
        val lemma: String,
        val sourceFeatures: String,
        val requestedFeatures: String,
        val typed: String,
        val surface: String,
        val normalizedKey: String,
        val shouldSuggest: Boolean,
        val oracleRecognized: Boolean,
        val oracleAnalysisCount: Int,
        val reason: String,
    )

    @Test
    fun kotlinSubsetMatchesPinnedHornMorphoGoldenCorpus() {
        val cases = loadCases()
        val positives = cases.filter { it.shouldSuggest }
        val negatives = cases.filterNot { it.shouldSuggest }

        assertTrue("expected at least 200 positive cases", positives.size >= 200)
        assertTrue("expected at least 100 negative cases", negatives.size >= 100)
        assertTrue("expected at least 40 lexemes", cases.map { it.kind to it.rawForm }.distinct().size >= 40)
        assertTrue("positive cases must be oracle-recognized", positives.all { it.oracleRecognized })
        assertTrue("negative near-misses must be rejected by the oracle", negatives.all { !it.oracleRecognized })

        cases.forEach { case ->
            val lexeme = AmharicNounMorphology.Lexeme(
                kind = case.kind,
                surface = case.lemma,
                features = case.sourceFeatures,
                frequency = 800,
            )
            val generated = AmharicNounMorphology.complete(
                typed = case.typed,
                lexemes = listOf(lexeme),
                limit = 200,
            ).map { it.word }
            assertEquals("${case.id}: ${case.reason}", case.shouldSuggest, case.surface in generated)
        }
    }

    @Test
    fun corpusCoversRequiredLexicalAndNormalizationClasses() {
        val cases = loadCases()
        val featureText = cases.joinToString("\n") { it.sourceFeatures }

        listOf("-pl", "-def", "p=0", "adp=0", "-gen", "-dis", "-col").forEach { feature ->
            assertTrue("missing restriction $feature", feature in featureText)
        }
        assertTrue(cases.any { it.kind == 1 })
        assertTrue(cases.any { it.kind == 2 })
        assertTrue(cases.any { it.kind == 3 })
        assertTrue(cases.any { '/' in it.rawForm })
        assertTrue(cases.any { it.rawForm.any { character -> character in '\u135D'..'\u135F' } })
        assertTrue(cases.any { it.source == "normalization_probe" && it.typed != it.surface })
        assertTrue(cases.groupBy { it.surface }.any { (_, values) -> values.map { it.kind }.distinct().size > 1 })
        cases.forEach { case ->
            assertEquals(case.normalizedKey, com.addiyon.keyboard.transliteration.EthiopicNormalizer.normalize(case.surface))
        }
    }

    @Test
    fun rawHornMorphoMarkersAreRemovedOnlyFromDisplayForms() {
        assertEquals("እናት", SQLiteMorphLexicon.cleanSurface("እ/ናት"))
        assertEquals("ቤት", SQLiteMorphLexicon.cleanSurface("ቤ፟ት"))
        assertFalse('/' in SQLiteMorphLexicon.cleanSurface("ቅ/ዱስ"))
    }

    private fun loadCases(): List<Case> {
        val lines = requireNotNull(javaClass.getResourceAsStream("/hornmorpho_nominal_golden.tsv"))
            .bufferedReader(Charsets.UTF_8)
            .use { it.readLines() }
        val header = parseTsv(lines.first()).withIndex().associate { it.value to it.index }
        fun List<String>.field(name: String): String = get(header.getValue(name))
        return lines.drop(1).filter { it.isNotEmpty() }.map { line ->
            val fields = parseTsv(line)
            Case(
                id = fields.field("id"),
                source = fields.field("source"),
                kind = fields.field("kind").toInt(),
                rawForm = fields.field("raw_form"),
                lemma = fields.field("lemma"),
                sourceFeatures = fields.field("source_features"),
                requestedFeatures = fields.field("requested_features"),
                typed = fields.field("typed"),
                surface = fields.field("surface"),
                normalizedKey = fields.field("normalized_key"),
                shouldSuggest = fields.field("should_suggest").toBooleanStrict(),
                oracleRecognized = fields.field("oracle_recognized").toBooleanStrict(),
                oracleAnalysisCount = fields.field("oracle_analysis_count").toInt(),
                reason = fields.field("reason"),
            )
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
