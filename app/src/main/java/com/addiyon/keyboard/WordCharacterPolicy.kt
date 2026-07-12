package com.addiyon.keyboard

internal fun isComposingWordCharacter(output: String): Boolean =
    output.isNotEmpty() && output.all { it.isLetter() || it == '\'' || it == '`' }
