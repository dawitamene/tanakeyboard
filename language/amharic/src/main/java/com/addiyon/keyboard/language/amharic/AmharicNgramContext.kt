package com.addiyon.keyboard.language.amharic

import com.addiyon.keyboard.suggestion.NgramContext
import java.text.Normalizer

private val GeminationMarks = Regex("[፝-፟]")

internal val AmharicNgramContext = NgramContext(
    isWordChar = {
        it in 'ሀ'..'ፚ' || it in '፝'..'፟' ||
            it in 'ᎀ'..'ᎏ' || it in 'ⶀ'..'ⷞ' || it in 'ꬁ'..'ꬮ'
    },
    normalizeWord = {
        GeminationMarks.replace(Normalizer.normalize(it, Normalizer.Form.NFC), "")
    }
)
