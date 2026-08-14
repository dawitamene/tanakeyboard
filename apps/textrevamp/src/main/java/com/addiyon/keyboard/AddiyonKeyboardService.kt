package com.addiyon.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import com.addiyon.keyboard.ai.AiKeyboardFeature
import com.addiyon.keyboard.features.appshell.KeyboardAppShellActivity
import com.addiyon.keyboard.ui.OptionalKeyboardUi

class AddiyonKeyboardService : PackKeyboardService() {
    override val configuredKeyboardProduct = TextRevampKeyboardProduct
    override val appShellActivityClass: Class<out KeyboardAppShellActivity> = MainActivity::class.java

    private lateinit var aiFeature: AiKeyboardFeature

    override val configuredLanguagePackProviders = TextRevampLanguagePackProviders

    override fun onCreate() {
        super.onCreate()
        aiFeature = AiKeyboardFeature.create(
            context = this,
            editor = AiEditorAdapter(editorGateway),
            strings = EnglishTextRevampStrings.asAiUiStrings(),
            isPrivateFieldProvider = { isPrivateField },
            prepareForPanel = ::prepareForOptionalPanel,
            onTextReplaced = ::onOptionalFeatureTextReplaced,
            openAuth = { openAiAccount(AiAccountActivity.MODE_AUTH) },
            openDashboard = { openAiAccount(AiAccountActivity.MODE_DASHBOARD) }
        )
    }

    @Composable
    override fun optionalKeyboardUi(): OptionalKeyboardUi =
        if (::aiFeature.isInitialized) aiFeature.optionalKeyboardUi() else OptionalKeyboardUi()

    override fun onStartInput(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)
        if (::aiFeature.isInitialized) aiFeature.onStartInput()
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        if (::aiFeature.isInitialized) aiFeature.onStartInputView()
    }

    override fun onFinishInput() {
        if (::aiFeature.isInitialized) aiFeature.onFinishInput()
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (::aiFeature.isInitialized) aiFeature.onFinishInputView()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        if (::aiFeature.isInitialized) aiFeature.onDestroy()
        super.onDestroy()
    }

    protected override fun onEditorContextChanged() {
        if (::aiFeature.isInitialized) aiFeature.onEditorContextChanged()
    }
}
