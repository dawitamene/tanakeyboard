package com.addiyon.keyboard.language.amharic

import com.addiyon.keyboard.suggestion.AmharicNounMorphology
import com.addiyon.keyboard.suggestion.AmharicVerbLexicon
import com.addiyon.keyboard.suggestion.CandidateRanker
import com.addiyon.keyboard.suggestion.NominalFeatureBits
import com.addiyon.keyboard.suggestion.NominalFeatureParser
import com.addiyon.keyboard.suggestion.SQLiteMorphLexicon
import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmharicSuggestionEngineIntegrationTest {
    @Test
    fun generatedVerbCandidatesUseMorphologyTierWithoutSQLiteSurfaceRows() =
        withDatabase { connection ->
            val lexicon = productionVerbLexicon()
            val expected = requireNotNull(lexicon.exact("ለመደ"))
            assertNull(word(connection, expected.surface))
            val suggestions = complete(connection, "lemede", lexicon)

            assertTrue(expected.surface in suggestions)
        }

    @Test
    fun mixedVowelOrderReadingAppearsOnlyWhenVerbArtifactValidatesIt() =
        withDatabase { connection ->
            assertNull(word(connection, "ሄደ"))

            assertFalse("ሄደ" in complete(connection, "hede", AmharicVerbLexicon.EMPTY))
            assertTrue("ሄደ" in complete(connection, "hede", productionVerbLexicon()))
        }

    @Test
    fun exactLexemeStillBeatsVerbAndNominalGeneration() = withDatabase { connection ->
        val suggestions = complete(connection, "sew", productionVerbLexicon())

        assertEquals("ሰው", suggestions.first())
    }

    @Test
    fun verbArtifactFailureLeavesNominalPipelineOperational() = withDatabase { connection ->
        val suggestions = complete(connection, "sewn", AmharicVerbLexicon.EMPTY)

        assertTrue("ሰውን" in suggestions)
    }

    @Test
    fun nounSuggestionLatencyDoesNotMateriallyRegressWithVerbProvider() =
        withDatabase { connection ->
            val lexicon = productionVerbLexicon()
            val inputs = listOf("sewn", "yesewn", "betoch", "bietu")
            repeat(5) {
                inputs.forEach { raw ->
                    complete(connection, raw, AmharicVerbLexicon.EMPTY)
                    complete(connection, raw, lexicon)
                }
            }
            val withoutVerb = ArrayList<Double>()
            val withVerb = ArrayList<Double>()
            repeat(40) { index ->
                val raw = inputs[index % inputs.size]
                if (index % 2 == 0) {
                    withoutVerb += measured { complete(connection, raw, AmharicVerbLexicon.EMPTY) }
                    withVerb += measured { complete(connection, raw, lexicon) }
                } else {
                    withVerb += measured { complete(connection, raw, lexicon) }
                    withoutVerb += measured { complete(connection, raw, AmharicVerbLexicon.EMPTY) }
                }
            }
            val baselineP95 = percentile95(withoutVerb)
            val verbP95 = percentile95(withVerb)

            println("PHASE8_METRIC nominal_baseline_p95_ms=$baselineP95 nominal_with_verbs_p95_ms=$verbP95")
            assertTrue("nominal with verb p95=${verbP95}ms", verbP95 <= 75.0)
            assertTrue(
                "nominal regression baseline=${baselineP95}ms with_verbs=${verbP95}ms",
                verbP95 <= maxOf(5.0, baselineP95 * 2.5),
            )
        }

    @Test
    fun exactLexemeBeatsGeneratedCompletions() = withDatabase { connection ->
        val suggestions = complete(connection, "sew")

        assertEquals("ሰው", suggestions.first())
        assertTrue(suggestions.any { it != "ሰው" && it.startsWith("ሰው") })
    }

    @Test
    fun generatedAccusativeAppearsWithoutSurfaceDictionaryRows() = withDatabase { connection ->
        assertNull(word(connection, "ሰውን"))
        assertNull(word(connection, "የሰውን"))

        assertTrue("ሰውን" in complete(connection, "sewn"))
        assertTrue("የሰውን" in complete(connection, "yesewn"))
    }

    @Test
    fun contaminatedAlternatesRequireLexicalOrMorphologicalValidation() = withDatabase { connection ->
        val leSuggestions = complete(connection, "le")
        assertFalse("ሌ" in leSuggestions)
        assertFalse("ሌላ" in leSuggestions)
        assertFalse("ሌሊት" in leSuggestions)
        val beSuggestions = complete(connection, "be")
        assertFalse("ቤ" in beSuggestions)
        assertFalse("ቤት" in beSuggestions)
        assertTrue("ቤት" in complete(connection, "bet"))
        assertFalse("ርዕ" in complete(connection, "rE"))
        assertTrue(
            complete(connection, "rEs").any {
                EthiopicNormalizer.normalize(it) == EthiopicNormalizer.normalize("ርዕስ")
            }
        )
    }

    @Test
    fun duplicateNameAndPlaceAnalysesProduceOneChip() = withDatabase { connection ->
        val suggestions = complete(connection, "hana")

        assertEquals(
            1,
            suggestions.count {
                EthiopicNormalizer.normalize(it) == EthiopicNormalizer.normalize("ሀና")
            },
        )
    }

    @Test
    fun unavailableDatabaseReturnsSafeLiteralBehavior() {
        val pipeline = AmharicSuggestionPipeline.prepare("sewn")
        val suggestions = AmharicSuggestionPipeline.rank(
            context = pipeline,
            limit = 15,
            frequencyOf = { null },
            completionsForPrefix = { _, _ -> emptyList() },
        )

        assertEquals(listOf(pipeline.readings.first()), suggestions)
    }

    @Test
    fun morphologyQueryUsesTheIndexedKeyPlan() = withDatabase { connection ->
        val morphology = AmharicNounMorphology.query("የሰውን")
        val query = requireNotNull(
            SQLiteMorphLexicon.nounQuery(
                morphology.exactStemSurfaces,
                morphology.completionStemPrefix,
                48,
                EthiopicNormalizer::normalize,
            )
        )
        val details = connection.prepareStatement("EXPLAIN QUERY PLAN ${query.sql}").use { statement ->
            query.args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.getString(4))
                }
            }
        }

        assertTrue(details.any { it.contains("idx_morph_lexemes_key") || it.contains("PRIMARY KEY") })
        assertFalse(details.any { it == "SCAN m" })
    }

    @Test
    fun materializedNominalFeaturesMatchTheRuntimeEncoding() = withDatabase { connection ->
        connection.prepareStatement(
            "SELECT kind, features, morph_bits, stem_class FROM morph_lexemes ORDER BY lexeme_id"
        ).use { statement ->
            statement.executeQuery().use { result ->
                var rows = 0
                while (result.next()) {
                    val kind = result.getInt(1)
                    val parsed = NominalFeatureParser.parse(result.getString(2), kind)
                    assertEquals(NominalFeatureBits.encode(parsed, kind), result.getLong(3))
                    assertEquals(NominalFeatureBits.stemClassCode(parsed), result.getInt(4))
                    rows++
                }
                assertEquals(18_867, rows)
            }
        }
    }

    @Test
    fun sparseSurfaceStatisticsAreAProperSubsetOfOracleValidatedForms() = withDatabase { connection ->
        val validKeys = oracleSupportedKeys("hornmorpho_nominal_golden.tsv") +
            oracleSupportedKeys("hornmorpho_nominal_phase4.tsv")
        val stats = connection.createStatement().executeQuery(
            "SELECT key, frequency FROM morph_surface_stats ORDER BY key"
        ).use { result ->
            buildMap {
                while (result.next()) put(result.getString(1), result.getInt(2))
            }
        }

        assertEquals(481, stats.size)
        assertTrue(stats.keys.all(validKeys::contains))
        assertTrue(stats.values.all { it > 0 })
        assertEquals(17_483, stats.getValue(EthiopicNormalizer.normalize("የሰው")))
        assertFalse(EthiopicNormalizer.normalize("ሃሃሃ") in stats)
        assertNull(word(connection, "የሰው"))
    }

    @Test
    fun generatedCandidatesReceiveSurfaceEvidenceOnlyAfterMorphologicalValidation() = withDatabase { connection ->
        val query = AmharicNounMorphology.query("ቤቶች")
        val candidates = AmharicNounMorphology.complete(
            typed = "ቤቶች",
            lexemes = nounLexemes(connection, query, 48),
            limit = 15,
            surfaceFrequencyOf = { surfaceFrequencies(connection, it) },
        )
        val attested = candidates.first { it.word == "ቤቶች" }

        assertEquals(CandidateRanker.CandidateSource.ATTESTED_SURFACE, attested.source)
        assertEquals(17_206, attested.surfaceFrequency)
        assertTrue(candidates.none { it.word == "ሃሃሃ" })
    }

    @Test
    fun warmNominalLookupP95StaysWithinFifteenMilliseconds() = withDatabase { connection ->
        val surfaces = listOf("ሰውን", "የሰውን", "ቤቶቹን", "ቤታችን", "መምህራን", "ቤትንም", "ቦታያችን")
        repeat(5) {
            surfaces.forEach { surface -> measuredMorphologyLookup(connection, surface) }
        }
        val samples = (0 until 100).map { index ->
            measuredMorphologyLookup(connection, surfaces[index % surfaces.size])
        }
        val p95 = percentile95(samples)

        println("PHASE4_METRIC nominal_lookup_p95_ms=$p95 max_ms=${samples.max()}")
        assertTrue("warm nominal lookup p95=${p95}ms samples=${samples.sorted().takeLast(10)}", p95 <= 15.0)
    }

    @Test
    fun warmEndToEndSuggestionP95StaysWithinPublicationBudget() = withDatabase { connection ->
        val rawInputs = listOf("sewn", "yesewn", "betoch", "bietu", "betachn", "memhran")
        repeat(5) { rawInputs.forEach { raw -> complete(connection, raw) } }
        val samples = (0 until 60).map { index ->
            val start = System.nanoTime()
            complete(connection, rawInputs[index % rawInputs.size])
            (System.nanoTime() - start) / 1_000_000.0
        }
        val p95 = percentile95(samples)
        val maximum = samples.max()

        println("PHASE4_METRIC suggestion_pipeline_p95_ms=$p95 max_ms=$maximum")
        assertTrue("warm suggestion p95=${p95}ms samples=${samples.sorted().takeLast(10)}", p95 <= 75.0)
        assertTrue("warm suggestion max=${maximum}ms", maximum <= 200.0)
    }

    private fun complete(
        connection: Connection,
        raw: String,
        verbLexicon: AmharicVerbLexicon = AmharicVerbLexicon.EMPTY,
    ): List<String> {
        val pipeline = AmharicSuggestionPipeline.prepare(raw)
        val frequencies = pipeline.readings.associateWith { reading ->
            connection.prepareStatement("SELECT freq FROM words WHERE key = ?").use { statement ->
                statement.setString(1, EthiopicNormalizer.normalize(reading))
                statement.executeQuery().use { result -> if (result.next()) result.getInt(1) else null }
            }
        }
        val completions = HashMap<String, List<CandidateRanker.AmharicCandidate>>()
        return AmharicSuggestionPipeline.rank(
            context = pipeline,
            limit = 15,
            frequencyOf = frequencies::get,
            completionsForPrefix = { prefix, limit ->
                completions.getOrPut(prefix) {
                    val direct = directCompletions(connection, prefix, limit)
                    val query = AmharicNounMorphology.query(prefix)
                    val lexemes = nounLexemes(connection, query, 48)
                    val nominal = AmharicNounMorphology.complete(
                        prefix,
                        lexemes,
                        limit,
                        direct,
                    ) { surfaceFrequencies(connection, it) }
                    val seen = (direct + nominal).mapTo(HashSet()) {
                        EthiopicNormalizer.normalize(it.word)
                    }
                    val verbs = verbLexicon.complete(prefix, limit).mapNotNull { terminal ->
                        if (!seen.add(EthiopicNormalizer.normalize(terminal.surface))) {
                            return@mapNotNull null
                        }
                        CandidateRanker.AmharicCandidate(
                            word = terminal.surface,
                            source = CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                            lexicalFrequency = terminal.bestAnalysis.rootFrequency,
                            morphologyCost = 1,
                        )
                    }
                    direct + nominal + verbs
                }
            },
        )
    }

    private fun productionVerbLexicon(): AmharicVerbLexicon {
        val artifact = listOf(
            File("src/main/assets/amharic_verbs.ahrf"),
            File("language/amharic/src/main/assets/amharic_verbs.ahrf"),
        ).first(File::isFile)
        return AmharicVerbLexicon.fromBytes(artifact.readBytes())
    }

    private fun directCompletions(
        connection: Connection,
        prefix: String,
        limit: Int,
    ): List<CandidateRanker.AmharicCandidate> {
        val key = EthiopicNormalizer.normalize(prefix)
        val upper = prefixEndBound(key)
        return connection.prepareStatement(
            "SELECT COALESCE(display, key), freq FROM words " +
                "WHERE key >= ? AND key < ? ORDER BY freq DESC, key LIMIT ?"
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, upper)
            statement.setInt(3, limit)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            CandidateRanker.AmharicCandidate(
                                word = result.getString(1),
                                source = CandidateRanker.CandidateSource.EXACT_LEXEME,
                                lexicalFrequency = result.getInt(2),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun nounLexemes(
        connection: Connection,
        morphology: AmharicNounMorphology.Query,
        limit: Int,
    ): List<AmharicNounMorphology.Lexeme> {
        val query = SQLiteMorphLexicon.nounQuery(
            morphology.exactStemSurfaces,
            morphology.completionStemPrefix,
            limit,
            EthiopicNormalizer::normalize,
        ) ?: return emptyList()
        return connection.prepareStatement(query.sql).use { statement ->
            query.args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            AmharicNounMorphology.Lexeme(
                                lexemeId = result.getLong(1),
                                kind = result.getInt(2),
                                surface = SQLiteMorphLexicon.cleanSurface(result.getString(3)),
                                features = result.getString(4),
                                morphBits = result.getLong(5),
                                stemClassCode = result.getInt(6),
                                frequency = result.getInt(7),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun word(connection: Connection, surface: String): String? =
        connection.prepareStatement("SELECT COALESCE(display, key) FROM words WHERE key = ?").use { statement ->
            statement.setString(1, EthiopicNormalizer.normalize(surface))
            statement.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
        }

    private fun surfaceFrequencies(
        connection: Connection,
        keys: Collection<String>,
    ): Map<String, Int> {
        val normalized = keys.map(EthiopicNormalizer::normalize).distinct()
        if (normalized.isEmpty()) return emptyMap()
        val placeholders = normalized.joinToString(",") { "?" }
        return connection.prepareStatement(
            "SELECT key, frequency FROM morph_surface_stats WHERE key IN ($placeholders)"
        ).use { statement ->
            normalized.forEachIndexed { index, key -> statement.setString(index + 1, key) }
            statement.executeQuery().use { result ->
                buildMap {
                    while (result.next()) put(result.getString(1), result.getInt(2))
                }
            }
        }
    }

    private fun oracleSupportedKeys(resource: String): Set<String> {
        val lines = requireNotNull(javaClass.classLoader?.getResourceAsStream(resource))
            .bufferedReader()
            .use { it.readLines() }
        val header = lines.first().split('\t')
        val surface = header.indexOf("surface")
        val recognized = header.indexOf("oracle_recognized")
        val classification = header.indexOf("classification")
        val shouldSuggest = header.indexOf("should_suggest")
        return lines.drop(1).mapNotNullTo(linkedSetOf()) { line ->
            val fields = line.split('\t')
            val supported = if (classification >= 0) {
                fields[classification] == "supported"
            } else {
                fields[shouldSuggest] == "true"
            }
            fields[surface].takeIf { supported && fields[recognized] == "true" }
                ?.let(EthiopicNormalizer::normalize)
        }
    }

    private fun measuredMorphologyLookup(connection: Connection, surface: String): Double {
        val start = System.nanoTime()
        val query = AmharicNounMorphology.query(surface)
        val lexemes = nounLexemes(connection, query, 48)
        AmharicNounMorphology.completeCandidates(surface, lexemes, 15)
        return (System.nanoTime() - start) / 1_000_000.0
    }

    private fun measured(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000.0
    }

    private fun percentile95(values: List<Double>): Double {
        val sorted = values.sorted()
        return sorted[((sorted.size * 95 + 99) / 100 - 1).coerceIn(sorted.indices)]
    }

    private fun prefixEndBound(prefix: String): String {
        val codePoint = prefix.codePointBefore(prefix.length)
        val start = prefix.length - Character.charCount(codePoint)
        return prefix.substring(0, start) + String(Character.toChars(codePoint + 1))
    }

    private fun withDatabase(block: (Connection) -> Unit) {
        val database = listOf(
            File("build/generated/dictionaryAssets/amharic.db"),
            File("language/amharic/build/generated/dictionaryAssets/amharic.db"),
        ).first { it.exists() }
        DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}").use(block)
    }
}
