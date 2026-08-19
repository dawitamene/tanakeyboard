package com.addiyon.keyboard

import android.content.Context
import android.content.Intent
import com.addiyon.keyboard.features.appshell.KeyboardExternalActions
import com.addiyon.keyboard.ui.ai.AiUiStrings

internal fun TextRevampStrings.asAiUiStrings(): AiUiStrings = object : AiUiStrings {
    override val back = this@asAiUiStrings.aiBack
    override val aiToolbarDescription = this@asAiUiStrings.aiDescription
    override val aiAccountDisconnectedDescription = this@asAiUiStrings.aiAccountDisconnectedDescription
    override val aiAccountFallback = this@asAiUiStrings.aiAccountFallback
    override val aiAccountSectionTitle = this@asAiUiStrings.aiAccountSectionTitle
    override val aiAccountTitle = this@asAiUiStrings.aiAccountTitle
    override val aiAnonymousAccess = this@asAiUiStrings.aiAnonymousAccess
    override val aiAuthAction = this@asAiUiStrings.aiAuthAction
    override val aiAuthMessage = this@asAiUiStrings.aiAuthMessage
    override val aiAuthSending = this@asAiUiStrings.aiAuthSending
    override val aiAuthTitle = this@asAiUiStrings.aiAuthTitle
    override val aiCopyDescription = this@asAiUiStrings.aiCopyDescription
    override val aiClipboardLabel = this@asAiUiStrings.aiClipboardLabel
    override val aiCopiedMessage = this@asAiUiStrings.aiCopiedMessage
    override val aiEmptyAction = this@asAiUiStrings.aiEmptyAction
    override val aiEmptyMessage = this@asAiUiStrings.aiEmptyMessage
    override val aiEmptyTitle = this@asAiUiStrings.aiEmptyTitle
    override val aiErrorDescription = this@asAiUiStrings.aiErrorDescription
    override val aiErrorNeedsAuth = this@asAiUiStrings.aiErrorNeedsAuth
    override val aiErrorNoText = this@asAiUiStrings.aiErrorNoText
    override val aiErrorOffline = this@asAiUiStrings.aiErrorOffline
    override val aiErrorPrivateField = this@asAiUiStrings.aiErrorPrivateField
    override val aiErrorQuotaFormat = this@asAiUiStrings.aiErrorQuotaFormat
    override val aiErrorRateLimited = this@asAiUiStrings.aiErrorRateLimited
    override val aiErrorUnknown = this@asAiUiStrings.aiErrorUnknown
    override val aiErrorTextChanged = this@asAiUiStrings.aiErrorTextChanged
    override val aiErrorSelectionChanged = this@asAiUiStrings.aiErrorSelectionChanged
    override val aiErrorReplaceFailed = this@asAiUiStrings.aiErrorReplaceFailed
    override val aiPrivateMessage = this@asAiUiStrings.aiPrivateMessage
    override val aiPrivateTitle = this@asAiUiStrings.aiPrivateTitle
    override val aiReplaceDescription = this@asAiUiStrings.aiReplaceDescription
    override val aiSelectToneMessage = this@asAiUiStrings.aiSelectToneMessage
    override val aiSignInAction = this@asAiUiStrings.aiSignInAction
    override val aiSignOut = this@asAiUiStrings.aiSignOut
    override val aiToneCasual = this@asAiUiStrings.aiToneCasual
    override val aiToneFixGrammar = this@asAiUiStrings.aiToneFixGrammar
    override val aiToneFormal = this@asAiUiStrings.aiToneFormal
    override val aiToneFriendly = this@asAiUiStrings.aiToneFriendly
    override val aiToneHumanize = this@asAiUiStrings.aiToneHumanize
    override val aiToneProfessional = this@asAiUiStrings.aiToneProfessional
    override val aiToneShorten = this@asAiUiStrings.aiToneShorten
    override val aiToneSummarize = this@asAiUiStrings.aiToneSummarize
    override val aiAddCustomTone = this@asAiUiStrings.aiAddCustomTone
    override val aiCustomToneTitle = this@asAiUiStrings.aiCustomToneTitle
    override val aiCustomToneDescription = this@asAiUiStrings.aiCustomToneDescription
    override val aiCustomToneNewHeading = this@asAiUiStrings.aiCustomToneNewHeading
    override val aiCustomToneEditHeading = this@asAiUiStrings.aiCustomToneEditHeading
    override val aiCustomToneTitleLabel = this@asAiUiStrings.aiCustomToneTitleLabel
    override val aiCustomToneInstructionLabel = this@asAiUiStrings.aiCustomToneInstructionLabel
    override val aiCustomToneIconLabel = this@asAiUiStrings.aiCustomToneIconLabel
    override val aiCustomToneTitleFieldPlaceholder = this@asAiUiStrings.aiCustomToneTitleFieldPlaceholder
    override val aiCustomToneFieldPlaceholder = this@asAiUiStrings.aiCustomToneFieldPlaceholder
    override val aiCustomToneSave = this@asAiUiStrings.aiCustomToneSave
    override val aiCustomToneSaveChanges = this@asAiUiStrings.aiCustomToneSaveChanges
    override val aiCustomToneCancel = this@asAiUiStrings.aiCustomToneCancel
    override val aiCustomToneEdit = this@asAiUiStrings.aiCustomToneEdit
    override val aiCustomToneListHeading = this@asAiUiStrings.aiCustomToneListHeading
    override val aiCustomToneEmpty = this@asAiUiStrings.aiCustomToneEmpty
    override val aiReorderInstructionsTitle = this@asAiUiStrings.aiReorderInstructionsTitle
    override val aiReorderInstructionsDescription = this@asAiUiStrings.aiReorderInstructionsDescription
    override val aiReorderInstructionsSave = this@asAiUiStrings.aiReorderInstructionsSave
    override val aiReorderInstructionsBuiltInBadge = this@asAiUiStrings.aiReorderInstructionsBuiltInBadge
    override val aiReorderInstructionsCustomBadge = this@asAiUiStrings.aiReorderInstructionsCustomBadge
    override val aiCustomToneRemove = this@asAiUiStrings.aiCustomToneRemove
    override val aiCustomToneError = this@asAiUiStrings.aiCustomToneError
    override val aiAddNewInstructionTitle = this@asAiUiStrings.aiAddNewInstructionTitle
    override val aiCustomToneIconAutoAwesome = this@asAiUiStrings.aiCustomToneIconAutoAwesome
    override val aiCustomToneIconFace = this@asAiUiStrings.aiCustomToneIconFace
    override val aiCustomToneIconFavorite = this@asAiUiStrings.aiCustomToneIconFavorite
    override val aiCustomToneIconStar = this@asAiUiStrings.aiCustomToneIconStar
    override val aiCustomToneIconBolt = this@asAiUiStrings.aiCustomToneIconBolt
    override val aiCustomToneIconPalette = this@asAiUiStrings.aiCustomToneIconPalette
    override val aiCustomToneIconMusicNote = this@asAiUiStrings.aiCustomToneIconMusicNote
    override val aiCustomToneIconEmojiEmotions = this@asAiUiStrings.aiCustomToneIconEmojiEmotions
    override val aiCustomToneIconSentimentSatisfied = this@asAiUiStrings.aiCustomToneIconSentimentSatisfied
    override val aiCustomToneIconThumbUp = this@asAiUiStrings.aiCustomToneIconThumbUp
    override val aiCustomToneIconWbSunny = this@asAiUiStrings.aiCustomToneIconWbSunny
    override val aiCustomToneIconLocalFireDepartment = this@asAiUiStrings.aiCustomToneIconLocalFireDepartment
    override val aiCustomToneIconWaterDrop = this@asAiUiStrings.aiCustomToneIconWaterDrop
    override val aiCustomToneIconEco = this@asAiUiStrings.aiCustomToneIconEco
    override val aiCustomToneIconPets = this@asAiUiStrings.aiCustomToneIconPets
    override val aiCustomToneIconSchool = this@asAiUiStrings.aiCustomToneIconSchool
    override val aiCustomToneIconWorkOutline = this@asAiUiStrings.aiCustomToneIconWorkOutline
    override val aiCustomToneIconAccountBalance = this@asAiUiStrings.aiCustomToneIconAccountBalance
    override val aiCustomToneIconSpellcheck = this@asAiUiStrings.aiCustomToneIconSpellcheck
    override val aiCustomToneIconShorten = this@asAiUiStrings.aiCustomToneIconShorten
    override val aiCustomToneIconSummarize = this@asAiUiStrings.aiCustomToneIconSummarize
    override val aiCustomToneIconVerified = this@asAiUiStrings.aiCustomToneIconVerified
    override val aiCustomToneIconDiamond = this@asAiUiStrings.aiCustomToneIconDiamond
    override val aiCustomToneIconRocket = this@asAiUiStrings.aiCustomToneIconRocket
    override val aiCustomToneIconCastle = this@asAiUiStrings.aiCustomToneIconCastle
    override val aiCustomToneIconTerrain = this@asAiUiStrings.aiCustomToneIconTerrain
    override val aiCustomToneIconFlight = this@asAiUiStrings.aiCustomToneIconFlight
    override val aiCustomToneIconCoffee = this@asAiUiStrings.aiCustomToneIconCoffee
    override val aiCustomToneIconIcecream = this@asAiUiStrings.aiCustomToneIconIcecream
    override val aiCustomToneIconNightlight = this@asAiUiStrings.aiCustomToneIconNightlight
    override val aiCustomToneIconQuestionAnswer = this@asAiUiStrings.aiCustomToneIconQuestionAnswer
    override val aiCustomToneIconAutoStories = this@asAiUiStrings.aiCustomToneIconAutoStories
    override val aiCustomToneColorTeal = this@asAiUiStrings.aiCustomToneColorTeal
    override val aiCustomToneColorIndigo = this@asAiUiStrings.aiCustomToneColorIndigo
    override val aiCustomToneColorOrange = this@asAiUiStrings.aiCustomToneColorOrange
    override val aiCustomToneColorPurple = this@asAiUiStrings.aiCustomToneColorPurple
    override val aiCustomToneColorGreen = this@asAiUiStrings.aiCustomToneColorGreen
    override val aiCustomToneColorRose = this@asAiUiStrings.aiCustomToneColorRose
    override val aiCustomToneColorBlue = this@asAiUiStrings.aiCustomToneColorBlue
    override val aiCustomToneColorAmber = this@asAiUiStrings.aiCustomToneColorAmber
    override val aiCustomToneColorCyan = this@asAiUiStrings.aiCustomToneColorCyan
    override val aiCustomToneColorLime = this@asAiUiStrings.aiCustomToneColorLime
    override val aiCustomToneColorPink = this@asAiUiStrings.aiCustomToneColorPink
    override val aiCustomToneColorRed = this@asAiUiStrings.aiCustomToneColorRed
    override val aiCustomToneColorYellow = this@asAiUiStrings.aiCustomToneColorYellow
    override val aiCustomToneColorBrown = this@asAiUiStrings.aiCustomToneColorBrown
    override val aiCustomToneColorGrey = this@asAiUiStrings.aiCustomToneColorGrey
    override val aiCustomToneColorDeepPurple = this@asAiUiStrings.aiCustomToneColorDeepPurple
    override val aiTruncated = this@asAiUiStrings.aiTruncated
    override val aiUsageRemaining = this@asAiUiStrings.aiUsageRemaining
    override val aiUsageTokensRemainingFormat = this@asAiUiStrings.aiUsageTokensRemainingFormat
    override val aiUsageResetDateFormat = this@asAiUiStrings.aiUsageResetDateFormat
    override val aiUsageResetTodayFormat = this@asAiUiStrings.aiUsageResetTodayFormat
    override val aiUsageResetTomorrowFormat = this@asAiUiStrings.aiUsageResetTomorrowFormat
    override val aiUsageTitle = this@asAiUiStrings.aiUsageTitle
    override val aiVariantErrorNeedsAuth = this@asAiUiStrings.aiVariantErrorNeedsAuth
    override val aiVariantErrorNoText = this@asAiUiStrings.aiVariantErrorNoText
    override val aiVariantErrorOffline = this@asAiUiStrings.aiVariantErrorOffline
    override val aiVariantErrorPrivateField = this@asAiUiStrings.aiVariantErrorPrivateField
    override val aiVariantErrorQuota = this@asAiUiStrings.aiVariantErrorQuota
    override val aiVariantErrorTryAgain = this@asAiUiStrings.aiVariantErrorTryAgain
    override val aiVariantErrorUnavailable = this@asAiUiStrings.aiVariantErrorUnavailable
    override val aiWorkspaceConnectedDescription = this@asAiUiStrings.aiWorkspaceConnectedDescription
    override val aiWorkspaceDisconnectedDescription = this@asAiUiStrings.aiWorkspaceDisconnectedDescription
    override val aiWorkspaceTitle = this@asAiUiStrings.aiWorkspaceTitle
    override val aiPhraseCompletionTitle = this@asAiUiStrings.aiPhraseCompletionTitle
    override val aiPhraseCompletionDescription = this@asAiUiStrings.aiPhraseCompletionDescription
    override val aiPhraseCompletionSignedOut = this@asAiUiStrings.aiPhraseCompletionSignedOut
    override val aiPhraseCompletionDisclosureTitle =
        this@asAiUiStrings.aiPhraseCompletionDisclosureTitle
    override val aiPhraseCompletionDisclosureMessage =
        this@asAiUiStrings.aiPhraseCompletionDisclosureMessage
    override val aiPhraseCompletionEnable = this@asAiUiStrings.aiPhraseCompletionEnable
    override val aiPhraseCompletionNotNow = this@asAiUiStrings.aiPhraseCompletionNotNow
    override val aiPhraseCompletionIdle = this@asAiUiStrings.aiPhraseCompletionIdle
    override val aiPhraseCompletionLoading = this@asAiUiStrings.aiPhraseCompletionLoading
    override val aiPhraseCompletionInsert = this@asAiUiStrings.aiPhraseCompletionInsert
    override val aiPhraseCompletionDismiss = this@asAiUiStrings.aiPhraseCompletionDismiss
}

internal fun Context.openAiAccount(mode: String) {
    val intent = Intent(this, AiAccountActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(AiAccountActivity.EXTRA_MODE, mode)
    KeyboardExternalActions.start(this, intent, "Unable to open TextRevamp AI account.")
}
