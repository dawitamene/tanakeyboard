package com.addiyon.buildlogic.dictionary

internal object EthiopicNormalizer {
    private val fold: Map<Char, Char> = buildMap {
        fun series(variants: String, canonical: String) {
            require(variants.length == canonical.length)
            for (index in variants.indices) put(variants[index], canonical[index])
        }
        series("ሐሑሒሓሔሕሖ", "ሀሁሂሀሄህሆ")
        series("ኀኁኂኃኄኅኆ", "ሀሁሂሀሄህሆ")
        put('ሃ', 'ሀ')
        put('ሗ', 'ኋ')
        series("ሠሡሢሣሤሥሦሧ", "ሰሱሲሳሴስሶሷ")
        series("ዐዑዒዓዔዕዖ", "አኡኢአኤእኦ")
        put('ኣ', 'አ')
        series("ፀፁፂፃፄፅፆ", "ጸጹጺጻጼጽጾ")
    }

    fun normalize(value: String): String = buildString(value.length) {
        for (character in value) append(fold[character] ?: character)
    }
}
