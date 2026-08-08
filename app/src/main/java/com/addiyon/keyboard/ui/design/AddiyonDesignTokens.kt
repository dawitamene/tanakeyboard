package com.addiyon.keyboard.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Immutable

object AddiyonSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object AddiyonRadii {
    val small = 8.dp
    val medium = 12.dp
    val card = 16.dp
    val large = 20.dp
    val group = 28.dp
    val pill = 50.dp
}

object AddiyonSizes {
    val compact = 40.dp
    val keyboardAction = 44.dp
    val minimumTouchTarget = 48.dp
    val appHeader = 64.dp
    val iconSmall = 16.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val iconHero = 44.dp
}

object AddiyonElevation {
    val none = 0.dp
    val low = 1.dp
    val raised = 3.dp
    val overlay = 8.dp
}

object AddiyonMotion {
    const val fast = 150
    const val standard = 250
    const val emphasis = 400
    const val gentle = 500
}

@Immutable
data class AddiyonColors(
    val brandPrimary: Color,
    val onBrandPrimary: Color,
    val aiResultSurface: Color,
    val onAiResultSurface: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color
)

val AddiyonLightColors = AddiyonColors(
    brandPrimary = Color(0xFFEE4D2D),
    onBrandPrimary = Color(0xFFFFF6F0),
    aiResultSurface = Color(0xFFFFFFFF),
    onAiResultSurface = Color(0xFF17150F),
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC8E6C9),
    onSuccessContainer = Color(0xFF1B5E20)
)

val AddiyonDarkColors = AddiyonColors(
    brandPrimary = Color(0xFFFF8464),
    onBrandPrimary = Color(0xFF3A1408),
    aiResultSurface = Color(0xFFFFFFFF),
    onAiResultSurface = Color(0xFF17150F),
    success = Color(0xFF81C784),
    onSuccess = Color(0xFF0D2A12),
    successContainer = Color(0xFF1B5E20),
    onSuccessContainer = Color(0xFFC8E6C9)
)

val LocalAddiyonColors = staticCompositionLocalOf { AddiyonLightColors }

val MaterialTheme.addiyonColors: AddiyonColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAddiyonColors.current
