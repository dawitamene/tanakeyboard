package com.addiyon.keyboard.suggestion

import com.addiyon.keyboard.transliteration.Transliterator

internal object AmharicCommitPolicy {
    fun resolve(raw: String, cachedWorkerCandidate: String?): String {
        if (raw.isEmpty()) return ""
        return cachedWorkerCandidate ?: Transliterator.transliterate(raw)
    }
}
