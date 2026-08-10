package com.addiyon.keyboard.composing

class TypingProfile(
    val isWordCharacter: (String) -> Boolean,
    val commitTransform: (String) -> String = { it },
    val lastUnitStart: (String) -> Int = { it.length - 1 },
    val transformStandalone: (String) -> String = { it },
    val wordEndingAtCursor: (before: String, after: String) -> String? = { _, _ -> null },
    val allowsAdoption: Boolean = true,
    val remembersRawLatin: Boolean = false
)
