package com.addiyon.keyboard.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The UI languages the app itself can be shown in (distinct from what the
 * KEYBOARD types). [code] is persisted; [label] is what the in-app language
 * toggle shows for each option, written in that language's own script.
 */
enum class AppLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"),
    AMHARIC("am", "አማርኛ")
}

/**
 * Every user-visible string in the app's own UI (NOT the keyboard glyphs),
 * one field per string. There's one instance per [AppLanguage]; the active one
 * is provided through [LocalAppStrings] and swapped when the user flips the
 * language toggle. Parameterized strings are `%s`/`%d` format templates so the
 * placeholder can sit wherever each language's grammar needs it.
 */
data class AppStrings(
    // Common
    val back: String,
    // Settings home
    val themes: String,
    val typingGuide: String,
    val preferences: String,
    val testKeyboard: String,
    val shareApp: String,
    val rateApp: String,
    val feedback: String,
    val about: String,
    // Feedback
    val sendFeedback: String,
    val email: String,
    val telegram: String,
    val feedbackEmailSubject: String,
    val shareTextFormat: String,
    val shareChooserTitle: String,
    // Typing guide
    val searchPlaceholder: String,
    // Preferences
    val vibrateOnKeypress: String,
    val soundOnKeypress: String,
    val numberRow: String,
    // Test keyboard
    val testPlaceholder: String,
    // About
    val versionFormat: String,
    val aboutDescription: String,
    val aboutPrivacy: String,
    val madeBy: String,
    // Onboarding
    val activateTitle: String,
    val activateDescription: String,
    val openSettings: String,
    val activateFootnote: String,
    val enableTitle: String,
    val enableDescription: String,
    val switchKeyboard: String,
    val stepFormat: String,
    val allSet: String,
    val allSetSubtitle: String
)

val EnglishStrings = AppStrings(
    back = "Back",
    themes = "Themes",
    typingGuide = "Typing Guide",
    preferences = "Preferences",
    testKeyboard = "Test Keyboard",
    shareApp = "Share Addiyon Keyboard",
    rateApp = "Rate Addiyon Keyboard",
    feedback = "Feedback",
    about = "About",
    sendFeedback = "Send feedback",
    email = "Email",
    telegram = "Telegram",
    feedbackEmailSubject = "Addiyon Keyboard feedback",
    shareTextFormat = "Type Amharic easily with Addiyon Keyboard: %s",
    shareChooserTitle = "Share Addiyon Keyboard",
    searchPlaceholder = "Search: he, sh, ላ ...",
    vibrateOnKeypress = "Vibrate on keypress",
    soundOnKeypress = "Sound on keypress",
    numberRow = "Number row",
    testPlaceholder = "Type \"selam\" → ሰላም",
    versionFormat = "Version %s",
    aboutDescription = "Type Amharic (Ge'ez) using simple Latin transliteration " +
        "— for example, \"selam\" becomes ሰላም.",
    aboutPrivacy = "Your privacy: Addiyon Keyboard never collects any data. " +
        "Everything you type stays on your device.",
    madeBy = "Made by Addiyon",
    activateTitle = "Activate Addiyon Keyboard",
    activateDescription = "Turn Addiyon Keyboard on in your device's input-method " +
        "settings. It only takes a moment.",
    openSettings = "Open Settings",
    activateFootnote = "Addiyon Keyboard never collects any data from you. " +
        "Everything you type stays on your device.",
    enableTitle = "Enable Addiyon Keyboard",
    enableDescription = "Pick Addiyon Keyboard from the keyboard switcher to make " +
        "it your active keyboard.",
    switchKeyboard = "Switch Keyboard",
    stepFormat = "Step %d",
    allSet = "All set!",
    allSetSubtitle = "Addiyon Keyboard is ready to use."
)

val AmharicStrings = AppStrings(
    back = "ተመለስ",
    themes = "ገጽታዎች",
    typingGuide = "የአፃፃፍ መመሪያ",
    preferences = "ምርጫዎች",
    testKeyboard = "ኪቦርዱን ሞክር",
    shareApp = "አዲዮን ኪቦርድን አጋራ",
    rateApp = "አዲዮን ኪቦርድን ደረጃ ስጥ",
    feedback = "አስተያየት",
    about = "ስለ መተግበሪያው",
    sendFeedback = "አስተያየት ላክ",
    email = "ኢሜይል",
    telegram = "ቴሌግራም",
    feedbackEmailSubject = "የአዲዮን ኪቦርድ አስተያየት",
    shareTextFormat = "አማርኛን በቀላሉ በአዲዮን ኪቦርድ ይፃፉ %s",
    shareChooserTitle = "አዲዮን ኪቦርድን አጋራ",
    searchPlaceholder = "ፈልግ፡ he, sh, ላ ...",
    vibrateOnKeypress = "ቁልፍ ሲነካ ንዝረት",
    soundOnKeypress = "ቁልፍ ሲነካ ድምፅ",
    numberRow = "የቁጥር ረድፍ",
    testPlaceholder = "\"selam\" ብለው ይፃፉ → ሰላም",
    versionFormat = "ስሪት %s",
    aboutDescription = "የእንግሊዝኛ ፊደላትን በመጠቀም በቀላሉ አማርኛ ይፃፉ — " +
        "ለምሳሌ \"selam\" ወደ ሰላም ይቀየራል።",
    aboutPrivacy = "የእርስዎ ግላዊነት፡ አዲዮን ኪቦርድ ምንም መረጃ አይሰበስብም። " +
        "የሚፅፉት ሁሉ በስልክዎ ላይ ይቆያል።",
    madeBy = "በአዲዮን የተሰራ",
    activateTitle = "አዲዮን ኪቦርድን አስጀምር",
    activateDescription = "አዲዮን ኪቦርድን በስልክዎ የፅህፈት ዘዴ ቅንብሮች ውስጥ ያብሩት። " +
        "ጥቂት ጊዜ ብቻ ነው የሚወስደው።",
    openSettings = "ቅንብሮችን ክፈት",
    activateFootnote = "አዲዮን ኪቦርድ ከእርስዎ ምንም መረጃ አይሰበስብም። " +
        "የሚፅፉት ሁሉ በስልክዎ ላይ ይቆያል።",
    enableTitle = "አዲዮን ኪቦርድን ምረጥ",
    enableDescription = "አዲዮን ኪቦርድን ከኪቦርድ መቀየሪያው ይምረጡት።",
    switchKeyboard = "ኪቦርድ ቀይር",
    stepFormat = "ደረጃ %d",
    allSet = "ሁሉም ተዘጋጅቷል!",
    allSetSubtitle = "አዲዮን ኪቦርድ ለመጠቀም ዝግጁ ነው።"
)

/**
 * Controls the active app language: read [current] to know which is selected,
 * call [set]/[toggle] to change it (also persisted via [LanguagePrefs]).
 * Provided alongside [LocalAppStrings] by [ProvideAppLocalization].
 */
interface AppLanguageController {
    val current: AppLanguage
    fun set(language: AppLanguage)
    fun toggle()
}

/** The active string table. Defaults to English until a provider overrides it. */
val LocalAppStrings = staticCompositionLocalOf { EnglishStrings }

/** The active language controller; must be supplied by [ProvideAppLocalization]. */
val LocalAppLanguage = staticCompositionLocalOf<AppLanguageController> {
    error("No AppLanguageController provided; wrap content in ProvideAppLocalization")
}
