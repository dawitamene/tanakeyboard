package com.addiyon.tanakeyboard.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    BOLD("Bold")
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
    private val dark: PaletteColors
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
    );

    // Secondary ctor for full palettes: one PaletteColors used for both modes.
    constructor(id: String, displayName: String, category: PaletteCategory, both: PaletteColors) :
        this(id, displayName, category, both, both)

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
// Tana brand palette (the app's own settings/onboarding UI, NOT the keyboard).
//
// Source colors from branding:
//   Deep Tana  #0E3A63  primary
//   Lake Blue  #1B6FB3  secondary
//   Shore Aqua #38A9D4  accent
//   Sand       #E9B872  highlight / English-mode cue  -> exposed as [TanaSand]
//   Mist       #EEF3F7  surface
//   Ink        #0B1722  text
// The keyboard palettes above are user-selectable and deliberately untouched;
// this is the fixed brand identity for the app chrome (buttons, fields, cards).
// ---------------------------------------------------------------------------

/** Sand highlight — the English-mode cue / accent chip color. Same in both modes. */
val TanaSand: Color = Color(0xFFE9B872)

private val TanaLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF0E3A63),            // Deep Tana
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E6F6),
    onPrimaryContainer = Color(0xFF082138),
    secondary = Color(0xFF1B6FB3),          // Lake Blue
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE5F6),
    onSecondaryContainer = Color(0xFF0A2C48),
    tertiary = Color(0xFF38A9D4),           // Shore Aqua
    onTertiary = Color(0xFF042836),
    tertiaryContainer = Color(0xFFC9ECF7),
    onTertiaryContainer = Color(0xFF042836),
    background = Color(0xFFEEF3F7),         // Mist
    onBackground = Color(0xFF0B1722),       // Ink
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1722),          // Ink
    surfaceVariant = Color(0xFFDCE6EF),
    onSurfaceVariant = Color(0xFF43525E),
    outline = Color(0xFF74828E),
    outlineVariant = Color(0xFFC2CDD6)
)

private val TanaDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF7FC1EA),            // lightened Lake Blue for contrast on dark
    onPrimary = Color(0xFF052440),
    primaryContainer = Color(0xFF0E3A63),   // Deep Tana
    onPrimaryContainer = Color(0xFFD4E6F6),
    secondary = Color(0xFF9AD0EC),
    onSecondary = Color(0xFF05283F),
    secondaryContainer = Color(0xFF1B6FB3), // Lake Blue
    onSecondaryContainer = Color(0xFFDCEEF9),
    tertiary = Color(0xFF6FCDEC),           // brightened Shore Aqua
    onTertiary = Color(0xFF03222F),
    tertiaryContainer = Color(0xFF1B6E8C),
    onTertiaryContainer = Color(0xFFC9ECF7),
    background = Color(0xFF0B1722),         // Ink
    onBackground = Color(0xFFE6EEF4),
    surface = Color(0xFF12222E),
    onSurface = Color(0xFFE6EEF4),
    surfaceVariant = Color(0xFF2A3A47),
    onSurfaceVariant = Color(0xFFB9C7D1),
    outline = Color(0xFF80909C),
    outlineVariant = Color(0xFF3C4C58)
)

/**
 * The brand theme for the app's own UI (settings, onboarding, home, manual).
 * Uses the fixed Tana palette and follows the system light/dark setting.
 * Distinct from [CustomKeyboardTheme], which themes only the keyboard surface
 * from a user-selected [KeyboardPalette].
 */
@Composable
fun TanaBrandTheme(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDarkTheme) TanaDarkScheme else TanaLightScheme,
        content = content
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
 * Instead, the caller (TanaKeyboardService) tracks dark mode itself via
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
        content = content
    )
}
