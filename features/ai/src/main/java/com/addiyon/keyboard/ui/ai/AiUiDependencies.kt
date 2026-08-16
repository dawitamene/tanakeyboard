package com.addiyon.keyboard.ui.ai

import com.addiyon.keyboard.ai.AiQuota
import com.addiyon.keyboard.ai.CustomTone

interface AiUiStrings {
    val back: String
    val aiToolbarDescription: String
    val aiAccountDisconnectedDescription: String
    val aiAccountFallback: String
    val aiAccountSectionTitle: String
    val aiAccountTitle: String
    val aiAnonymousAccess: String
    val aiAuthAction: String
    val aiAuthMessage: String
    val aiAuthSending: String
    val aiAuthTitle: String
    val aiCopyDescription: String
    val aiClipboardLabel: String
    val aiCopiedMessage: String
    val aiEmptyAction: String
    val aiEmptyMessage: String
    val aiEmptyTitle: String
    val aiErrorDescription: String
    val aiErrorNeedsAuth: String
    val aiErrorNoText: String
    val aiErrorOffline: String
    val aiErrorPrivateField: String
    val aiErrorQuotaFormat: String
    val aiErrorRateLimited: String
    val aiErrorUnknown: String
    val aiErrorTextChanged: String
    val aiErrorSelectionChanged: String
    val aiErrorReplaceFailed: String
    val aiPrivateMessage: String
    val aiPrivateTitle: String
    val aiReplaceDescription: String
    val aiSelectToneMessage: String
    val aiSignInAction: String
    val aiSignOut: String
    val aiToneCasual: String
    val aiToneFixGrammar: String
    val aiToneFormal: String
    val aiToneFriendly: String
    val aiToneHumanize: String
    val aiToneProfessional: String
    val aiToneShorten: String
    val aiToneSummarize: String
    val aiAddCustomTone: String
    val aiCustomToneTitle: String
    val aiCustomToneDescription: String
    val aiCustomToneNewHeading: String
    val aiCustomToneEditHeading: String
    val aiCustomToneTitleLabel: String
    val aiCustomToneInstructionLabel: String
    val aiCustomToneIconLabel: String
    val aiCustomToneTitleFieldPlaceholder: String
    val aiCustomToneFieldPlaceholder: String
    val aiCustomToneSave: String
    val aiCustomToneSaveChanges: String
    val aiCustomToneCancel: String
    val aiCustomToneEdit: String
    val aiCustomToneListHeading: String
    val aiCustomToneEmpty: String
    val aiCustomToneRemove: String
    val aiCustomToneError: String
    val aiCustomToneIconAutoAwesome: String
    val aiCustomToneIconFace: String
    val aiCustomToneIconFavorite: String
    val aiCustomToneIconStar: String
    val aiCustomToneIconBolt: String
    val aiCustomToneIconPalette: String
    val aiCustomToneIconMusicNote: String
    val aiCustomToneIconEmojiEmotions: String
    val aiCustomToneIconSentimentSatisfied: String
    val aiCustomToneIconThumbUp: String
    val aiCustomToneIconWbSunny: String
    val aiCustomToneIconLocalFireDepartment: String
    val aiCustomToneIconWaterDrop: String
    val aiCustomToneIconEco: String
    val aiCustomToneIconPets: String
    val aiCustomToneIconSchool: String
    val aiCustomToneIconWorkOutline: String
    val aiCustomToneIconAccountBalance: String
    val aiCustomToneIconSpellcheck: String
    val aiCustomToneIconShorten: String
    val aiCustomToneIconSummarize: String
    val aiCustomToneIconVerified: String
    val aiCustomToneIconDiamond: String
    val aiCustomToneIconRocket: String
    val aiCustomToneIconCastle: String
    val aiCustomToneIconTerrain: String
    val aiCustomToneIconFlight: String
    val aiCustomToneIconCoffee: String
    val aiCustomToneIconIcecream: String
    val aiCustomToneIconNightlight: String
    val aiCustomToneIconQuestionAnswer: String
    val aiCustomToneIconAutoStories: String
    val aiCustomToneColorTeal: String
    val aiCustomToneColorIndigo: String
    val aiCustomToneColorOrange: String
    val aiCustomToneColorPurple: String
    val aiCustomToneColorGreen: String
    val aiCustomToneColorRose: String
    val aiCustomToneColorBlue: String
    val aiCustomToneColorAmber: String
    val aiTruncated: String
    val aiUsageRemaining: String
    val aiUsageTokensRemainingFormat: String
    val aiUsageResetDateFormat: String
    val aiUsageResetTodayFormat: String
    val aiUsageResetTomorrowFormat: String
    val aiUsageTitle: String
    val aiVariantErrorNeedsAuth: String
    val aiVariantErrorNoText: String
    val aiVariantErrorOffline: String
    val aiVariantErrorPrivateField: String
    val aiVariantErrorQuota: String
    val aiVariantErrorTryAgain: String
    val aiVariantErrorUnavailable: String
    val aiWorkspaceConnectedDescription: String
    val aiWorkspaceDisconnectedDescription: String
    val aiWorkspaceTitle: String
    val aiPhraseCompletionTitle: String
    val aiPhraseCompletionDescription: String
    val aiPhraseCompletionSignedOut: String
    val aiPhraseCompletionDisclosureTitle: String
    val aiPhraseCompletionDisclosureMessage: String
    val aiPhraseCompletionEnable: String
    val aiPhraseCompletionNotNow: String
    val aiPhraseCompletionIdle: String
    val aiPhraseCompletionLoading: String
    val aiPhraseCompletionInsert: String
    val aiPhraseCompletionDismiss: String
}

interface AiAccountStore {
    fun jwt(): String?
    fun setJwt(value: String?)
    fun email(): String?
    fun setEmail(value: String?)
    fun anonymousId(): String
    fun quota(): AiQuota
    fun saveQuota(quota: AiQuota)
    fun clearJwt()
    fun phraseCompletionsEnabled(): Boolean
    fun setPhraseCompletionsEnabled(enabled: Boolean)
    fun phraseCompletionConsentVersion(): Int
    fun setPhraseCompletionConsentVersion(version: Int)
    fun customTones(): List<CustomTone>
    fun addCustomTone(title: String, instruction: String, icon: String, color: String): CustomTone?
    fun updateCustomTone(
        id: String,
        title: String,
        instruction: String,
        icon: String,
        color: String
    ): CustomTone?
    fun removeCustomTone(id: String)
    fun registerCustomToneChangeListener(listener: () -> Unit)
    fun unregisterCustomToneChangeListener(listener: () -> Unit)
}
