package com.addiyon.buildlogic.dictionary

internal object NominalFeatureEncoding {
    const val PRODUCTIVE = 1L shl 0
    private const val POS_NOUN = 1L shl 1
    private const val POS_ADJECTIVE = 1L shl 2
    private const val GENDER_FEMININE = 1L shl 3
    private const val GENDER_MASCULINE = 1L shl 4
    private const val ORDINARY_PLURAL = 1L shl 5
    private const val DEFINITE = 1L shl 6
    private const val POSSESSIVE = 1L shl 7
    private const val ADPOSITION = 1L shl 8
    private const val GENITIVE = 1L shl 9
    private const val DISTRIBUTIVE = 1L shl 10
    private const val COLLECTIVE = 1L shl 11
    private const val INITIAL_DELETION = 1L shl 12
    private const val HUMAN_SUFFIX = 1L shl 13
    private const val PROPER_PERSON = 1L shl 14
    private const val PROPER_PLACE = 1L shl 15
    private const val HUMAN_BLOCKED = 1L shl 26

    private val adpositions = listOf("የ", "ለ", "በ", "ከ", "እንደ", "ወደ", "እስከ", "ስለ", "በስተ", "ያለ")

    data class Encoded(val bits: Long, val stemClass: Int)

    fun encode(kind: Int, raw: String): Encoded {
        if (kind !in 0..3) return Encoded(0L, 0)
        val tokens = featureTokens(raw)
        val flags = tokens.filter { it.length > 1 && (it.first() == '+' || it.first() == '-') }
            .associate { it.drop(1) to it.first() }
        val values = tokens.filter { '=' in it }.associate { token ->
            token.substringBefore('=').trim() to token.substringAfter('=').trim().split('|').toSet()
        }
        val partsOfSpeech = values["pos"].orEmpty()
        val properPerson = kind == 2
        val properPlace = kind == 3
        val person = values["p"]
        val sourceIsBase = flags["def"] != '+' && flags["acc"] != '+' &&
            (person == null || person == setOf("0"))
        val productive = properPerson || properPlace ||
            (kind in 0..1 && partsOfSpeech.any { it == "N" || it == "ADJ" } && sourceIsBase)
        val stemClass = when {
            kind == 1 || "an" in values["ps"].orEmpty() -> 2
            kind == 0 || "oc" in values["ps"].orEmpty() -> 1
            else -> 0
        }
        if (!productive) return Encoded(0L, stemClass)

        var bits = PRODUCTIVE
        if ("N" in partsOfSpeech) bits = bits or POS_NOUN
        if ("ADJ" in partsOfSpeech) bits = bits or POS_ADJECTIVE
        if (values["g"] == setOf("f")) bits = bits or GENDER_FEMININE
        if (values["g"] == setOf("m")) bits = bits or GENDER_MASCULINE
        if (properPerson) bits = bits or PROPER_PERSON
        if (properPlace) bits = bits or PROPER_PLACE
        if (kind in 0..1 && flags["pl"] != '-') bits = bits or ORDINARY_PLURAL
        if (kind in 0..1 && flags["def"] != '-') bits = bits or DEFINITE
        if (kind in 0..1 && person != setOf("0")) bits = bits or POSSESSIVE

        val adpositionValues = values["adp"]
        if (adpositionValues != setOf("0")) {
            bits = bits or ADPOSITION
            val allowed = adpositionValues?.minus("0") ?: adpositions.toSet()
            allowed.forEach { adposition ->
                val index = adpositions.indexOf(adposition)
                if (index >= 0) bits = bits or (1L shl (16 + index))
            }
        }
        if (flags["gen"] != '-') bits = bits or GENITIVE
        if (kind in 0..1 && flags["dis"] != '-') bits = bits or DISTRIBUTIVE
        if ((kind in 0..1 || properPerson) && flags["col"] != '-') bits = bits or COLLECTIVE
        if (flags["delinit"] != '-') bits = bits or INITIAL_DELETION
        if (flags["h"] == '-') bits = bits or HUMAN_BLOCKED
        if (kind == 0 && "N" in partsOfSpeech && flags["h"] != '-') bits = bits or HUMAN_SUFFIX
        return Encoded(bits, stemClass)
    }

    private fun featureTokens(raw: String): List<String> {
        val start = raw.indexOf('[')
        if (start < 0) return emptyList()
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
                    if (depth == 0) return splitTopLevel(raw.substring(start + 1, index))
                }
            }
        }
        return emptyList()
    }

    private fun splitTopLevel(value: String): List<String> {
        val result = ArrayList<String>()
        var start = 0
        var depth = 0
        var quote: Char? = null
        for (index in value.indices) {
            val character = value[index]
            if (character == '\'' || character == '"') {
                if (quote == null) quote = character else if (quote == character) quote = null
                continue
            }
            if (quote != null) continue
            when (character) {
                '[' -> depth++
                ']' -> depth--
                ',' -> if (depth == 0) {
                    result += value.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        result += value.substring(start).trim()
        return result.filter(String::isNotEmpty)
    }
}
