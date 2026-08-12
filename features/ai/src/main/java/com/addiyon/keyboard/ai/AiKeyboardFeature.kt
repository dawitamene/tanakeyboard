package com.addiyon.keyboard.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.addiyon.keyboard.ui.KeyboardToolbarAction
import com.addiyon.keyboard.ui.OptionalKeyboardUi
import com.addiyon.keyboard.ui.ai.AiAccountStore
import com.addiyon.keyboard.ui.ai.AiPanel
import com.addiyon.keyboard.ui.ai.AiUiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val AI_PANEL_HEIGHT_SCALE = 1.4f

class AiKeyboardFeature internal constructor(
    private val controller: AiController,
    private val store: AiAccountStore,
    private val strings: AiUiStrings,
    private val isPrivateFieldProvider: () -> Boolean,
    private val prepareForPanel: () -> Unit,
    private val onTextReplaced: () -> Unit,
    private val openAuth: () -> Unit,
    private val openDashboard: () -> Unit,
    private val copyResult: (label: String, text: String) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    var uiState by mutableStateOf(
        AiUiState(
            quota = store.quota(),
            authEmail = store.email().orEmpty()
        )
    )
        private set

    private var requestJob: Job? = null

    @Composable
    fun optionalKeyboardUi(): OptionalKeyboardUi = OptionalKeyboardUi(
        toolbarAction = KeyboardToolbarAction(
            icon = Icons.Outlined.AutoAwesome,
            contentDescription = strings.aiToolbarDescription,
            onClick = ::onToolbarAction
        ),
        panelVisible = uiState.isVisible,
        panelHeightScale = AI_PANEL_HEIGHT_SCALE,
        panel = {
            AiPanel(
                state = uiState,
                strings = strings,
                onDismiss = ::dismissPanel,
                onTabSelected = ::onTabSelected,
                onCopyVariant = ::onCopyVariant,
                onReplaceVariant = ::onReplaceVariant,
                onOpenDashboard = openDashboard
            )
        }
    )

    fun onToolbarAction() {
        if (uiState.isVisible) {
            dismissPanel()
            return
        }
        prepareForPanel()
        if (store.jwt().isNullOrBlank()) {
            openAuth()
            return
        }
        val privateField = isPrivateFieldProvider()
        val captured = if (privateField) null else controller.captureInput()
        cancelRequest()
        uiState = AiUiState(
            isVisible = true,
            selectedTab = null,
            input = captured,
            isQuotaLoading = true,
            error = if (privateField) AiError.PrivateField else null,
            quota = store.quota(),
            isPrivateField = privateField,
            needsAuth = false,
            authEmail = store.email().orEmpty()
        )
        requestJob = scope.launch {
            val result = withContext(Dispatchers.IO) { controller.loadQuota() }
            if (!uiState.isVisible || uiState.selectedTab != null) return@launch
            result.onSuccess { quota ->
                store.saveQuota(quota)
                uiState = uiState.copy(
                    quota = quota,
                    isQuotaLoading = false,
                    error = if (isPrivateFieldProvider()) AiError.PrivateField else null
                )
            }.onFailure { throwable ->
                uiState = uiState.copy(
                    isQuotaLoading = false,
                    error = if (isPrivateFieldProvider()) {
                        AiError.PrivateField
                    } else {
                        controller.parseError(throwable)
                    }
                )
            }
            requestJob = null
        }
    }

    fun dismissPanel() {
        cancelRequest()
        uiState = uiState.copy(
            isVisible = false,
            isLoading = false,
            isQuotaLoading = false
        )
    }

    fun onTabSelected(tab: AiToneTab) {
        cancelRequest()
        uiState = uiState.copy(
            selectedTab = tab,
            isQuotaLoading = false,
            result = null,
            error = null,
            variantResults = emptyMap(),
            variantErrors = emptyMap(),
            selectedVariant = null
        )
        if (isPrivateFieldProvider()) {
            uiState = uiState.copy(error = AiError.PrivateField)
            return
        }
        if (uiState.needsAuth || store.jwt().isNullOrBlank()) {
            uiState = uiState.copy(error = AiError.NeedsAuth, needsAuth = true)
            return
        }
        val input = controller.captureInput()
        uiState = uiState.copy(input = input)
        if (input.text.isBlank()) {
            uiState = uiState.copy(error = AiError.NoText)
            return
        }
        if (uiState.quota.remaining <= 0) {
            uiState = uiState.copy(error = AiError.QuotaExceeded(uiState.quota.remaining))
            return
        }
        uiState = uiState.copy(isLoading = true, error = null)
        requestJob = scope.launch {
            val results = withContext(Dispatchers.IO) {
                controller.revampVariants(input, tab)
            }
            if (!uiState.isVisible || uiState.selectedTab != tab) return@launch
            val successes = results.mapNotNull { (strength, result) ->
                result.getOrNull()?.let { strength to it }
            }.toMap()
            val failures = results.mapNotNull { (strength, result) ->
                result.exceptionOrNull()?.let { strength to controller.parseError(it) }
            }.toMap()
            if (successes.isNotEmpty()) {
                val selected = successes.keys.firstOrNull()
                uiState = uiState.copy(
                    quota = store.consumeRequest(),
                    variantResults = successes,
                    variantErrors = failures,
                    selectedVariant = selected,
                    result = successes[selected],
                    isLoading = false,
                    error = null
                )
            } else {
                val firstError = failures.values.firstOrNull()
                uiState = uiState.copy(
                    quota = if (firstError is AiError.QuotaExceeded) store.quota() else uiState.quota,
                    variantResults = emptyMap(),
                    variantErrors = failures,
                    isLoading = false,
                    error = firstError
                )
            }
            requestJob = null
        }
    }

    fun onCopyVariant(strength: AiStrength) {
        val result = selectVariant(strength) ?: return
        copyResult(strings.aiClipboardLabel, result.text)
    }

    fun onReplaceVariant(strength: AiStrength) {
        val result = selectVariant(strength) ?: return
        val input = uiState.input ?: return
        val snapshot = input.snapshot
        if (snapshot == null) {
            uiState = uiState.copy(error = AiError.Server(strings.aiErrorReplaceFailed))
            return
        }
        when (controller.replaceIfCurrent(snapshot, result.text)) {
            AiEditorReplaceResult.Replaced -> {
                uiState = uiState.copy(isVisible = false)
                controller.invalidateEditorCaptures()
                onTextReplaced()
            }
            AiEditorReplaceResult.TextChanged -> {
                uiState = uiState.copy(error = AiError.Server(strings.aiErrorTextChanged))
            }
            AiEditorReplaceResult.SelectionChanged -> {
                uiState = uiState.copy(error = AiError.Server(strings.aiErrorSelectionChanged))
            }
            AiEditorReplaceResult.Failed -> {
                uiState = uiState.copy(error = AiError.Server(strings.aiErrorReplaceFailed))
            }
        }
    }

    fun onStartInput() = resetForLifecycle()

    fun onStartInputView() = resetForLifecycle()

    fun onFinishInput() = resetForLifecycle()

    fun onFinishInputView() = resetForLifecycle()

    fun onDestroy() {
        resetForLifecycle()
        scope.cancel()
    }

    private fun selectVariant(strength: AiStrength): AiResult? {
        val selected = uiState.variantResults[strength]
            ?: uiState.result?.takeIf { strength == AiStrength.Balanced }
            ?: return null
        uiState = uiState.copy(selectedVariant = strength, result = selected)
        return selected
    }

    private fun resetForLifecycle() {
        cancelRequest()
        controller.invalidateEditorCaptures()
        uiState = AiUiState(
            quota = store.quota(),
            authEmail = store.email().orEmpty()
        )
    }

    private fun cancelRequest() {
        requestJob?.cancel()
        requestJob = null
    }

    companion object {
        fun create(
            context: Context,
            editor: AiEditor,
            strings: AiUiStrings,
            isPrivateFieldProvider: () -> Boolean,
            prepareForPanel: () -> Unit,
            onTextReplaced: () -> Unit,
            openAuth: () -> Unit,
            openDashboard: () -> Unit
        ): AiKeyboardFeature {
            val appContext = context.applicationContext
            val store = AiPreferences(appContext)
            val repository = AiRepository(
                AiServiceFactory.create(
                    debug = appContext.applicationInfo.flags and
                        ApplicationInfo.FLAG_DEBUGGABLE != 0
                )
            )
            val controller = AiController(
                editor = editor,
                repository = repository,
                quotaProvider = store::quota,
                jwtProvider = store::jwt,
                anonIdProvider = store::anonymousId,
                isPrivateFieldProvider = isPrivateFieldProvider
            )
            return AiKeyboardFeature(
                controller = controller,
                store = store,
                strings = strings,
                isPrivateFieldProvider = isPrivateFieldProvider,
                prepareForPanel = prepareForPanel,
                onTextReplaced = onTextReplaced,
                openAuth = openAuth,
                openDashboard = openDashboard,
                copyResult = { label, text ->
                    runCatching {
                        val clipboard = appContext.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
                        Toast.makeText(appContext, strings.aiCopiedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
