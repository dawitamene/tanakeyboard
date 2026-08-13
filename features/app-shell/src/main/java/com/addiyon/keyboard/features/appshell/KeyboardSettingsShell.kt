package com.addiyon.keyboard.features.appshell

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.addiyon.keyboard.product.KeyboardProduct
import com.addiyon.keyboard.product.TYPING_GUIDE_FEATURE_ID
import com.addiyon.keyboard.ui.design.AddiyonContentSection
import com.addiyon.keyboard.ui.design.AddiyonRadii
import com.addiyon.keyboard.ui.design.AddiyonSpacing
import com.addiyon.keyboard.ui.settings.KeyboardPrefs

data class KeyboardSettingsMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val badge: String? = null
)

@Composable
fun KeyboardSettingsMenuScreen(
    header: @Composable () -> Unit,
    primaryItems: List<KeyboardSettingsMenuItem>,
    storeItems: List<KeyboardSettingsMenuItem>,
    supportItems: List<KeyboardSettingsMenuItem>,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize(), topBar = header) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AddiyonSpacing.md)
        ) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AddiyonSpacing.md)
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        SettingsGroup(primaryItems)
                    }
                    Column(Modifier.weight(1f)) {
                        SettingsGroup(storeItems)
                        Spacer(Modifier.height(AddiyonSpacing.md))
                        SettingsGroup(supportItems)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SettingsGroup(primaryItems)
                    Spacer(Modifier.height(AddiyonSpacing.xl))
                    SettingsGroup(storeItems)
                    Spacer(Modifier.height(AddiyonSpacing.xl))
                    SettingsGroup(supportItems)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(items: List<KeyboardSettingsMenuItem>) {
    AddiyonContentSection(modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick)
                    .padding(horizontal = AddiyonSpacing.md, vertical = AddiyonSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(AddiyonSpacing.lg))
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                item.badge?.let { badge ->
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(AddiyonRadii.pill))
                    ) { Text(badge) }
                }
            }
        }
    }
}

@Composable
private fun standardKeyboardAppShellConfig(
    product: KeyboardProduct,
    options: KeyboardStandardShellOptions
): KeyboardAppShellConfig {
    val context = LocalContext.current
    val copy = options.copy

    fun destination(id: String) = KeyboardShellMenuTarget.Destination(id)
    fun action(block: () -> Unit) = KeyboardShellMenuTarget.Action(block)

    val commonDestinations = listOf(
        KeyboardShellDestination(KeyboardShellDestinations.THEMES) { scope ->
            KeyboardThemePickerScreen(
                title = copy.themes,
                backContentDescription = copy.back,
                selectedPalette = KeyboardPrefs.palette(context),
                onPaletteSelected = {
                    KeyboardPrefs.setPalette(context, it)
                    if (scope.openedFromKeyboard) scope.finish()
                },
                onBack = scope.onBack
            )
        },
    ) + listOfNotNull(
        KeyboardShellDestination(KeyboardShellDestinations.GUIDE) { scope ->
            options.guideScreen?.invoke(scope.onBack) ?: KeyboardTextGuideScreen(
                    title = copy.typingGuide,
                    backContentDescription = copy.back,
                    sections = options.guideSections,
                    onBack = scope.onBack
                )
        }.takeIf { TYPING_GUIDE_FEATURE_ID in product.featureIds }
    ) + listOf(
        KeyboardShellDestination(KeyboardShellDestinations.PREFERENCES) { scope ->
            KeyboardStandardPreferencesScreen(
                copy = copy.preferencesCopy,
                onBack = scope.onBack,
                onOpenKeyboardHeight = {
                    scope.navigate(KeyboardShellDestinations.KEYBOARD_HEIGHT)
                },
                onSettingChanged = options.onSettingChanged
            )
        },
        KeyboardShellDestination(
            id = KeyboardShellDestinations.KEYBOARD_HEIGHT,
            parentId = KeyboardShellDestinations.PREFERENCES
        ) { scope ->
            KeyboardHeightScreen(
                copy = copy.heightCopy,
                onBack = scope.onBack,
                showLanguageSwitchKey = product.showsLanguageSwitchKey
            )
        },
        KeyboardShellDestination(KeyboardShellDestinations.TEST_KEYBOARD) { scope ->
            KeyboardTestScreen(
                title = copy.testKeyboard,
                backContentDescription = copy.back,
                placeholder = copy.testPlaceholder,
                onBack = scope.onBack
            )
        },
        KeyboardShellDestination(KeyboardShellDestinations.PERSONAL_DICTIONARY) { scope ->
            KeyboardPersonalDictionaryScreen(
                copy = copy.personalDictionaryCopy,
                onBack = scope.onBack
            )
        },
        KeyboardShellDestination(KeyboardShellDestinations.FEEDBACK) { scope ->
            KeyboardFeedbackScreen(
                copy = copy.feedbackCopy,
                onBack = scope.onBack,
                onPicked = {
                    if (scope.openedFromKeyboard) scope.finish()
                }
            )
        },
        KeyboardShellDestination(KeyboardShellDestinations.ABOUT) { scope ->
            val version = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull().orEmpty()
            }
            KeyboardAboutScreen(
                title = copy.about,
                backContentDescription = copy.back,
                productName = options.productName,
                versionText = copy.versionFormat.format(version),
                description = copy.aboutDescription,
                privacyPolicyLabel = copy.privacyPolicy,
                madeBy = copy.madeBy,
                onPrivacyPolicy = {
                    KeyboardExternalActions.openPrivacyPolicy(
                        context,
                        url = options.privacyPolicyUrl
                    )
                },
                onBack = scope.onBack,
                logo = options.logo,
                privacyPolicyTestTag = PRIVACY_POLICY_TEST_TAG
            )
        }
    )

    val commonPrimaryEntries = listOf(
        KeyboardShellMenuEntry(
            Icons.Default.Palette,
            copy.themes,
            KeyboardSettingsGroup.PRIMARY,
            destination(KeyboardShellDestinations.THEMES)
        ),
    ) + listOfNotNull(
        KeyboardShellMenuEntry(
            Icons.AutoMirrored.Filled.MenuBook,
            copy.typingGuide,
            KeyboardSettingsGroup.PRIMARY,
            destination(KeyboardShellDestinations.GUIDE)
        ).takeIf { TYPING_GUIDE_FEATURE_ID in product.featureIds }
    ) + listOf(
        KeyboardShellMenuEntry(
            Icons.Default.Tune,
            copy.preferences,
            KeyboardSettingsGroup.PRIMARY,
            destination(KeyboardShellDestinations.PREFERENCES)
        ),
        KeyboardShellMenuEntry(
            Icons.Default.Keyboard,
            copy.testKeyboard,
            KeyboardSettingsGroup.PRIMARY,
            destination(KeyboardShellDestinations.TEST_KEYBOARD)
        ),
        KeyboardShellMenuEntry(
            Icons.Default.Book,
            copy.personalDictionary,
            KeyboardSettingsGroup.PRIMARY,
            destination(KeyboardShellDestinations.PERSONAL_DICTIONARY)
        )
    )
    val menuEntries = commonPrimaryEntries + options.featurePrimaryEntries + listOf(
        KeyboardShellMenuEntry(
            Icons.Default.Share,
            copy.shareApp,
            KeyboardSettingsGroup.STORE,
            action {
                KeyboardExternalActions.shareApplication(
                    context = context,
                    shareText = copy.shareText,
                    chooserTitle = copy.shareChooserTitle
                )
            }
        ),
        KeyboardShellMenuEntry(
            Icons.Default.StarRate,
            copy.rateApp,
            KeyboardSettingsGroup.STORE,
            action { KeyboardExternalActions.rateApplication(context) }
        ),
        KeyboardShellMenuEntry(
            Icons.Default.Feedback,
            copy.feedback,
            KeyboardSettingsGroup.SUPPORT,
            destination(KeyboardShellDestinations.FEEDBACK)
        ),
        KeyboardShellMenuEntry(
            Icons.Default.Info,
            copy.about,
            KeyboardSettingsGroup.SUPPORT,
            destination(KeyboardShellDestinations.ABOUT)
        )
    )

    return KeyboardAppShellConfig(
        product = product,
        onboardingCopy = options.onboardingCopy,
        header = options.header,
        destinations = commonDestinations,
        menuEntries = menuEntries,
        onOpenInputSettings = { KeyboardExternalActions.openInputMethodSettings(context) },
        onShowInputMethodPicker = { KeyboardExternalActions.showInputMethodPicker(context) },
        tourPages = options.tourPages,
        isTourSeen = options.isTourSeen,
        markTourSeen = options.markTourSeen,
        settingsOverlay = {}
    )
}

private data class KeyboardStandardShellCopy(
    val back: String,
    val themes: String,
    val typingGuide: String,
    val preferences: String,
    val testKeyboard: String,
    val personalDictionary: String,
    val shareApp: String,
    val rateApp: String,
    val feedback: String,
    val about: String,
    val preferencesCopy: KeyboardPreferencesCopy,
    val heightCopy: KeyboardHeightCopy,
    val testPlaceholder: String,
    val personalDictionaryCopy: KeyboardPersonalDictionaryCopy,
    val feedbackCopy: KeyboardFeedbackCopy,
    val versionFormat: String,
    val aboutDescription: String,
    val privacyPolicy: String,
    val madeBy: String,
    val shareText: String,
    val shareChooserTitle: String
)

private data class KeyboardStandardShellOptions(
    val copy: KeyboardStandardShellCopy,
    val productName: String,
    val onboardingCopy: KeyboardOnboardingCopy,
    val header: @Composable () -> Unit,
    val guideSections: List<KeyboardGuideSection>,
    val guideScreen: (@Composable (() -> Unit) -> Unit)?,
    val logo: @Composable () -> Unit,
    val featurePrimaryEntries: List<KeyboardShellMenuEntry>,
    val tourPages: List<KeyboardTourPage>,
    val isTourSeen: () -> Boolean,
    val markTourSeen: () -> Unit,
    val onSettingChanged: (KeyboardPreferenceSetting, Boolean) -> Unit = { _, _ -> },
    val privacyPolicyUrl: String = KeyboardExternalActions.DEFAULT_PRIVACY_POLICY_URL
)

data class KeyboardAppShellCustomization(
    val featurePrimaryEntries: List<KeyboardShellMenuEntry> = emptyList(),
    val featureGuideSections: List<KeyboardGuideSection> = emptyList(),
    val guideScreen: (@Composable (() -> Unit) -> Unit)? = null,
    val featureTourPages: List<KeyboardTourPage> = emptyList(),
    val headerActions: @Composable RowScope.() -> Unit = {},
    val onSettingChanged: (KeyboardPreferenceSetting, Boolean) -> Unit = { _, _ -> },
    val privacyPolicyUrl: String = KeyboardExternalActions.DEFAULT_PRIVACY_POLICY_URL
)

private const val PRIVACY_POLICY_TEST_TAG = "about.privacyPolicy"

@Composable
fun packKeyboardAppShellConfig(
    product: KeyboardProduct,
    customization: KeyboardAppShellCustomization = KeyboardAppShellCustomization()
): KeyboardAppShellConfig {
    val context = LocalContext.current
    val productName = stringResource(R.string.keyboard_product_name)
    val storeLink = "https://play.google.com/store/apps/details?id=${context.packageName}"
    val back = stringResource(R.string.back)
    val preferences = stringResource(R.string.menu_preferences)
    val keyboardHeight = stringResource(R.string.preference_keyboard_height)
    val feedbackTitle = stringResource(R.string.send_feedback)
    val options = KeyboardStandardShellOptions(
        copy = KeyboardStandardShellCopy(
            back = back,
            themes = stringResource(R.string.menu_themes),
            typingGuide = stringResource(R.string.menu_typing_guide),
            preferences = preferences,
            testKeyboard = stringResource(R.string.menu_test_keyboard),
            personalDictionary = stringResource(R.string.menu_personal_dictionary),
            shareApp = stringResource(R.string.menu_share),
            rateApp = stringResource(R.string.menu_rate),
            feedback = stringResource(R.string.menu_feedback),
            about = stringResource(R.string.menu_about),
            preferencesCopy = KeyboardPreferencesCopy(
                title = preferences,
                back = back,
                keyboardHeight = keyboardHeight,
                vibration = stringResource(R.string.preference_vibration),
                sound = stringResource(R.string.preference_sound),
                numberRow = stringResource(R.string.preference_number_row)
            ),
            heightCopy = KeyboardHeightCopy(
                title = keyboardHeight,
                back = back,
                hint = stringResource(R.string.keyboard_height_hint),
                reset = stringResource(R.string.reset),
                done = stringResource(R.string.done),
                previewLanguageName = stringResource(R.string.preview_language_name)
            ),
            testPlaceholder = stringResource(R.string.test_keyboard_placeholder),
            personalDictionaryCopy = KeyboardPersonalDictionaryCopy(
                title = stringResource(R.string.personal_dictionary_title),
                back = back,
                empty = stringResource(R.string.personal_dictionary_empty),
                clearAll = stringResource(R.string.personal_dictionary_clear_all),
                delete = stringResource(R.string.delete),
                wordSingular = stringResource(R.string.word_singular),
                wordPlural = stringResource(R.string.word_plural)
            ),
            feedbackCopy = KeyboardFeedbackCopy(
                title = feedbackTitle,
                back = back,
                telegram = stringResource(R.string.telegram),
                email = stringResource(R.string.email),
                emailSubject = stringResource(R.string.feedback_subject_format, productName)
            ),
            versionFormat = stringResource(R.string.version_format),
            aboutDescription = stringResource(R.string.about_body),
            privacyPolicy = stringResource(R.string.privacy_policy),
            madeBy = stringResource(R.string.made_by_addiyon),
            shareText = stringResource(R.string.share_text_format, storeLink),
            shareChooserTitle = stringResource(R.string.share_chooser_title)
        ),
        productName = productName,
        onboardingCopy = KeyboardOnboardingCopy(
            activateTitle = stringResource(R.string.keyboard_activate_title),
            activateDescription = stringResource(R.string.keyboard_activate_description),
            openSettings = stringResource(R.string.open_keyboard_settings),
            activateFootnote = stringResource(R.string.keyboard_activate_footnote),
            enableTitle = stringResource(R.string.keyboard_enable_title),
            enableDescription = stringResource(R.string.keyboard_enable_description),
            switchKeyboard = stringResource(R.string.switch_keyboard),
            stepFormat = stringResource(R.string.keyboard_setup_step),
            allSet = stringResource(R.string.keyboard_all_set),
            allSetSubtitle = stringResource(R.string.keyboard_all_set_subtitle),
            tourSkip = stringResource(R.string.tour_skip),
            tourNext = stringResource(R.string.tour_next),
            tourStart = stringResource(R.string.tour_start)
        ),
        header = {
            KeyboardProductHeader(
                title = productName,
                trailingContent = customization.headerActions
            )
        },
        guideSections = customization.featureGuideSections + listOf(
            KeyboardGuideSection(body = stringResource(R.string.typing_guide_body)),
            KeyboardGuideSection(
                title = stringResource(R.string.tour_suggestions_title),
                body = stringResource(R.string.tour_suggestions_description)
            )
        ),
        guideScreen = customization.guideScreen,
        logo = {
            Image(
                painter = painterResource(R.drawable.ic_addiyon_app_shell),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
        },
        featurePrimaryEntries = customization.featurePrimaryEntries,
        tourPages = customization.featureTourPages + listOf(
            KeyboardTourPage(
                icon = Icons.Default.Translate,
                title = stringResource(R.string.tour_typing_title),
                description = stringResource(R.string.tour_typing_description),
                example = stringResource(R.string.tour_typing_example)
            ),
            KeyboardTourPage(
                icon = Icons.Default.Lightbulb,
                title = stringResource(R.string.tour_suggestions_title),
                description = stringResource(R.string.tour_suggestions_description)
            ),
            KeyboardTourPage(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.tour_personalize_title),
                description = stringResource(R.string.tour_personalize_description)
            )
        ),
        isTourSeen = { KeyboardPrefs.featureTourSeen(context) },
        markTourSeen = { KeyboardPrefs.setFeatureTourSeen(context) },
        onSettingChanged = customization.onSettingChanged,
        privacyPolicyUrl = customization.privacyPolicyUrl
    )
    return standardKeyboardAppShellConfig(product, options)
}
