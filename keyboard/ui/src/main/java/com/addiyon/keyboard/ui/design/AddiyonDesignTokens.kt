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
    val loadingDot = 10.dp
    val compact = 40.dp
    val keyboardAction = 44.dp
    val minimumTouchTarget = 48.dp
    val formControl = 48.dp
    val appHeader = 64.dp
    val iconSmall = 16.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val iconHero = 44.dp
}

object AddiyonBorders {
    val selectedTone = 1.dp
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
    val ink = Color(0xFF23272F)
    val mutedInk = Color(0xFF5A6572)
    val paper = Color.White.mix(Color.Black, 0.06f)
    val paperTop = Color.White.mix(Color.Black, 0.03f)
    val surface = Color.White
    val surfaceVariant = Color.White.mix(Color.Black, 0.08f)
    val outline = primary.mix(Color.Black, 0.22f)
    val darkInk = Color(0xFFE5E7EB)
    val darkMutedInk = Color(0xFF9CA3AF)
    val darkSurface = primary.mix(Color.Black, 0.76f)
    val darkSurfaceVariant = primary.mix(Color.Black, 0.58f)
    val darkOutline = primary.mix(Color.White, 0.48f)
    val icon = Color(0xFF5E6B78)
    val iconMuted = Color(0xFF7A8693)
    val darkIcon = Color(0xFF9CA3AF)
    val darkIconMuted = Color(0xFF6B7280)
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
data class AddiyonAiToneGlowColors(
    val start: Color,
    val end: Color
)

@Immutable
data class AddiyonColors(
    val brandPrimary: Color,
    val onBrandPrimary: Color,
    val aiToneIcons: AddiyonAiToneColors,
    val aiToneGlow: AddiyonAiToneGlowColors,
    val aiCustomToneColors: Map<String, Color>,
    val resultSurface: Color,
    val onResultSurface: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val icon: Color,
    val iconMuted: Color,
    val cardBackground: Color = Color.White
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
    aiToneGlow = AddiyonAiToneGlowColors(
        start = Color(0xFFD05398),
        end = Color(0xFF5980F0)
    ),
    aiCustomToneColors = mapOf(
        "teal" to brand.primary,
        "indigo" to Color(0xFF3F51B5),
        "orange" to Color(0xFFEF6C00),
        "purple" to Color(0xFF6A1B9A),
        "green" to Color(0xFF2E7D32),
        "rose" to Color(0xFFC2185B),
        "blue" to Color(0xFF0277BD),
        "amber" to Color(0xFFF9A825),
        "cyan" to Color(0xFF00ACC1),
        "lime" to Color(0xFF7CB342),
        "pink" to Color(0xFFEC407A),
        "red" to Color(0xFFE53935),
        "yellow" to Color(0xFFFBC02D),
        "brown" to Color(0xFF6D4C41),
        "grey" to Color(0xFF546E7A),
        "deep_purple" to Color(0xFF512DA8)
    ),
    resultSurface = brand.surface,
    onResultSurface = brand.ink,
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC8E6C9),
    onSuccessContainer = Color(0xFF1B5E20),
    icon = brand.icon,
    iconMuted = brand.iconMuted,
    cardBackground = Color.White
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
    aiToneGlow = AddiyonAiToneGlowColors(
        start = Color(0xFFD05398),
        end = Color(0xFF5980F0)
    ),
    aiCustomToneColors = mapOf(
        "teal" to brand.primaryLight,
        "indigo" to Color(0xFF9FA8DA),
        "orange" to Color(0xFFFFB74D),
        "purple" to Color(0xFFCE93D8),
        "green" to Color(0xFF81C784),
        "rose" to Color(0xFFF48FB1),
        "blue" to Color(0xFF4FC3F7),
        "amber" to Color(0xFFFFD54F),
        "cyan" to Color(0xFF4DD0E1),
        "lime" to Color(0xFFAED581),
        "pink" to Color(0xFFF06292),
        "red" to Color(0xFFEF9A9A),
        "yellow" to Color(0xFFFFE082),
        "brown" to Color(0xFFBCAAA4),
        "grey" to Color(0xFFB0BEC5),
        "deep_purple" to Color(0xFFB39DDB)
    ),
    resultSurface = brand.darkSurface,
    onResultSurface = brand.darkInk,
    success = Color(0xFF81C784),
    onSuccess = Color(0xFF1B5E20),
    successContainer = Color(0xFF2E7D32),
    onSuccessContainer = Color(0xFFC8E6C9),
    icon = brand.darkIcon,
    iconMuted = brand.darkIconMuted,
    cardBackground = Color.White
)

val LocalAddiyonColors = staticCompositionLocalOf<AddiyonColors> {
    error("AddiyonColors are available only inside an Addiyon theme")
}

val MaterialTheme.addiyonColors: AddiyonColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAddiyonColors.current
