package com.addiyon.keyboard.suggestion

object NominalFeatureBits {
    const val PRODUCTIVE = 1L shl 0
    const val POS_NOUN = 1L shl 1
    const val POS_ADJECTIVE = 1L shl 2
    const val GENDER_FEMININE = 1L shl 3
    const val GENDER_MASCULINE = 1L shl 4
    const val ORDINARY_PLURAL = 1L shl 5
    const val DEFINITE = 1L shl 6
    const val POSSESSIVE = 1L shl 7
    const val ADPOSITION = 1L shl 8
    const val GENITIVE = 1L shl 9
    const val DISTRIBUTIVE = 1L shl 10
    const val COLLECTIVE = 1L shl 11
    const val INITIAL_DELETION = 1L shl 12
    const val HUMAN_SUFFIX = 1L shl 13
    const val PROPER_PERSON = 1L shl 14
    const val PROPER_PLACE = 1L shl 15
    const val HUMAN_BLOCKED = 1L shl 26

    val adpositions = listOf("የ", "ለ", "በ", "ከ", "እንደ", "ወደ", "እስከ", "ስለ", "በስተ", "ያለ")

    fun adpositionBit(adposition: String): Long {
        val index = adpositions.indexOf(adposition)
        return if (index < 0) 0L else 1L shl (16 + index)
    }

    fun encode(features: NominalFeatures, kind: Int): Long {
        val properPerson = kind == 2
        val properPlace = kind == 3
        val nominalPart = features.partsOfSpeech.any {
            it == PartOfSpeech.NOUN || it == PartOfSpeech.ADJECTIVE
        }
        val sourceIsBase = features.definite != FeatureState.POSITIVE &&
            features.accusative != FeatureState.POSITIVE &&
            features.person !is PersonFeature.Values
        val productive = !features.malformed && (properPerson || properPlace || (kind in 0..1 && nominalPart && sourceIsBase))
        if (!productive) return 0L

        var bits = PRODUCTIVE
        if (PartOfSpeech.NOUN in features.partsOfSpeech) bits = bits or POS_NOUN
        if (PartOfSpeech.ADJECTIVE in features.partsOfSpeech) bits = bits or POS_ADJECTIVE
        if (properPerson) bits = bits or PROPER_PERSON
        if (properPlace) bits = bits or PROPER_PLACE
        if (features.gender == Gender.FEMININE) bits = bits or GENDER_FEMININE
        if (features.gender == Gender.MASCULINE) bits = bits or GENDER_MASCULINE
        if (kind in 0..1 && features.plural != FeatureState.NEGATIVE) bits = bits or ORDINARY_PLURAL
        if (kind in 0..1 && features.definite != FeatureState.NEGATIVE) bits = bits or DEFINITE
        if (kind in 0..1 && features.person != PersonFeature.None) bits = bits or POSSESSIVE
        if (features.allowsAdposition) {
            bits = bits or ADPOSITION
            val allowed = features.allowedAdpositions ?: adpositions.toSet()
            allowed.forEach { bits = bits or adpositionBit(it) }
        }
        if (features.allowsGenitive) bits = bits or GENITIVE
        if (kind in 0..1 && features.allowsDistributive) bits = bits or DISTRIBUTIVE
        if ((kind in 0..1 || properPerson) && features.allowsCollective) bits = bits or COLLECTIVE
        if (features.flags["delinit"] != FeatureState.NEGATIVE) bits = bits or INITIAL_DELETION
        if (features.human == FeatureState.NEGATIVE) bits = bits or HUMAN_BLOCKED
        if (kind == 0 && PartOfSpeech.NOUN in features.partsOfSpeech && features.human != FeatureState.NEGATIVE) {
            bits = bits or HUMAN_SUFFIX
        }
        return bits
    }

    fun decode(bits: Long, stemClassCode: Int, kind: Int): NominalFeatures {
        fun has(bit: Long) = bits and bit != 0L
        val partsOfSpeech = buildSet {
            if (has(POS_NOUN)) add(PartOfSpeech.NOUN)
            if (has(POS_ADJECTIVE)) add(PartOfSpeech.ADJECTIVE)
            if (kind in 2..3) add(PartOfSpeech.PROPER_NOUN)
        }
        val gender = when {
            has(GENDER_FEMININE) -> Gender.FEMININE
            has(GENDER_MASCULINE) -> Gender.MASCULINE
            else -> null
        }
        val allowedAdpositions = adpositions.filterTo(linkedSetOf()) { has(adpositionBit(it)) }
            .takeIf(Set<String>::isNotEmpty)
        return NominalFeatures(
            partsOfSpeech = partsOfSpeech,
            gender = gender,
            stemClass = stemClass(stemClassCode),
            human = if (has(HUMAN_BLOCKED)) FeatureState.NEGATIVE else FeatureState.ABSENT,
            plural = if (has(ORDINARY_PLURAL)) FeatureState.ABSENT else FeatureState.NEGATIVE,
            definite = if (has(DEFINITE)) FeatureState.ABSENT else FeatureState.NEGATIVE,
            accusative = FeatureState.ABSENT,
            genitive = if (has(GENITIVE)) FeatureState.ABSENT else FeatureState.NEGATIVE,
            distributive = if (has(DISTRIBUTIVE)) FeatureState.ABSENT else FeatureState.NEGATIVE,
            collective = if (has(COLLECTIVE)) FeatureState.ABSENT else FeatureState.NEGATIVE,
            person = if (has(POSSESSIVE)) PersonFeature.Absent else PersonFeature.None,
            allowsAdposition = has(ADPOSITION),
            allowedAdpositions = allowedAdpositions,
            allowsGenitive = has(GENITIVE),
            allowsDistributive = has(DISTRIBUTIVE),
            allowsCollective = has(COLLECTIVE),
            flags = emptyMap(),
            enumerated = emptyMap(),
            unknownFeatures = emptyList(),
            malformed = false,
        )
    }

    fun stemClassCode(features: NominalFeatures): Int = when (features.stemClass) {
        StemClass.UNSPECIFIED -> 0
        StemClass.ORDINARY -> 1
        StemClass.ALTERNATE_AN -> 2
    }

    fun stemClass(code: Int): StemClass = when (code) {
        1 -> StemClass.ORDINARY
        2 -> StemClass.ALTERNATE_AN
        else -> StemClass.UNSPECIFIED
    }
}
