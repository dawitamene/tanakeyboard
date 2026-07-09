package com.addiyon.keyboard.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.addiyon.keyboard.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import kotlin.math.sin
import kotlin.random.Random

val PlaypenSansBrand = FontFamily(Font(R.font.playpen_sans_extrabold))

/** The keyboard's own typeface -- every key, suggestion, and toolbar label. */
val PoppinsFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

private val KeyboardTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = PoppinsFamily),
        displayMedium = base.displayMedium.copy(fontFamily = PoppinsFamily),
        displaySmall = base.displaySmall.copy(fontFamily = PoppinsFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = PoppinsFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = PoppinsFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = PoppinsFamily),
        titleLarge = base.titleLarge.copy(fontFamily = PoppinsFamily),
        titleMedium = base.titleMedium.copy(fontFamily = PoppinsFamily),
        titleSmall = base.titleSmall.copy(fontFamily = PoppinsFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = PoppinsFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = PoppinsFamily),
        bodySmall = base.bodySmall.copy(fontFamily = PoppinsFamily),
        labelLarge = base.labelLarge.copy(fontFamily = PoppinsFamily),
        labelMedium = base.labelMedium.copy(fontFamily = PoppinsFamily),
        labelSmall = base.labelSmall.copy(fontFamily = PoppinsFamily)
    )
}

/**
 * The full per-appearance color inputs for one palette. Every Material role
 * the keyboard reads is specified here, so a palette can range from a subtle
 * recolor (neutral keys, tinted special keys + accent) all the way to a
 * fully-colored keyboard (its own tray AND key background).
 *
 * Role mapping the key composables rely on:
 *   [tray]    -> background (keyboard backdrop / suggestion strip)
 *   [key]     -> surface (normal letter key)
 *   [special] -> surfaceVariant (shift/enter/space/toggles)
 *   [accent]  -> primary (active shift, highlights)
 *   [onText]  -> onSurface / onSurfaceVariant (key text + icons)
 *   [onAccent]-> onPrimary (content drawn on the accent)
 */
private data class PaletteColors(
    val tray: Color,
    val key: Color,
    val special: Color,
    val accent: Color,
    val onText: Color,
    val onAccent: Color
)

private fun schemeFrom(c: PaletteColors, isDark: Boolean): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        background = c.tray,
        surface = c.key,
        surfaceVariant = c.special,
        onSurface = c.onText,
        onSurfaceVariant = c.onText
    )
}

/** Grouping shown as a section on the Themes screen. */
enum class PaletteCategory(val displayName: String) {
    MINIMAL("Minimal"),
    PASTEL("Pastel"),
    BOLD("Bold"),
    NATURE("Nature"),
    VIBRANT("Vibrant"),
    DARK("Dark")
}

sealed interface KeyboardBackground {
    data object Solid : KeyboardBackground
    data object LakeRipple : KeyboardBackground
}

/**
 * A selectable keyboard color theme. Supplies a light and a dark
 * [ColorScheme] plus the tray color used to tint the system navigation-bar
 * strip beneath the keyboard (set by the service, outside Compose).
 *
 * MINIMAL palettes keep near-white / dark keys and follow the system
 * light/dark setting. PASTEL and BOLD palettes recolor the key background too
 * and look the same in light and dark (a chosen identity over appearance).
 *
 * [id] is the stable string persisted in preferences (never the ordinal).
 */
enum class KeyboardPalette(
    val id: String,
    val displayName: String,
    val category: PaletteCategory,
    private val light: PaletteColors,
    private val dark: PaletteColors,
    val backgroundEffect: KeyboardBackground = KeyboardBackground.Solid
) {
    // ---- MINIMAL: tinted tray/special + accent, neutral keys, light+dark ----
    CLASSIC(
        "CLASSIC", "Classic", PaletteCategory.MINIMAL,
        PaletteColors(Color(0xFFECEEF0), Color(0xFFFFFFFF), Color(0xFFD3D8DC), Color(0xFF1A73E8), Color(0xFF1C1C1E), Color(0xFFFFFFFF)),
        PaletteColors(Color(0xFF1E1F22), Color(0xFF3A3B3E), Color(0xFF232427), Color(0xFF8AB4F8), Color(0xFFF2F2F2), Color(0xFF0A1F33))
    ),
    GRAPHITE(
        "GRAPHITE", "Graphite", PaletteCategory.MINIMAL,
        PaletteColors(Color(0xFFECECEE), Color(0xFFFFFFFF), Color(0xFFD6D6DA), Color(0xFF5A5A5F), Color(0xFF1C1C1E), Color(0xFFFFFFFF)),
        PaletteColors(Color(0xFF1E1F22), Color(0xFF3A3B3E), Color(0xFF2A2A2E), Color(0xFFBFBFC6), Color(0xFFF2F2F2), Color(0xFF1A1A1D))
    ),
    OCEAN(
        "OCEAN", "Ocean", PaletteCategory.MINIMAL,
        PaletteColors(Color(0xFFE3F0F4), Color(0xFFFFFFFF), Color(0xFFC7DEE6), Color(0xFF0091A7), Color(0xFF10323B), Color(0xFFFFFFFF)),
        PaletteColors(Color(0xFF0E2A30), Color(0xFF16383F), Color(0xFF11363D), Color(0xFF4DD0E1), Color(0xFFEAF6F8), Color(0xFF06232A))
    ),
    ROSE(
        "ROSE", "Rose", PaletteCategory.MINIMAL,
        PaletteColors(Color(0xFFF6E9EC), Color(0xFFFFFFFF), Color(0xFFEBD0D7), Color(0xFFC2185B), Color(0xFF3A1C24), Color(0xFFFFFFFF)),
        PaletteColors(Color(0xFF2A1720), Color(0xFF3C2531), Color(0xFF3A2029), Color(0xFFF48FB1), Color(0xFFFBE4EC), Color(0xFF2A0E19))
    ),
    FOREST(
        "FOREST", "Forest", PaletteCategory.MINIMAL,
        PaletteColors(Color(0xFFE6F0E8), Color(0xFFFFFFFF), Color(0xFFCADFCE), Color(0xFF2E7D32), Color(0xFF14301B), Color(0xFFFFFFFF)),
        PaletteColors(Color(0xFF14251A), Color(0xFF1E3524), Color(0xFF1B3020), Color(0xFF81C784), Color(0xFFE6F3E8), Color(0xFF0B1F10))
    ),
    SAND(
        "SAND", "Sand", PaletteCategory.MINIMAL,
        PaletteColors(Color(0xFFF3ECE0), Color(0xFFFFFFFF), Color(0xFFE6DAC6), Color(0xFFB8863B), Color(0xFF3A2E1C), Color(0xFFFFFFFF)),
        PaletteColors(Color(0xFF26201A), Color(0xFF383026), Color(0xFF2C251C), Color(0xFFD9B071), Color(0xFFF3ECE0), Color(0xFF26201A))
    ),

    // ---- PASTEL: soft colored tray + keys, dark text, fixed light look ----
    BUBBLEGUM(
        "BUBBLEGUM", "Bubblegum", PaletteCategory.PASTEL,
        full(0xFFFFE4EF, 0xFFFFF2F7, 0xFFFBD0E2, 0xFFFF5C9E, 0xFF4A1F33, 0xFFFFFFFF)
    ),
    BUTTER(
        "BUTTER", "Butter", PaletteCategory.PASTEL,
        full(0xFFFFF6D9, 0xFFFFFBEC, 0xFFFBE9B0, 0xFFE0A400, 0xFF4A3B12, 0xFFFFFFFF)
    ),
    SKY(
        "SKY", "Sky", PaletteCategory.PASTEL,
        full(0xFFE1F0FF, 0xFFF2F8FF, 0xFFC6E1FB, 0xFF2E8BE6, 0xFF143352, 0xFFFFFFFF)
    ),
    SAGE(
        "SAGE", "Sage", PaletteCategory.PASTEL,
        full(0xFFE6F1E4, 0xFFF3F8F1, 0xFFCFE3CB, 0xFF5A9E52, 0xFF20351C, 0xFFFFFFFF)
    ),
    PEACH(
        "PEACH", "Peach", PaletteCategory.PASTEL,
        full(0xFFFFEBDD, 0xFFFFF5EE, 0xFFFBD5BE, 0xFFF0813C, 0xFF4A2A14, 0xFFFFFFFF)
    ),
    LAVENDER(
        "LAVENDER", "Lavender", PaletteCategory.PASTEL,
        full(0xFFEDE7FB, 0xFFF6F2FF, 0xFFD9CEF6, 0xFF8A6BE0, 0xFF2C2050, 0xFFFFFFFF)
    ),

    // ---- BOLD: dark colored tray + keys, light text, fixed dark look ----
    MIDNIGHT(
        "MIDNIGHT", "Midnight", PaletteCategory.BOLD,
        full(0xFF0B1020, 0xFF1A2238, 0xFF121A2E, 0xFF5B8CFF, 0xFFE6ECFF, 0xFF0B1020)
    ),
    GRAPE(
        "GRAPE", "Grape", PaletteCategory.BOLD,
        full(0xFF241633, 0xFF3A2352, 0xFF2E1B45, 0xFFCBA6FF, 0xFFF1E9FF, 0xFF241633)
    ),
    COFFEE(
        "COFFEE", "Coffee", PaletteCategory.BOLD,
        full(0xFF2A1E16, 0xFF3E2C20, 0xFF33241A, 0xFFE8B98A, 0xFFF3E7DD, 0xFF2A1E16)
    ),
    MINT(
        "MINT", "Mint", PaletteCategory.BOLD,
        full(0xFF0E2A22, 0xFF163C30, 0xFF113026, 0xFF6FE0B0, 0xFFE3FBF1, 0xFF0E2A22)
    ),
    CRIMSON(
        "CRIMSON", "Crimson", PaletteCategory.BOLD,
        full(0xFF2A0D12, 0xFF3E161D, 0xFF331117, 0xFFFF6B81, 0xFFFBE3E7, 0xFF2A0D12)
    ),
    SLATE(
        "SLATE", "Slate", PaletteCategory.BOLD,
        full(0xFF12161C, 0xFF232A33, 0xFF1A2027, 0xFF8FB0C9, 0xFFE7EEF5, 0xFF12161C)
    ),

    TANA(
        "TANA", "Tana", PaletteCategory.NATURE,
        PaletteColors(Color(0xFFDEEAF4), Color(0xFFF2F7FB), Color(0xFFC5DAE8), Color(0xFF1B6FB3), Color(0xFF0B1A28), Color(0xFFFFFFFF)),
        PaletteColors(Color(0xFF0B1722), Color(0xFF152636), Color(0xFF0E1E30), Color(0xFF389ED4), Color(0xFFDEE8F2), Color(0xFF0B1722)),
        backgroundEffect = KeyboardBackground.LakeRipple
    ),
    AURORA(
        "AURORA", "Aurora", PaletteCategory.NATURE,
        full(0xFFD8F0E8, 0xFFEDF8F2, 0xFFC0E5D5, 0xFF1A9E6E, 0xFF0A2E1E, 0xFFFFFFFF)
    ),
    DUSK(
        "DUSK", "Dusk", PaletteCategory.NATURE,
        full(0xFFF2E6DE, 0xFFFAF2EC, 0xFFE8D5C8, 0xFFD4784A, 0xFF2A1A0E, 0xFFFFFFFF)
    ),
    CORAL(
        "CORAL", "Coral", PaletteCategory.NATURE,
        full(0xFFFCE8E2, 0xFFFEF4F0, 0xFFF8D0C5, 0xFFE06050, 0xFF2A1A14, 0xFFFFFFFF)
    ),
    EMERALD(
        "EMERALD", "Emerald", PaletteCategory.NATURE,
        full(0xFF0E1E16, 0xFF182E24, 0xFF12251A, 0xFF50D898, 0xFFE0F5EA, 0xFF0E1E16)
    ),
    STORM(
        "STORM", "Storm", PaletteCategory.NATURE,
        full(0xFF181C24, 0xFF262A35, 0xFF1E222C, 0xFF8A9EC0, 0xFFE6EAF2, 0xFF181C24)
    ),

    NEON(
        "NEON", "Neon", PaletteCategory.VIBRANT,
        full(0xFFF2F0FF, 0xFFFAF8FF, 0xFFE2DCF8, 0xFFF03088, 0xFF180A2A, 0xFFFFFFFF)
    ),
    SUNSET(
        "SUNSET", "Sunset", PaletteCategory.VIBRANT,
        full(0xFFFFF0E5, 0xFFFFF8F2, 0xFFFDDFCF, 0xFFF06830, 0xFF2A1400, 0xFFFFFFFF)
    ),
    ELECTRIC(
        "ELECTRIC", "Electric", PaletteCategory.VIBRANT,
        full(0xFFEDF0FF, 0xFFF6F8FF, 0xFFD5DDFA, 0xFF6840FF, 0xFF0E082A, 0xFFFFFFFF)
    ),
    BERRY(
        "BERRY", "Berry", PaletteCategory.VIBRANT,
        full(0xFF1A0E20, 0xFF2D1A35, 0xFF22132B, 0xFFE048A8, 0xFFF5E5F0, 0xFF1A0E20)
    ),
    TANGERINE(
        "TANGERINE", "Tangerine", PaletteCategory.VIBRANT,
        full(0xFFFFF2E5, 0xFFFFFAF3, 0xFFFFE5D0, 0xFFFF8040, 0xFF2A1400, 0xFFFFFFFF)
    ),
    AQUA(
        "AQUA", "Aqua", PaletteCategory.VIBRANT,
        full(0xFFDEFAF2, 0xFFEEFFFA, 0xFFC0F0E3, 0xFF00C0A5, 0xFF00281E, 0xFFFFFFFF)
    ),

    CHARCOAL(
        "CHARCOAL", "Charcoal", PaletteCategory.DARK,
        full(0xFF1C1C20, 0xFF2A2A30, 0xFF222228, 0xFF9EA0B2, 0xFFE8E8EE, 0xFF1C1C20)
    ),
    PEARL(
        "PEARL", "Pearl", PaletteCategory.DARK,
        full(0xFF222018, 0xFF333028, 0xFF282520, 0xFFC8B898, 0xFFF0EBE0, 0xFF222018)
    ),
    STEEL(
        "STEEL", "Steel", PaletteCategory.DARK,
        full(0xFF1A1E26, 0xFF282C38, 0xFF20242E, 0xFF8298B8, 0xFFE4EAF2, 0xFF1A1E26)
    ),
    ASH(
        "ASH", "Ash", PaletteCategory.DARK,
        full(0xFF242428, 0xFF343438, 0xFF2A2A30, 0xFFA6A6B6, 0xFFECECF0, 0xFF242428)
    ),
    SHADOW(
        "SHADOW", "Shadow", PaletteCategory.DARK,
        full(0xFF101014, 0xFF1C1C20, 0xFF141418, 0xFF888898, 0xFFD8D8E0, 0xFF101014)
    );

    constructor(id: String, displayName: String, category: PaletteCategory, both: PaletteColors, backgroundEffect: KeyboardBackground = KeyboardBackground.Solid) :
        this(id, displayName, category, both, both, backgroundEffect)

    private val lightScheme: ColorScheme = schemeFrom(light, isDark = false)
    private val darkScheme: ColorScheme = schemeFrom(dark, isDark = true)

    fun scheme(isDark: Boolean): ColorScheme = if (isDark) darkScheme else lightScheme
    fun tray(isDark: Boolean): Color = if (isDark) dark.tray else light.tray

    companion object {
        /** Parse a persisted [id], falling back to [CLASSIC] on anything unknown. */
        fun fromId(id: String?): KeyboardPalette =
            entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** Builds a [PaletteColors] for a full (colored-key) palette from ARGB longs. */
private fun full(
    tray: Long, key: Long, special: Long, accent: Long, onText: Long, onAccent: Long
): PaletteColors =
    PaletteColors(Color(tray), Color(key), Color(special), Color(accent), Color(onText), Color(onAccent))

// ---------------------------------------------------------------------------
// Addiyon brand palette (the app's own settings/onboarding UI, NOT the keyboard).
//
// Source colors from branding ("Stencil Solid" — see new_design/exports):
//   Vermillion   #EE4D2D  primary
//   Vermillion dark #C63A1E  pressed / secondary
//   Warm white   #FFF6F0  on-primary
//   Ink          #17150F  text
//   Stone        #8A8578  secondary text
//   Sand         #E4DFD3  divider / outline
//   Paper        #F4F1EA  surface / background
// The keyboard palettes above are user-selectable and deliberately untouched;
// this is the fixed brand identity for the app chrome (buttons, fields, cards).
// The "Tana" keyboard palette (Lake Tana / LakeRipple background, above) is a
// nature-themed color option users can pick, unrelated to this app-brand
// identity -- it keeps its name.
// ---------------------------------------------------------------------------

/** Vermillion tint highlight — the English-mode cue / accent chip color. Same in both modes. */
val AddiyonSand: Color = Color(0xFFF0B49E)

private val AddiyonLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFEE4D2D),            // Vermillion
    onPrimary = Color(0xFFFFF6F0),          // Warm white
    primaryContainer = Color(0xFFFBDDD3),   // Vermillion tint
    onPrimaryContainer = Color(0xFFC63A1E),
    secondary = Color(0xFFC63A1E),          // Vermillion dark
    onSecondary = Color(0xFFFFF6F0),
    secondaryContainer = Color(0xFFFBDDD3),
    onSecondaryContainer = Color(0xFF7A2312),
    tertiary = Color(0xFF8A8578),           // Stone
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4DFD3),
    onTertiaryContainer = Color(0xFF2A2620),
    background = Color.Transparent,
    onBackground = Color(0xFF17150F),       // Ink
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17150F),          // Ink
    surfaceVariant = Color(0xFFE4DFD3),     // Sand
    onSurfaceVariant = Color(0xFF57534A),
    outline = Color(0xFF8A8578),            // Stone
    outlineVariant = Color(0xFFE4DFD3)      // Sand
)

private val AddiyonDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFF8464),            // lightened vermillion for contrast on dark
    onPrimary = Color(0xFF3A1408),
    primaryContainer = Color(0xFFC63A1E),   // Vermillion dark
    onPrimaryContainer = Color(0xFFFBDDD3),
    secondary = Color(0xFFF0A084),
    onSecondary = Color(0xFF3A1408),
    secondaryContainer = Color(0xFF7A2312),
    onSecondaryContainer = Color(0xFFFBDDD3),
    tertiary = Color(0xFFB8B2A2),           // lightened Stone
    onTertiary = Color(0xFF201E18),
    tertiaryContainer = Color(0xFF43413A),
    onTertiaryContainer = Color(0xFFE4DFD3),
    background = Color.Transparent,
    onBackground = Color(0xFFF4F1EA),       // Paper
    surface = Color(0xFF2A2620),            // Charcoal
    onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF43413A),
    onSurfaceVariant = Color(0xFFCAC5B8),
    outline = Color(0xFF8A8578),            // Stone
    outlineVariant = Color(0xFF43413A)
)

@Composable
private fun PaperBackground(isDark: Boolean) {
    val topColor = if (isDark) Color(0xFF201E18) else Color(0xFFFAF8F4)
    val deepColor = if (isDark) Color(0xFF17150F) else Color(0xFFF4F1EA)
    val wave = if (isDark) Color(0xFFC63A1E).copy(alpha = 0.10f) else Color(0xFFEE4D2D).copy(alpha = 0.06f)

    val seeds = remember { List(2) { Random(it.hashCode()).nextFloat() } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(Brush.verticalGradient(listOf(topColor, deepColor)))

        val path = Path().apply {
            moveTo(0f, h * 0.85f)
            var x = 0f
            while (x <= w) {
                val dx = x / w
                val y = h * 0.85f + sin(dx * 6.28f * 1.5f + seeds[0] * 6.28f) * h * 0.02f
                    + sin(dx * 6.28f * 3.5f + seeds[1] * 6.28f) * h * 0.012f
                lineTo(x, y)
                x += w / 100f
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path, wave)
    }
}

@Composable
private fun LakeRippleBackground(modifier: Modifier, isDark: Boolean) {
    val topColor = if (isDark) Color(0xFF0E2A40) else Color(0xFFE1F0F8)
    val deepColor = if (isDark) Color(0xFF091C2E) else Color(0xFFB8D8EB)
    val wave = if (isDark) Color(0xFF254D68).copy(alpha = 0.40f) else Color(0xFF8EBFDA).copy(alpha = 0.45f)
    val wave2 = if (isDark) Color(0xFF1A3D55).copy(alpha = 0.25f) else Color(0xFFA8CFE5).copy(alpha = 0.25f)

    val seeds = remember { List(3) { Random(it.hashCode()).nextFloat() } }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(Brush.verticalGradient(listOf(topColor, deepColor)))

        val path1 = Path().apply {
            moveTo(0f, h * 0.70f)
            var x = 0f
            while (x <= w) {
                val dx = x / w
                val y = h * 0.70f + sin(dx * 6.28f * 1.2f + seeds[0] * 6.28f) * h * 0.055f
                    + sin(dx * 6.28f * 3.0f + seeds[1] * 6.28f) * h * 0.03f
                lineTo(x, y)
                x += w / 100f
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path1, wave)

        val path2 = Path().apply {
            moveTo(0f, h * 0.76f)
            var x = 0f
            while (x <= w) {
                val dx = x / w
                val y = h * 0.76f + sin(dx * 6.28f * 2.2f + seeds[2] * 6.28f) * h * 0.035f
                    + sin(dx * 6.28f * 4.5f + seeds[0] * 6.28f) * h * 0.016f
                lineTo(x, y)
                x += w / 100f
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path2, wave2)
    }
}

/**
 * The brand theme for the app's own UI (settings, onboarding, home, manual).
 * Uses the fixed Addiyon palette and follows the system light/dark setting.
 * Distinct from [CustomKeyboardTheme], which themes only the keyboard surface
 * from a user-selected [KeyboardPalette].
 */
@Composable
fun AddiyonBrandTheme(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDarkTheme) AddiyonDarkScheme else AddiyonLightScheme,
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                PaperBackground(isDark = isDarkTheme)
                content()
            }
        }
    )
}

/**
 * Wraps content in a MaterialTheme using the selected [palette] resolved to
 * the given appearance ([isDarkTheme]).
 *
 * IMPORTANT: this does NOT use isSystemInDarkTheme() to decide the scheme.
 * InputMethodService runs in its own window, separate from any Activity,
 * and Compose's isSystemInDarkTheme() reads LocalConfiguration, which
 * isn't reliably updated for an IME window when the system theme changes.
 * Instead, the caller (AddiyonKeyboardService) tracks dark mode itself via
 * onConfigurationChanged and passes the current value in explicitly, so
 * this composable just reacts to whatever state it's given.
 */
@Composable
fun CustomKeyboardTheme(
    isDarkTheme: Boolean,
    palette: KeyboardPalette = KeyboardPalette.CLASSIC,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = palette.scheme(isDarkTheme),
        typography = KeyboardTypography,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (palette.backgroundEffect is KeyboardBackground.LakeRipple) {
                    LakeRippleBackground(Modifier.matchParentSize(), isDarkTheme)
                }
                content()
            }
        }
    )
}
