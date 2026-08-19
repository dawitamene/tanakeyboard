package com.addiyon.keyboard.suggestion

enum class FeatureState {
    ABSENT,
    POSITIVE,
    NEGATIVE,
}

enum class PartOfSpeech(val hornMorphoValue: String) {
    NOUN("N"),
    ADJECTIVE("ADJ"),
    PROPER_NOUN("PROPN"),
    PRONOUN("PRON"),
    DETERMINER("DET"),
    NUMERAL("NUM"),
    VERBAL_NOUN("N_V"),
    ADVERB("ADV"),
    VERB("V"),
    COPULA("COP"),
    PREVERB("PV"),
}

enum class Gender {
    MASCULINE,
    FEMININE,
}

enum class StemClass {
    ORDINARY,
    ALTERNATE_AN,
    UNSPECIFIED,
}

sealed interface PersonFeature {
    data object Absent : PersonFeature
    data object None : PersonFeature
    data class Values(val values: Set<String>) : PersonFeature
}

data class NominalFeatures(
    val partsOfSpeech: Set<PartOfSpeech>,
    val gender: Gender?,
    val stemClass: StemClass,
    val human: FeatureState,
    val plural: FeatureState,
    val definite: FeatureState,
    val accusative: FeatureState,
    val genitive: FeatureState,
    val distributive: FeatureState,
    val collective: FeatureState,
    val person: PersonFeature,
    val allowsAdposition: Boolean,
    val allowedAdpositions: Set<String>?,
    val allowsGenitive: Boolean,
    val allowsDistributive: Boolean,
    val allowsCollective: Boolean,
    val flags: Map<String, FeatureState>,
    val enumerated: Map<String, Set<String>>,
    val unknownFeatures: List<String>,
    val malformed: Boolean,
)

object NominalFeatureParser {
    private val knownFlags = setOf(
        "acc", "col", "cop", "def", "delinit", "dis", "gen", "h", "hum", "irr", "pl",
    )
    private val knownValues = setOf("adp", "g", "p", "pos", "ps")
    private val partOfSpeechByValue = PartOfSpeech.entries.associateBy { it.hornMorphoValue }

    fun parse(raw: String, kind: Int): NominalFeatures {
        val block = featureBlock(raw)
        val malformed = block.malformed
        val flags = LinkedHashMap<String, FeatureState>()
        val enumerated = LinkedHashMap<String, Set<String>>()
        val unknown = ArrayList<String>()

        if (!malformed && block.content != null) {
            for (token in splitTopLevel(block.content)) {
                val trimmed = token.trim()
                if (trimmed.isEmpty()) continue
                when {
                    (trimmed.startsWith('+') || trimmed.startsWith('-')) &&
                        trimmed.drop(1).matches(IDENTIFIER) -> {
                        val name = trimmed.drop(1)
                        val state = if (trimmed.first() == '+') {
                            FeatureState.POSITIVE
                        } else {
                            FeatureState.NEGATIVE
                        }
                        flags[name] = state
                        if (name !in knownFlags) unknown += trimmed
                    }

                    '=' in trimmed -> {
                        val name = trimmed.substringBefore('=').trim()
                        val value = trimmed.substringAfter('=').trim()
                        if (!name.matches(IDENTIFIER) || value.isEmpty()) {
                            unknown += trimmed
                        } else {
                            val values = splitAlternatives(value)
                            enumerated[name] = values
                            if (name !in knownValues) unknown += trimmed
                        }
                    }

                    else -> unknown += trimmed
                }
            }
        }

        val rawPartsOfSpeech = enumerated["pos"].orEmpty()
        val partsOfSpeech = rawPartsOfSpeech.mapNotNullTo(linkedSetOf()) { partOfSpeechByValue[it] }
        rawPartsOfSpeech.filterNot(partOfSpeechByValue::containsKey).forEach { unknown += "pos=$it" }
        val gender = when (enumerated["g"]?.singleOrNull()) {
            "m" -> Gender.MASCULINE
            "f" -> Gender.FEMININE
            else -> null
        }
        val stemClass = when {
            kind == 1 || "an" in enumerated["ps"].orEmpty() -> StemClass.ALTERNATE_AN
            kind == 0 || "oc" in enumerated["ps"].orEmpty() -> StemClass.ORDINARY
            else -> StemClass.UNSPECIFIED
        }
        val personValues = enumerated["p"]
        val person = when {
            personValues == null -> PersonFeature.Absent
            personValues == setOf("0") -> PersonFeature.None
            else -> PersonFeature.Values(personValues)
        }
        val adpositionValues = enumerated["adp"]
        val allowsAdposition = adpositionValues != setOf("0")
        val allowedAdpositions = adpositionValues
            ?.filterNotTo(linkedSetOf()) { it == "0" }
            ?.takeIf { it.isNotEmpty() }

        return NominalFeatures(
            partsOfSpeech = partsOfSpeech,
            gender = gender,
            stemClass = stemClass,
            human = flags.stateOf("h"),
            plural = flags.stateOf("pl"),
            definite = flags.stateOf("def"),
            accusative = flags.stateOf("acc"),
            genitive = flags.stateOf("gen"),
            distributive = flags.stateOf("dis"),
            collective = flags.stateOf("col"),
            person = person,
            allowsAdposition = allowsAdposition,
            allowedAdpositions = allowedAdpositions,
            allowsGenitive = flags["gen"] != FeatureState.NEGATIVE,
            allowsDistributive = flags["dis"] != FeatureState.NEGATIVE,
            allowsCollective = flags["col"] != FeatureState.NEGATIVE,
            flags = flags,
            enumerated = enumerated,
            unknownFeatures = unknown.distinct(),
            malformed = malformed,
        )
    }

    private fun Map<String, FeatureState>.stateOf(name: String): FeatureState =
        get(name) ?: FeatureState.ABSENT

    private data class FeatureBlock(val content: String?, val malformed: Boolean)

    private fun featureBlock(raw: String): FeatureBlock {
        val start = raw.indexOf('[')
        if (start < 0) return FeatureBlock(null, false)
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in start until raw.length) {
            val character = raw[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (character == '\\' && quote != null) {
                escaped = true
                continue
            }
            if (character == '\'' || character == '"') {
                if (quote == null) quote = character else if (quote == character) quote = null
                continue
            }
            if (quote != null) continue
            when (character) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        val trailing = raw.substring(index + 1).trim()
                        return FeatureBlock(raw.substring(start + 1, index), trailing.isNotEmpty())
                    }
                    if (depth < 0) return FeatureBlock(null, true)
                }
            }
        }
        return FeatureBlock(null, true)
    }

    private fun splitTopLevel(value: String): List<String> {
        val result = ArrayList<String>()
        var start = 0
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in value.indices) {
            val character = value[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (character == '\\' && quote != null) {
                escaped = true
                continue
            }
            if (character == '\'' || character == '"') {
                if (quote == null) quote = character else if (quote == character) quote = null
                continue
            }
            if (quote != null) continue
            when (character) {
                '[' -> depth++
                ']' -> depth--
                ',' -> if (depth == 0) {
                    result += value.substring(start, index)
                    start = index + 1
                }
            }
        }
        result += value.substring(start)
        return result
    }

    private fun splitAlternatives(value: String): Set<String> {
        if (value.startsWith('[') || value.startsWith('"') || value.startsWith('\'')) {
            return setOf(value)
        }
        return value.split('|').mapTo(linkedSetOf()) { it.trim() }
    }

    private val IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9_]*")
}
