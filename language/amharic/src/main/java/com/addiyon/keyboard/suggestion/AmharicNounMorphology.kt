package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.AmharicTable
import com.addiyon.keyboard.transliteration.EthiopicNormalizer

object AmharicNounMorphology {
    data class Lexeme(
        val kind: Int,
        val surface: String,
        val features: String,
        val frequency: Int,
    )

    data class Query(
        val prefix: String,
        val exactStemSurfaces: Set<String>,
        val completionStemPrefix: String,
    )

    private data class GeneratedForm(val word: String, val cost: Int)

    private val prefixes = listOf(
        "እንደ", "እስከ", "በስተ", "ስለ", "ወደ", "ያለ",
        "በየ", "ለየ", "ከየ", "የ", "በ", "ለ", "ከ",
    )
    private val distributivePrefixes = setOf("በየ", "ለየ", "ከየ")

    private val directPossessives = listOf(
        "ህ", "ሽ", "ዎ", "ዎን", "ዎት",
    )
    private val transformedPossessives = listOf(
        "ችን", "ችሁ", "ቸው",
    )
    private const val GENERATED_FREQUENCY_DIVISOR = 8
    private const val MAX_REVERSE_DEPTH = 3

    fun query(typed: String): Query {
        val prefix = prefixes.firstOrNull { typed.length > it.length && typed.startsWith(it) }.orEmpty()
        val stemFragment = typed.removePrefix(prefix)
        return Query(
            prefix = prefix,
            exactStemSurfaces = reverseStemCandidates(stemFragment),
            completionStemPrefix = stemFragment,
        )
    }

    fun complete(
        typed: String,
        lexemes: List<Lexeme>,
        limit: Int,
        alreadyFound: List<CandidateRanker.DictionaryWord> = emptyList(),
    ): List<CandidateRanker.DictionaryWord> {
        if (typed.isEmpty() || limit <= 0 || lexemes.isEmpty()) return emptyList()
        val query = query(typed)
        val normalizedTyped = EthiopicNormalizer.normalize(typed)
        val seen = HashSet<String>()
        alreadyFound.forEach { seen += EthiopicNormalizer.normalize(it.word) }
        val candidates = ArrayList<Pair<GeneratedForm, Lexeme>>()

        for (lexeme in lexemes) {
            val features = NominalFeatureParser.parse(lexeme.features, lexeme.kind)
            if (!isProductiveNoun(lexeme, features)) continue
            if (!allowsPrefix(features, query.prefix)) continue
            for (form in inflectedForms(lexeme, query.prefix, features)) {
                if (EthiopicNormalizer.normalize(form.word).startsWith(normalizedTyped)) {
                    candidates += form to lexeme
                }
            }
        }
        return candidates
            .sortedWith(
                compareBy<Pair<GeneratedForm, Lexeme>> {
                    it.first.word.length - typed.length
                }.thenBy { it.first.cost }
                    .thenByDescending { it.second.frequency }
                    .thenBy { it.first.word }
            )
            .mapNotNull { (form, lexeme) ->
                if (!seen.add(EthiopicNormalizer.normalize(form.word))) return@mapNotNull null
                CandidateRanker.DictionaryWord(
                    word = form.word,
                    frequency = (lexeme.frequency / GENERATED_FREQUENCY_DIVISOR).coerceAtLeast(1),
                )
            }
            .take(limit)
    }

    fun analyze(surface: String, lexemes: List<Lexeme>): List<Lexeme> {
        if (surface.isEmpty()) return emptyList()
        val query = query(surface)
        val normalized = EthiopicNormalizer.normalize(surface)
        return lexemes.filter { lexeme ->
            val features = NominalFeatureParser.parse(lexeme.features, lexeme.kind)
            isProductiveNoun(lexeme, features) &&
                allowsPrefix(features, query.prefix) &&
                inflectedForms(lexeme, query.prefix, features).any {
                    EthiopicNormalizer.normalize(it.word) == normalized
                }
        }
    }

    private fun isProductiveNoun(lexeme: Lexeme, features: NominalFeatures): Boolean {
        if (features.malformed) return false
        if (lexeme.kind in 2..3) return true
        if (lexeme.kind !in 0..1) return false
        if (features.partsOfSpeech.none { it == PartOfSpeech.NOUN || it == PartOfSpeech.ADJECTIVE }) {
            return false
        }
        if (features.definite == FeatureState.POSITIVE || features.accusative == FeatureState.POSITIVE) {
            return false
        }
        return features.person !is PersonFeature.Values
    }

    private fun inflectedForms(
        lexeme: Lexeme,
        prefix: String,
        features: NominalFeatures,
    ): List<GeneratedForm> {
        val singular = lexeme.surface
        val numbers = buildList {
            add(singular)
            pluralOf(lexeme, features)?.let(::add)
        }
        val forms = LinkedHashMap<String, Int>()
        for ((numberIndex, number) in numbers.withIndex()) {
            val plural = numberIndex > 0
            val numberCost = if (plural) 1 else 0
            fun add(form: String, cost: Int) {
                val prefixed = prefix + form
                forms.merge(prefixed, cost, ::minOf)
                forms.merge(prefixed + "ን", cost + 1, ::minOf)
            }

            add(number, numberCost)
            if (lexeme.kind !in 2..3 && features.definite != FeatureState.NEGATIVE) {
                definiteOf(number, feminine = features.gender == Gender.FEMININE, plural = plural)
                    ?.let { add(it, numberCost + 1) }
            }
            if (features.person != PersonFeature.None && lexeme.kind !in 2..3) {
                possessiveForms(number, plural).forEach { add(it, numberCost + 2) }
            }
        }
        return forms.map { GeneratedForm(it.key, it.value) }
    }

    private fun pluralOf(lexeme: Lexeme, features: NominalFeatures): String? {
        if (lexeme.kind in 2..3 || features.plural == FeatureState.NEGATIVE) return null
        val stem = lexeme.surface
        if (stem.isEmpty()) return null
        if (features.stemClass == StemClass.ALTERNATE_AN) {
            if (stem.endsWith("ዊ")) return stem.dropLast(1) + "ውያን"
            return if (AmharicTable.orderIndexOfFidel(stem.last()) == AmharicTable.BARE_FORM_INDEX) {
                replaceLastWithOrder(stem, 3)?.plus("ን")
            } else {
                null
            }
        }
        val finalOrder = AmharicTable.orderIndexOfFidel(stem.last()) ?: return null
        return if (finalOrder == AmharicTable.BARE_FORM_INDEX) {
            replaceLastWithOrder(stem, 6)?.plus("ች")
        } else {
            stem + "ዎች"
        }
    }

    private fun definiteOf(surface: String, feminine: Boolean, plural: Boolean): String? {
        if (surface.isEmpty()) return null
        if (feminine && !plural) {
            val finalOrder = AmharicTable.orderIndexOfFidel(surface.last())
            val labialized = if (finalOrder == AmharicTable.BARE_FORM_INDEX) {
                AmharicTable.labializedFormOfFidel(surface.last())
            } else {
                null
            }
            return if (labialized == null) surface + "ዋ" else surface.dropLast(1) + labialized
        }
        if (surface.last() == 'ው') return null
        return if (AmharicTable.orderIndexOfFidel(surface.last()) == AmharicTable.BARE_FORM_INDEX) {
            replaceLastWithOrder(surface, 1)
        } else {
            surface + "ው"
        }
    }

    private fun possessiveForms(surface: String, plural: Boolean): List<String> = buildList {
        if (surface.isEmpty()) return@buildList
        add(surface + "ህ")
        add(surface + "ሽ")
        add(transformedSuffix(surface, "ችን"))
        add(transformedSuffix(surface, "ችሁ"))
        add(transformedSuffix(surface, "ቸው"))
        add(surface + "ዎ")
        add(surface + "ዎን")
        add(surface + "ዎት")
        firstPersonSingular(surface)?.let(::add)
        definiteOf(surface, feminine = false, plural = plural)?.let(::add)
        if (!plural) definiteOf(surface, feminine = true, plural = false)?.let(::add)
    }.distinct()

    private fun firstPersonSingular(surface: String): String? {
        if (surface.isEmpty()) return null
        return if (AmharicTable.orderIndexOfFidel(surface.last()) == AmharicTable.BARE_FORM_INDEX) {
            replaceLastWithOrder(surface, 4)
        } else {
            surface + "ዬ"
        }
    }

    private fun transformedSuffix(surface: String, suffix: String): String {
        val finalOrder = AmharicTable.orderIndexOfFidel(surface.last())
        if (finalOrder == AmharicTable.BARE_FORM_INDEX) {
            return requireNotNull(replaceLastWithOrder(surface, 3)) + suffix
        }
        return when (finalOrder) {
            2, 4 -> surface + "ያ" + suffix
            1, 6 -> surface + "አ" + suffix
            else -> surface + suffix
        }
    }

    private fun replaceLastWithOrder(surface: String, orderIndex: Int): String? {
        if (surface.isEmpty()) return null
        val replacement = AmharicTable.formInSameFamily(surface.last(), orderIndex) ?: return null
        return surface.dropLast(1) + replacement
    }

    private fun reverseStemCandidates(fragment: String): Set<String> {
        if (fragment.isEmpty()) return emptySet()
        val seen = linkedSetOf(fragment)
        var frontier = listOf(fragment)
        repeat(MAX_REVERSE_DEPTH) {
            val next = ArrayList<String>()
            for (candidate in frontier) {
                fun offer(value: String) {
                    if (value.isNotEmpty() && seen.add(value)) next += value
                }

                if (candidate.endsWith("ዎች")) offer(candidate.dropLast(2))
                if (candidate.endsWith("ውያን")) offer(candidate.dropLast(3) + "ዊ")
                if (candidate.endsWith("ች")) {
                    val without = candidate.dropLast(1)
                    offer(reverseLastToBare(without))
                }
                for (suffix in transformedPossessives) {
                    if (candidate.endsWith(suffix) && candidate.length > suffix.length) {
                        val remainder = candidate.dropLast(suffix.length)
                        offer(remainder)
                        offer(reverseLastToBare(remainder))
                        if (remainder.endsWith("ያ") || remainder.endsWith("አ")) {
                            offer(remainder.dropLast(1))
                        }
                    }
                }
                for (suffix in directPossessives) {
                    if (candidate.endsWith(suffix) && candidate.length > suffix.length) {
                        offer(candidate.dropLast(suffix.length))
                    }
                }
                if (candidate.endsWith("ን") && candidate.length > 1) {
                    val without = candidate.dropLast(1)
                    offer(without)
                    offer(reverseLastToBare(without))
                }
                if ((candidate.endsWith("ው") || candidate.endsWith("ዋ") || candidate.endsWith("ዬ")) &&
                    candidate.length > 1
                ) {
                    offer(candidate.dropLast(1))
                }
                offer(reverseLastToBare(candidate))
            }
            frontier = next
            if (frontier.isEmpty()) return seen
        }
        return seen
    }

    private fun reverseLastToBare(surface: String): String {
        if (surface.isEmpty()) return surface
        val bare = AmharicTable.bareFormOfFidel(surface.last()) ?: return surface
        return surface.dropLast(1) + bare
    }

    private fun allowsPrefix(features: NominalFeatures, prefix: String): Boolean {
        if (prefix.isEmpty()) return true
        if (!features.allowsAdposition) return false
        val adposition = if (prefix in distributivePrefixes) prefix.dropLast(1) else prefix
        if (features.allowedAdpositions != null && adposition !in features.allowedAdpositions) return false
        if (prefix.endsWith("የ") && !features.allowsGenitive) return false
        if (prefix in distributivePrefixes && !features.allowsDistributive) return false
        return true
    }
}
