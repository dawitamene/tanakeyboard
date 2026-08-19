package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer

data class DetailedMorphAnalysis(
    val surface: String,
    val lemma: String,
    val root: String? = null,
    val partOfSpeech: PartOfSpeech,
    val segmentation: String,
    val features: Map<String, String>,
    val uniMorphTag: String,
)

object AmharicMorphologyAnalyzer {
    fun analyze(
        surface: String,
        verbLexicon: AmharicVerbLexicon = AmharicVerbLexicon.EMPTY,
    ): List<DetailedMorphAnalysis> {
        if (surface.isEmpty()) return emptyList()
        val normalized = EthiopicNormalizer.normalize(surface)
        val analyses = ArrayList<DetailedMorphAnalysis>()

        if (verbLexicon.isEnabled) {
            val verbTerminal = verbLexicon.exact(normalized)
            if (verbTerminal != null) {
                for (analysis in verbTerminal.analyses) {
                    val uniMorph = buildString {
                        append("V")
                        append(";ACT")
                    }
                    analyses += DetailedMorphAnalysis(
                        surface = surface,
                        lemma = analysis.lexeme,
                        root = analysis.root,
                        partOfSpeech = PartOfSpeech.VERB,
                        segmentation = segmentVerb(surface, analysis.lexeme),
                        features = mapOf(
                            "pos" to "V",
                            "root" to analysis.root,
                            "lemma" to analysis.lexeme,
                        ),
                        uniMorphTag = uniMorph,
                    )
                }
            }
        }

        val nominalAnalyses = analyzeNominal(surface)
        analyses.addAll(nominalAnalyses)

        if (analyses.isEmpty()) {
            analyses += DetailedMorphAnalysis(
                surface = surface,
                lemma = surface,
                partOfSpeech = PartOfSpeech.NOUN,
                segmentation = surface,
                features = mapOf("pos" to "N"),
                uniMorphTag = "N",
            )
        }

        return analyses.sortedWith(
            compareByDescending<DetailedMorphAnalysis> { it.features.size }
                .thenByDescending { it.segmentation.count { ch -> ch == '-' } }
        )
    }

    fun segment(surface: String, verbLexicon: AmharicVerbLexicon = AmharicVerbLexicon.EMPTY): String {
        return analyze(surface, verbLexicon).firstOrNull()?.segmentation ?: surface
    }

    private fun analyzeNominal(surface: String): List<DetailedMorphAnalysis> {
        val contexts = NominalRuleGraph.prefixContexts(surface)
        val result = ArrayList<DetailedMorphAnalysis>()

        for ((context, fragment) in contexts) {
            if (fragment.isEmpty()) continue
            val prefixes = ArrayList<String>()
            val tags = ArrayList<String>()
            val featMap = LinkedHashMap<String, String>()

            featMap["pos"] = "N"
            tags.add("N")

            if (context.adposition != null) {
                prefixes.add(context.adposition)
                featMap["adp"] = context.adposition
            }
            if (context.marker == NominalPrefixMarker.DISTRIBUTIVE) {
                prefixes.add("እየ")
                featMap["dis"] = "+"
                tags.add("DIST")
            } else if (context.marker == NominalPrefixMarker.COLLECTIVE) {
                prefixes.add("እነ")
                featMap["col"] = "+"
                tags.add("COLL")
            }

            var core = fragment
            val suffixes = ArrayList<String>()

            val conjunction = listOf("ም", "ስ", "ማ", "ሳ", "ና", "ኮ", "ጋ").firstOrNull { core.endsWith(it) && core.length > it.length }
            if (conjunction != null) {
                suffixes.add(0, conjunction)
                core = core.dropLast(conjunction.length)
                featMap["conj"] = conjunction
            }

            val copula = listOf("ነው", "ነኝ", "ነህ", "ነሽ", "ናት", "ነች", "ነን", "ናቸው", "ነበር").firstOrNull { core.endsWith(it) && core.length > it.length }
            if (copula != null) {
                suffixes.add(0, copula)
                core = core.dropLast(copula.length)
                featMap["cop"] = copula
                tags.add("COP")
            }

            if (core.endsWith("ን") && core.length > 1) {
                suffixes.add(0, "ን")
                core = core.dropLast(1)
                featMap["case"] = "acc"
                tags.add("ACC")
            }

            val possessiveMap = mapOf(
                "አችን" to ("PSS1P" to "1p"),
                "አችሁ" to ("PSS2P" to "2p"),
                "አቸው" to ("PSS3P" to "3p"),
                "ችን" to ("PSS1P" to "1p"),
                "ችሁ" to ("PSS2P" to "2p"),
                "ቸው" to ("PSS3P" to "3p"),
                "ዬ" to ("PSS1S" to "1s"),
                "ህ" to ("PSS2MS" to "2ms"),
                "ሽ" to ("PSS2FS" to "2fs"),
                "ው" to ("PSS3MS" to "3ms"),
                "ዋ" to ("PSS3FS" to "3fs"),
            )
            var hasPluralWithPossessive = false
            if (core.endsWith("ቻችን") || core.endsWith("ቻችሁ") || core.endsWith("ቻቸው")) {
                hasPluralWithPossessive = true
            }
            val possEntry = possessiveMap.entries.firstOrNull {
                core.endsWith(it.key) && core.length > it.key.length && !(it.key == "ው" && core == "ሰው")
            }
            if (possEntry != null) {
                val possSurface = if (hasPluralWithPossessive && !possEntry.key.startsWith("አ")) "አ" + possEntry.key else possEntry.key
                suffixes.add(0, possSurface)
                core = core.dropLast(possEntry.key.length)
                featMap["poss"] = possEntry.value.second
                tags.add(possEntry.value.first)
            }

            if (hasPluralWithPossessive && core.endsWith("ቻ")) {
                suffixes.add(0, "ኦች")
                core = core.dropLast(1)
                featMap["num"] = "pl"
                tags.add("PL")
            } else {
                val plural = listOf("ዎች", "ኦች", "ያን", "ች").firstOrNull { core.endsWith(it) && core.length > it.length }
                if (plural != null) {
                    suffixes.add(0, plural)
                    core = core.dropLast(plural.length)
                    featMap["num"] = "pl"
                    tags.add("PL")
                }
            }

            if (core.isNotEmpty()) {
                val order = com.addiyon.keyboard.transliteration.AmharicTable.orderIndexOfFidel(core.last())
                if (core.length > 1 && (order == 6 || order == 1 || order == 0)) {
                    val bare = com.addiyon.keyboard.transliteration.AmharicTable.bareFormOfFidel(core.last())
                    if (bare != null) {
                        core = core.dropLast(1) + bare
                    }
                } else if (core == "ሰ") {
                    core = "ሰው"
                }

                val segList = ArrayList<String>()
                segList.addAll(prefixes)
                segList.add(core)
                segList.addAll(suffixes)

                result += DetailedMorphAnalysis(
                    surface = surface,
                    lemma = core,
                    partOfSpeech = PartOfSpeech.NOUN,
                    segmentation = segList.joinToString("-"),
                    features = featMap,
                    uniMorphTag = tags.distinct().joinToString(";"),
                )
            }
        }
        return result
    }

    private fun segmentVerb(surface: String, lemma: String): String {
        return if (surface.length > lemma.length && surface.contains(lemma)) {
            val idx = surface.indexOf(lemma)
            val pre = surface.substring(0, idx)
            val post = surface.substring(idx + lemma.length)
            listOfNotNull(pre.takeIf(String::isNotEmpty), lemma, post.takeIf(String::isNotEmpty)).joinToString("-")
        } else {
            surface
        }
    }
}
