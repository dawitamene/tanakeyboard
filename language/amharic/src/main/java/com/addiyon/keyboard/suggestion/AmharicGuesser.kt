package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.AmharicTable
import com.addiyon.keyboard.transliteration.EthiopicNormalizer

object AmharicGuesser {
    private const val MAX_GUESS_CANDIDATES = 16

    fun guess(
        typed: String,
        limit: Int,
    ): List<CandidateRanker.AmharicCandidate> {
        if (typed.length < 2 || limit <= 0) return emptyList()
        val normalizedTyped = EthiopicNormalizer.normalize(typed)
        val contexts = NominalRuleGraph.prefixContexts(typed)
        val result = ArrayList<CandidateRanker.AmharicCandidate>(minOf(limit, MAX_GUESS_CANDIDATES))
        val seen = HashSet<String>()

        for ((context, fragment) in contexts) {
            if (fragment.length < 2) continue
            if (!isValidEthiopicStem(fragment)) continue

            val prefix = context.surface
            val baseForm = prefix + fragment
            if (baseForm.startsWith(typed) && seen.add(EthiopicNormalizer.normalize(baseForm))) {
                result += CandidateRanker.AmharicCandidate(
                    word = baseForm,
                    source = CandidateRanker.CandidateSource.GUESSER_MORPHOLOGY,
                    lexicalFrequency = 1,
                    morphologyCost = 3,
                )
            }

            val suffixes = listOf(
                "ን" to 3,
                "ዎች" to 4,
                "ው" to 4,
                "ም" to 4,
                "ና" to 4,
                "ነው" to 5,
                "ዎችን" to 5,
            )

            for ((suffix, cost) in suffixes) {
                val candidate = baseForm + suffix
                val normalized = EthiopicNormalizer.normalize(candidate)
                if (normalized.startsWith(normalizedTyped) && seen.add(normalized)) {
                    result += CandidateRanker.AmharicCandidate(
                        word = candidate,
                        source = CandidateRanker.CandidateSource.GUESSER_MORPHOLOGY,
                        lexicalFrequency = 1,
                        morphologyCost = cost,
                    )
                    if (result.size >= limit) return result
                }
            }
        }
        return result
    }

    private fun isValidEthiopicStem(stem: String): Boolean {
        for (char in stem) {
            val code = char.code
            if (code !in 0x1200..0x137F) return false
            if (code in 0x1360..0x137C) return false
        }
        return true
    }
}
