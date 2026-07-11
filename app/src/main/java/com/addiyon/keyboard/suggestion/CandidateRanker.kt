package com.addiyon.keyboard.suggestion

/**
 * Reorders a list of transliteration readings (see
 * [com.addiyon.keyboard.transliteration.Transliterator.candidates]) so an
 * exact dictionary match wins the commit slot over a structurally-greedier
 * reading that isn't a real word -- "fkr" -> ፍቅር (a word) beats the greedy
 * ፍክር (not a word).
 *
 * A STABLE PARTITION, nothing more: candidates that are exact dictionary
 * words come first (in the engine's own structural order), then everything
 * else (also in structural order). No frequency-based reordering -- among
 * multiple exact matches, or when nothing matches, the engine's greedy-first
 * order is deterministic and stays put; the Amharic dictionary's frequencies
 * are a heuristic, not real corpus data, so they aren't a reordering signal.
 *
 * Pure Kotlin, JVM-testable without a real dictionary -- [isWord] is any
 * predicate, so tests inject a fake.
 */
object CandidateRanker {
    fun rank(candidates: List<String>, isWord: (String) -> Boolean): List<String> {
        val exact = ArrayList<String>(candidates.size)
        val rest = ArrayList<String>(candidates.size)
        for (candidate in candidates) {
            if (isWord(candidate)) exact.add(candidate) else rest.add(candidate)
        }
        exact.addAll(rest)
        return exact
    }
}
