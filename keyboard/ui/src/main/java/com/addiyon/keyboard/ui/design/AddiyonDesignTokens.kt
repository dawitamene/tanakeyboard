package com.addiyon.keyboard.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Immutable
import com.addiyon.keyboard.ui.shared.R

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
    val formControl = 56.dp
    val appHeader = 64.dp
    val iconSmall = 16.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val iconHero = 44.dp
}

object AddiyonBorders {
    val selectedTone = 3.dp
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
data class AddiyonBrand(val primary: Color) {
    val primaryDark = primary.mix(Color.Black, 0.28f)
    val primaryDarker = primary.mix(Color.Black, 0.56f)
    val primaryLight = primary.mix(Color.White, 0.38f)
    val primaryLighter = primary.mix(Color.White, 0.65f)
    val primaryContainerLight = primary.mix(Color.White, 0.84f)
    val primaryContainerDark = primary.mix(Color.Black, 0.25f)
    val onPrimary = Color.White
    val onPrimaryDark = Color.White
    val ink = primary.mix(Color.Black, 0.88f)
    val mutedInk = primary.mix(Color.Black, 0.58f)
    val paper = Color.White.mix(Color.Black, 0.04f)
    val paperTop = Color.White.mix(Color.Black, 0.02f)
    val surface = Color.White
    val surfaceVariant = Color.White.mix(Color.Black, 0.08f)
    val outline = primary.mix(Color.Black, 0.22f)
    val darkInk = primary.mix(Color.White, 0.92f)
    val darkMutedInk = primary.mix(Color.White, 0.72f)
    val darkSurface = primary.mix(Color.Black, 0.76f)
    val darkSurfaceVariant = primary.mix(Color.Black, 0.58f)
    val darkOutline = primary.mix(Color.White, 0.48f)
}

private fun Color.mix(target: Color, fraction: Float): Color = lerp(this, target, fraction)

@Composable
fun rememberAddiyonBrand(): AddiyonBrand {
    val primary = colorResource(R.color.addiyon_brand_primary)
    return remember(primary) { AddiyonBrand(primary) }
}

@Immutable
data class AddiyonAiToneColors(
    val humanize: Color,
    val professional: Color,
    val casual: Color,
    val formal: Color,
    val friendly: Color,
    val fixGrammar: Color,
    val shorten: Color,
    val summarize: Color
)

@Immutable
data class AddiyonColors(
    val brandPrimary: Color,
    val onBrandPrimary: Color,
    val aiToneIcons: AddiyonAiToneColors,
    val resultSurface: Color,
    val onResultSurface: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color
)

fun addiyonLightColors(brand: AddiyonBrand) = AddiyonColors(
    brandPrimary = brand.primary,
    onBrandPrimary = brand.onPrimary,
    aiToneIcons = AddiyonAiToneColors(
        humanize = brand.primary,
        professional = Color(0xFF3F51B5),
        casual = Color(0xFFEF6C00),
        formal = Color(0xFF6A1B9A),
        friendly = Color(0xFF2E7D32),
        fixGrammar = Color(0xFF7B1FA2),
        shorten = Color(0xFFD84315),
        summarize = Color(0xFF0277BD)
    ),
    resultSurface = brand.surface,
    onResultSurface = brand.ink,
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC8E6C9),
    onSuccessContainer = Color(0xFF1B5E20)
)

fun addiyonDarkColors(brand: AddiyonBrand) = AddiyonColors(
    brandPrimary = brand.primaryLight,
    onBrandPrimary = brand.onPrimaryDark,
    aiToneIcons = AddiyonAiToneColors(
        humanize = brand.primaryLight,
        professional = Color(0xFF9FA8DA),
        casual = Color(0xFFFFB74D),
        formal = Color(0xFFCE93D8),
        friendly = Color(0xFF81C784),
        fixGrammar = Color(0xFFB39DDB),
        shorten = Color(0xFFFF8A65),
        summarize = Color(0xFF4FC3F7)
    ),
    resultSurface = brand.surface,
    onResultSurface = brand.ink,
    success = Color(0xFF81C784),
    onSuccess = Color(0xFF0D2A12),
    successContainer = Color(0xFF1B5E20),
    onSuccessContainer = Color(0xFFC8E6C9)
)

val LocalAddiyonColors = staticCompositionLocalOf<AddiyonColors> {
    error("AddiyonColors are available only inside an Addiyon theme")
}

val MaterialTheme.addiyonColors: AddiyonColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAddiyonColors.current
