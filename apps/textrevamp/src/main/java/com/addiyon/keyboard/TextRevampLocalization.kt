package com.addiyon.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ProvideTextRevampLocalization(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextRevampStrings provides EnglishTextRevampStrings,
        content = content
    )
}
