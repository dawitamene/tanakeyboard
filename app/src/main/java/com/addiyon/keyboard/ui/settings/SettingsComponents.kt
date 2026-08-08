package com.addiyon.keyboard.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.addiyon.keyboard.ui.design.AddiyonGroupSurface

/**
 * A rounded, filled container that groups related rows together -- the
 * white "card" look used across the settings screens. Shared so every
 * screen's groups match (radius, color) by construction.
 */
@Composable
internal fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AddiyonGroupSurface(modifier = modifier, content = content)
}
