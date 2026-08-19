package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer
import java.io.File
import java.sql.DriverManager
import java.util.Random
import java.util.zip.GZIPInputStream
import org.junit.Ignore
import org.junit.Test

class LegacyDictionaryCoverageTest {
    @Ignore("Long running benchmark analysis on legacy dictionary")
    @Test
    fun analyzeLegacyDictionaryCoverage() {
        val legacyFile = File("/Users/dev/code/addiyon-keyboard/archive/legacy-amharic-dictionary/corpus_surface_words.dat")
        if (!legacyFile.exists()) return

        val dbFile = File("/Users/dev/code/addiyon-keyboard/language/amharic/build/generated/dictionaryAssets/amharic.db")
        val ahrfFile = File("/Users/dev/code/addiyon-keyboard/language/amharic/src/main/assets/amharic_verbs.ahrf")

        val verbLexicon = if (ahrfFile.exists()) AmharicVerbLexicon.fromBytes(ahrfFile.readBytes()) else AmharicVerbLexicon.EMPTY

        val connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

        val exactWords = HashSet<String>()
        connection.createStatement().executeQuery("SELECT key FROM words").use { rs ->
            while (rs.next()) exactWords.add(rs.getString(1))
        }

        val baseLexemes = HashMap<String, MutableList<AmharicNounMorphology.Lexeme>>()
        connection.createStatement().executeQuery("SELECT lexeme_id, kind, key, features, morph_bits, stem_class FROM morph_lexemes WHERE key IS NOT NULL").use { rs ->
            while (rs.next()) {
                val key = rs.getString(3)
                val lex = AmharicNounMorphology.Lexeme(
                    lexemeId = rs.getLong(1),
                    kind = rs.getInt(2),
                    surface = key,
                    features = rs.getString(4),
                    frequency = 1,
                    morphBits = rs.getLong(5),
                    stemClassCode = rs.getInt(6),
                )
                baseLexemes.getOrPut(key) { ArrayList() }.add(lex)
            }
        }

        var totalWords = 0
        var coveredByVerbs = 0
        var coveredByExact = 0
        var coveredByNominal = 0
        var coveredByLight = 0
        val ungeneratedWords = ArrayList<Pair<String, Int>>()

        val reader = GZIPInputStream(legacyFile.inputStream().buffered()).bufferedReader()
        reader.useLines { lines ->
            for (line in lines) {
                val tab = line.indexOf('\t')
                val rawWord = if (tab >= 0) line.substring(0, tab) else line
                val freq = if (tab >= 0) line.substring(tab + 1).toIntOrNull() ?: 1 else 1
                val word = rawWord.trim()
                if (word.isEmpty() || freq <= 5) continue
                totalWords++

                val norm = EthiopicNormalizer.normalize(word)

                if (exactWords.contains(norm)) {
                    coveredByExact++
                    continue
                }

                if (verbLexicon.isEnabled && verbLexicon.exact(norm) != null) {
                    coveredByVerbs++
                    continue
                }

                if (AmharicLightVerb.isPreverb(norm)) {
                    coveredByLight++
                    continue
                }

                var nominalFound = false
                val contexts = NominalRuleGraph.prefixContexts(norm)
                for ((prefix, fragment) in contexts) {
                    val stemCandidates = NominalRuleGraph.reverseStemCandidates(fragment)
                    for (stem in stemCandidates) {
                        val matchingLexemes = baseLexemes[stem] ?: continue
                        for (lex in matchingLexemes) {
                            val generated = NominalRuleGraph.generate(lex, prefix, norm, 64)
                            if (generated.any { EthiopicNormalizer.normalize(it.word) == norm }) {
                                nominalFound = true
                                break
                            }
                        }
                        if (nominalFound) break
                    }
                    if (nominalFound) break
                }

                if (nominalFound) {
                    coveredByNominal++
                } else {
                    ungeneratedWords.add(word to freq)
                }
            }
        }

        val totalCovered = coveredByExact + coveredByVerbs + coveredByNominal + coveredByLight
        val ungeneratedCount = ungeneratedWords.size
        val coveragePct = (totalCovered.toDouble() / totalWords.toDouble()) * 100.0

        println("=== LEGACY DICTIONARY MORPHOLOGICAL COVERAGE REPORT ===")
        println("Total legacy dictionary words: $totalWords")
        println("Words generated/covered by engine: $totalCovered (${String.format("%.2f", coveragePct)}%)")
        println("  - Exact Lexemes/Dictionary: $coveredByExact")
        println("  - Verb FST Generated: $coveredByVerbs")
        println("  - Nominal Rule Graph Generated: $coveredByNominal")
        println("  - Preverbs / Light Verbs: $coveredByLight")
        println("Words that CANNOT be generated: $ungeneratedCount (${String.format("%.2f", 100.0 - coveragePct)}%)")

        val random = Random(42)
        val sampled = ArrayList<Pair<String, Int>>()
        val indices = HashSet<Int>()
        while (sampled.size < 10 && indices.size < ungeneratedWords.size) {
            val idx = random.nextInt(ungeneratedWords.size)
            if (indices.add(idx)) {
                sampled.add(ungeneratedWords[idx])
            }
        }

        println("\n=== 10 RANDOM UNGENERATED EXAMPLES ===")
        sampled.forEachIndexed { i, (w, freq) ->
            println("${i + 1}. $w (frequency: $freq)")
        }

        val topFreqUngenerated = ungeneratedWords.sortedByDescending { it.second }.take(10)
        println("\n=== TOP 10 FREQUENT UNGENERATED WORDS ===")
        topFreqUngenerated.forEachIndexed { i, (w, freq) ->
            println("${i + 1}. $w (frequency: $freq)")
        }

        val wordsDatFile = File("/Users/dev/code/addiyon-keyboard/language/amharic/src/dictionary/amharic_words.dat")
        val existingWords = LinkedHashMap<String, Pair<String, Int>>()
        if (wordsDatFile.exists()) {
            GZIPInputStream(wordsDatFile.inputStream().buffered()).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val w = line.substring(0, tab)
                    val f = line.substring(tab + 1).toIntOrNull() ?: 1
                    val k = EthiopicNormalizer.normalize(w)
                    existingWords[k] = w to f
                }
            }
        }

        fun isEthiopicWord(s: String): Boolean = s.length >= 2 && s.all { ch ->
            ch in '\u1200'..'\u137A' || ch in '\u1380'..'\u139F' || ch in '\u2D80'..'\u2DDE' || ch in '\uAB01'..'\uAB2E'
        }

        var addedCount = 0
        for ((word, freq) in ungeneratedWords) {
            if (!isEthiopicWord(word)) continue
            val k = EthiopicNormalizer.normalize(word)
            val current = existingWords[k]
            if (current == null) {
                existingWords[k] = word to freq
                addedCount++
            } else {
                existingWords[k] = current.first to maxOf(current.second, freq)
            }
        }

        java.util.zip.GZIPOutputStream(wordsDatFile.outputStream().buffered()).bufferedWriter().use { writer ->
            existingWords.values.sortedBy { EthiopicNormalizer.normalize(it.first) }.forEach { (w, f) ->
                writer.write("$w\t$f\n")
            }
        }
        println("\nMerged $addedCount new words into amharic_words.dat. Total dictionary words now: ${existingWords.size}")
    }
}
