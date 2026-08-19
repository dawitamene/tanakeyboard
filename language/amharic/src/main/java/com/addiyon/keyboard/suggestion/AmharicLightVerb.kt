package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.EthiopicNormalizer

object AmharicLightVerb {
    private val PREVERBS = listOf(
        "ዝም", "ብድግ", "ቁጭ", "ደስ", "እምቢ", "እሺ", "ፈገግ", "እልም", "ጸጥ",
        "ብልጭ", "ውልብ", "ቸል", "ከፍ", "ዝቅ", "ቀርብ", "ቅልጥ", "ትርፍ",
        "ድንቅ", "ዱብ", "ጥልቅ", "ፍንድቅ", "ፍንክች", "ጎንበስ", "ዞር", "ዋጥ",
        "ሞቅ", "ቀዝቀዝ", "በረድ", "ብቅ", "ልብ", "ብርግድ", "ጠብ", "እልል",
        "እፎይ", "እንቢ", "ዳዴ", "ፈንጠር", "ፎቀቅ", "ፏ", "ጥምጥም", "እፍ",
        "ቅስም", "ቅጥቅጥ", "ክትክት", "ክው", "ከረር", "ወለል", "ወለም", "ዥው",
    )

    private val ALE_FORMS = listOf(
        "አለ", "አለች", "አሉ", "አልኩ", "አልክ", "አልሽ", "አልን", "አላችሁ",
        "ይላል", "ትላለች", "ይላሉ", "እላለሁ", "ትላለህ", "ትያለሽ", "እንላለን", "ትላላችሁ",
        "ብሎ", "ብላ", "ብለው", "ብዬ", "ብለህ", "ብለሽ", "ብለን", "ብላችሁ",
        "እንዲል", "እንዳለ", "ሲል", "ስትል", "ሲሉ", "አትበል", "አትበይ", "አትበሉ",
        "አላለም", "አላለችም", "አላሉም", "አይልም", "አትልም", "አይሉም",
    )

    private val ADEREGE_FORMS = listOf(
        "አደረገ", "አደረገች", "አደረጉ", "አደረግሁ", "አደረግን",
        "ያደርጋል", "ታደርጋለች", "ያደርጋሉ", "አደርጋለሁ",
        "አድርጎ", "አድርጋ", "አድርገው", "አድርጌ",
        "አድርግ", "አድርጊ", "አድርጉ", "ያድርግ",
        "አላደረገም", "አያደርግም", "አታድርግ",
    )

    private val ASENYE_FORMS = listOf(
        "አሰኘ", "አሰኘች", "አሰኙ", "ያሰኛል", "አሰኝቶ", "አያሰኝም",
    )

    fun complete(typed: String, limit: Int): List<CandidateRanker.AmharicCandidate> {
        if (typed.isEmpty() || limit <= 0) return emptyList()
        val normalized = EthiopicNormalizer.normalize(typed)
        val result = ArrayList<CandidateRanker.AmharicCandidate>(limit)
        val seen = HashSet<String>()

        for (preverb in PREVERBS) {
            val normPreverb = EthiopicNormalizer.normalize(preverb)
            val matchesPrefix = normPreverb.startsWith(normalized) ||
                normalized.startsWith(normPreverb) ||
                normalized.startsWith("$normPreverb ") ||
                normalized.startsWith(normPreverb)

            if (!matchesPrefix) continue

            val forms = when (preverb) {
                "ደስ" -> ALE_FORMS + ASENYE_FORMS
                "እምቢ", "እሺ" -> ALE_FORMS + ADEREGE_FORMS
                else -> ALE_FORMS + ADEREGE_FORMS
            }

            for (form in forms) {
                val phrase = "$preverb $form"
                val normPhrase = EthiopicNormalizer.normalize(phrase)
                val unspacedPhrase = "$preverb$form"
                val normUnspaced = EthiopicNormalizer.normalize(unspacedPhrase)

                if (normPhrase.startsWith(normalized) || normUnspaced.startsWith(normalized) || normPreverb.startsWith(normalized)) {
                    if (seen.add(normPhrase)) {
                        result += CandidateRanker.AmharicCandidate(
                            word = phrase,
                            source = CandidateRanker.CandidateSource.GENERATED_MORPHOLOGY,
                            lexicalFrequency = 500,
                            morphologyCost = 1,
                        )
                        if (result.size >= limit) return result
                    }
                }
            }
        }
        return result
    }

    fun isPreverb(stem: String): Boolean =
        EthiopicNormalizer.normalize(stem) in PREVERBS.map(EthiopicNormalizer::normalize)
}
