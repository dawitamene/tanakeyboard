package com.addiyon.keyboard.ui.ai

import com.addiyon.keyboard.ai.AiQuota

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
}
