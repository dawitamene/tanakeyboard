package com.addiyon.keyboard.suggestion

data class Suggestion(val word: String, val frequency: Int)
data class FuzzyMatch(val word: String, val editDistance: Int, val frequency: Int)
fun interface SubstitutionCost {
    fun cost(a: Char, b: Char): Int
}
