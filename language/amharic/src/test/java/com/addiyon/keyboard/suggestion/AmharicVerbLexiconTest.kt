package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.AmharicTable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AmharicVerbLexiconTest {
    @Test
    fun productionArtifactSupportsExactAnalysisAndStableIdentity() {
        val lexicon = productionLexicon()
        val row = goldenRows().first {
            it.getValue("source_class") == "regular" && it.getValue("root_frequency").toInt() > 0
        }
        val terminal = requireNotNull(lexicon.exact(row.getValue("surface")))

        assertEquals(row.getValue("surface"), terminal.surface)
        assertTrue(terminal.analyses.any { it.root == row.getValue("root") })
        assertTrue(terminal.bestAnalysis.rootFrequency > 0)
        assertTrue(terminal.bestAnalysis.lemmaId.startsWith("verb:"))
        assertTrue(terminal.bestAnalysis.analysisId.startsWith(terminal.bestAnalysis.lemmaId))
    }

    @Test
    fun everyCheckedInGoldenSurfaceHasItsOracleAnalysis() {
        val lexicon = productionLexicon()
        val rows = goldenRows()

        rows.forEach { row ->
            val terminal = requireNotNull(lexicon.exact(row.getValue("surface")))
            assertTrue(
                "missing ${row.getValue("lexeme_id")} for ${row.getValue("surface")}",
                terminal.analyses.any {
                    it.lexeme == row.getValue("lexeme_id") &&
                        it.root == row.getValue("root") &&
                        it.featureId == row.getValue("feature_id").toInt()
                },
            )
        }
        assertTrue(rows.any { it.getValue("source_class") == "irregular" })
        assertTrue(rows.any { it.getValue("source_class") == "light" })
        assertEquals(
            setOf("perfective", "imperfective", "jussive_imperative"),
            rows.mapTo(linkedSetOf()) { it.getValue("feature_name") },
        )
    }

    @Test
    fun prefixCompletionIsBoundedRankedAndIncludesLightVerbs() {
        val lexicon = productionLexicon()
        val regularSurface = goldenRows()
            .first { it.getValue("source_class") == "regular" }
            .getValue("surface")
        val lightSurface = goldenRows()
            .first { it.getValue("source_class") == "light" }
            .getValue("surface")
        val regularPrefix = regularSurface.take(2)
        val regular = lexicon.complete(regularPrefix, 8)
        val light = lexicon.complete(lightSurface.dropLast(1), 8)

        assertTrue(regular.any { it.surface.startsWith(regularPrefix) })
        assertTrue(light.any { terminal ->
            terminal.surface == lightSurface &&
                terminal.analyses.any { it.sourceClass == AmharicVerbSourceClass.LIGHT }
        })
        assertTrue(regular.size <= 8)
        assertTrue(light.size <= 8)
    }

    @Test
    fun exactLookupHasZeroFalsePositivesOnSystematicNearMisses() {
        val lexicon = productionLexicon()
        val surfaces = goldenRows().map { it.getValue("surface") }.distinct()
        var checked = 0

        surfaces.forEach { surface ->
            val nearMiss = surface + "ሃ"
            if (nearMiss !in surfaces) {
                assertNull(nearMiss, lexicon.exact(nearMiss))
                checked++
            }
        }

        assertTrue(checked >= 25)
        assertNull(lexicon.exact("ሃሃሃ"))
    }

    @Test
    fun fuzzyTraversalReturnsOnlyAnalyzerBackedVerbSurfaces() {
        val lexicon = productionLexicon()
        val results = lexicon.fuzzy(
            surface = "ለመድ",
            maxEdits = 1,
            limit = 8,
            substitutionCost = SubstitutionCost(AmharicTable::fidelSubstitutionCost),
            insertCost = 2,
            deleteCost = 2,
        )

        assertTrue(results.any { it.word == "ለመደ" })
        assertTrue(results.all { lexicon.exact(it.word) != null })
        assertFalse(results.any { it.word == "ሃሃሃ" })
    }

    @Test
    fun versionChecksumAndBoundsFailuresAreFailClosed() {
        val original = productionBytes()
        expectFailure(original.clone().also { it[5] = 3 }, "unsupported verb automaton")
        expectFailure(
            original.clone().also { it[it.lastIndex] = (it.last() + 1).toByte() },
            "verb automaton checksum mismatch",
        )
        val invalid = original.clone().also { bytes ->
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(24, Int.MAX_VALUE)
            val checksum = CRC32().apply { update(bytes, 48, bytes.size - 48) }.value.toInt()
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(44, checksum)
        }
        expectFailure(invalid, "invalid verb automaton records")
        assertFalse(AmharicVerbLexicon.EMPTY.isEnabled)
        assertTrue(AmharicVerbLexicon.EMPTY.complete("ለ", 8).isEmpty())
    }

    @Test
    fun shortPrefixAndExactP95StayInsideRuntimeBudgets() {
        val lexicon = productionLexicon()
        repeat(20) {
            lexicon.complete("ል", 15)
            lexicon.exact("አለ")
        }
        val prefixSamples = (0 until 500).map {
            measured { lexicon.complete("ል", 15) }
        }
        val exactSamples = (0 until 1_000).map {
            measured { lexicon.exact("አለ") }
        }
        val prefixP95 = percentile95(prefixSamples)
        val exactP95 = percentile95(exactSamples)

        println("PHASE8_METRIC verb_prefix_p95_ms=$prefixP95 verb_exact_p95_ms=$exactP95")
        assertTrue("prefix p95=${prefixP95}ms", prefixP95 <= 25.0)
        assertTrue("exact p95=${exactP95}ms", exactP95 <= 10.0)
    }

    private fun measured(block: () -> Unit): Double {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / 1_000_000.0
    }

    private fun percentile95(values: List<Double>): Double {
        val sorted = values.sorted()
        return sorted[((sorted.size * 95 + 99) / 100 - 1).coerceIn(sorted.indices)]
    }

    private fun goldenRows(): List<Map<String, String>> {
        val lines = requireNotNull(javaClass.getResourceAsStream("/hornmorpho_verb_phase8.tsv"))
            .bufferedReader()
            .use { it.readLines() }
        val header = lines.first().split('\t')
        return lines.drop(1).filter(String::isNotBlank).map { line ->
            header.zip(line.split('\t')).toMap()
        }
    }

    private fun productionLexicon() = AmharicVerbLexicon.fromBytes(productionBytes())

    private fun productionBytes(): ByteArray = listOf(
        File("src/main/assets/amharic_verbs.ahva"),
        File("language/amharic/src/main/assets/amharic_verbs.ahva"),
    ).first(File::isFile).readBytes()

    private fun expectFailure(bytes: ByteArray, expectedMessage: String) {
        try {
            AmharicVerbLexicon.fromBytes(bytes)
            fail("expected production artifact validation to fail")
        } catch (failure: IllegalArgumentException) {
            assertEquals(expectedMessage, failure.message)
        }
    }
}
