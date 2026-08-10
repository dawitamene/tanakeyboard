package com.addiyon.keyboard.language.english

import com.addiyon.keyboard.suggestion.NgramContext

internal val EnglishNgramContext = NgramContext(
    isWordChar = { it.isLetter() || it == '\'' || it == '’' },
    normalizeWord = { it.replace('’', '\'') }
)
