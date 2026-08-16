package com.addiyon.keyboard.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.addiyon.keyboard.ui.KeyboardToolbarAction
import com.addiyon.keyboard.ui.OptionalKeyboardUi
import com.addiyon.keyboard.ui.ai.AiAccountStore
import com.addiyon.keyboard.ui.ai.AiResultsOverlay
import com.addiyon.keyboard.ui.ai.AiToneRow
import com.addiyon.keyboard.ui.ai.AiUiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiKeyboardFeature internal constructor(
    private val controller: AiController,
    private val store: AiAccountStore,
    private val strings: AiUiStrings,
    private val isPrivateFieldProvider: () -> Boolean,
    private val prepareForAiAction: () -> Unit,
    private val onTextReplaced: () -> Unit,
    private val openAuth: () -> Unit,
    private val openDashboard: () -> Unit,
    private val openCustomTone: () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    var uiState by mutableStateOf(
        AiUiState(
            quota = store.quota(),
            authEmail = store.email().orEmpty()
        )
    )
        private set

    var customTones by mutableStateOf(emptyList<CustomTone>())
        private set

    private var requestJob: Job? = null
    private var toneActionsEnabled by mutableStateOf(false)
    private var customToneChangeListener: (() -> Unit)? = null

    @Composable
    fun optionalKeyboardUi(): OptionalKeyboardUi = OptionalKeyboardUi(
        toolbarAction = KeyboardToolbarAction(
            icon = Icons.Outlined.AutoAwesome,
            contentDescription = strings.aiToolbarDescription,
            onClick = ::onToolbarAction
        ),
        contextualRowVisible = true,
        contextualRow = {
            AiToneRow(
                selectedTab = uiState.selectedTab,
                customTones = customTones,
                selectedCustomToneId = uiState.selectedCustomToneId,
                isLoading = uiState.isLoading,
                enabled = toneActionsEnabled,
                strings = strings,
                useKeyboardTopRowSpacing = true,
                onBack = if (
                    uiState.isLoading ||
                    uiState.hasResult ||
                    (uiState.selectedTab != null && uiState.error != null) ||
                    (uiState.selectedCustomToneId != null && uiState.error != null)
                ) {
                    ::dismissResponse
                } else {
                    null
                },
                onTabSelected = ::onKeyboardToneSelected,
                onCustomToneSelected = ::onKeyboardCustomToneSelected,
                onAddCustomTone = ::onAddCustomTone
            )
        },
        contentOverlayVisible = uiState.isLoading ||
            uiState.hasResult ||
            (uiState.selectedTab != null && uiState.error != null) ||
            (uiState.selectedCustomToneId != null && uiState.error != null),
        contentOverlay = {
            AiResultsOverlay(
                state = uiState,
                strings = strings,
                onReplaceVariant = ::onReplaceVariant
            )
        }
    )

    fun onToolbarAction() {
        if (store.jwt().isNullOrBlank()) {
            openAuth()
        } else {
            openDashboard()
        }
    }

    fun dismissResponse() {
        cancelRequest()
        uiState = AiUiState(
            quota = store.quota(),
            authEmail = store.email().orEmpty()
        )
        refreshToneActionsEnabled()
    }

    fun onTabSelected(tab: AiToneTab) = startToneRequest(tab, null)

    fun onCustomToneSelected(custom: CustomTone) = startToneRequest(null, custom)

    private fun startToneRequest(tab: AiToneTab?, custom: CustomTone?) {
        cancelRequest()
        val loadResultsInPlace = uiState.hasResult || uiState.isResultLoading
        uiState = uiState.copy(
            selectedTab = tab,
            selectedCustomToneId = custom?.id,
            isLoading = false,
            isResultLoading = false,
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
        toneActionsEnabled = input.text.isNotBlank()
        uiState = uiState.copy(input = input)
        if (input.text.isBlank()) {
            uiState = uiState.copy(error = AiError.NoText)
            return
        }
        if (uiState.quota.remaining <= 0) {
            uiState = uiState.copy(error = AiError.QuotaExceeded(uiState.quota.remaining))
            return
        }
        uiState = uiState.copy(
            isLoading = true,
            isResultLoading = loadResultsInPlace,
            error = null
        )
        requestJob = scope.launch {
            val results = withContext(Dispatchers.IO) {
                if (custom != null) {
                    controller.revampCustomVariants(input, custom.instruction)
                } else {
                    controller.revampVariants(input, checkNotNull(tab))
                }
            }
            val stillCurrent = if (custom != null) {
                uiState.selectedCustomToneId == custom.id
            } else {
                uiState.selectedTab == tab
            }
            if (!uiState.isLoading || !stillCurrent) return@launch
            val successes = results.mapNotNull { (strength, result) ->
                result.getOrNull()?.let { strength to it }
            }.toMap()
            val failures = results.mapNotNull { (strength, result) ->
                result.exceptionOrNull()?.let { strength to controller.parseError(it) }
            }.toMap()
            if (successes.isNotEmpty()) {
                val refreshedQuota = withContext(Dispatchers.IO) {
                    controller.loadQuota().getOrNull()
                }
                refreshedQuota?.let(store::saveQuota)
                val selected = successes.keys.firstOrNull()
                uiState = uiState.copy(
                    quota = refreshedQuota ?: uiState.quota,
                    variantResults = successes,
                    variantErrors = failures,
                    selectedVariant = selected,
                    result = successes[selected],
                    isLoading = false,
                    isResultLoading = false,
                    error = null
                )
            } else {
                val firstError = failures.values.firstOrNull()
                val refreshedQuota = if (firstError is AiError.QuotaExceeded) {
                    withContext(Dispatchers.IO) { controller.loadQuota().getOrNull() }
                } else {
                    null
                }
                refreshedQuota?.let(store::saveQuota)
                val displayedError = if (
                    firstError is AiError.QuotaExceeded && refreshedQuota != null
                ) {
                    firstError.copy(remaining = refreshedQuota.remaining)
                } else {
                    firstError
                }
                uiState = uiState.copy(
                    quota = refreshedQuota ?: uiState.quota,
                    variantResults = emptyMap(),
                    variantErrors = failures,
                    isLoading = false,
                    isResultLoading = false,
                    error = displayedError
                )
            }
            requestJob = null
        }
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
                uiState = AiUiState(
                    quota = store.quota(),
                    authEmail = store.email().orEmpty()
                )
                controller.invalidateEditorCaptures()
                onTextReplaced()
            }
            AiEditorReplaceResult.TextChanged -> {
                uiState = uiState.copy(
                    error = AiError.Server(strings.aiErrorTextChanged),
                    variantResults = emptyMap()
                )
            }
            AiEditorReplaceResult.SelectionChanged -> {
                uiState = uiState.copy(
                    error = AiError.Server(strings.aiErrorSelectionChanged),
                    variantResults = emptyMap()
                )
            }
            AiEditorReplaceResult.Failed -> {
                uiState = uiState.copy(
                    error = AiError.Server(strings.aiErrorReplaceFailed),
                    variantResults = emptyMap()
                )
            }
        }
    }

    fun onStartInput() {
        resetForLifecycle()
        refreshToneActionsEnabled()
    }

    fun onStartInputView() {
        resetForLifecycle()
        refreshToneActionsEnabled()
    }

    fun onFinishInput() = resetForLifecycle()

    fun onFinishInputView() = resetForLifecycle()

    fun onDestroy() {
        resetForLifecycle()
        customToneChangeListener?.let { store.unregisterCustomToneChangeListener(it) }
        customToneChangeListener = null
        scope.cancel()
    }

    fun onEditorContextChanged() {
        if (uiState.selectedTab != null || uiState.selectedCustomToneId != null) {
            dismissResponse()
        } else {
            refreshToneActionsEnabled()
        }
    }

    private fun onKeyboardToneSelected(tab: AiToneTab) {
        if (!toneActionsEnabled) return
        prepareForAiAction()
        if (store.jwt().isNullOrBlank()) {
            openAuth()
            return
        }
        onTabSelected(tab)
    }

    private fun onKeyboardCustomToneSelected(custom: CustomTone) {
        if (!toneActionsEnabled) return
        prepareForAiAction()
        if (store.jwt().isNullOrBlank()) {
            openAuth()
            return
        }
        onCustomToneSelected(custom)
    }

    fun onAddCustomTone() {
        prepareForAiAction()
        openCustomTone()
    }

    private fun refreshToneActionsEnabled() {
        toneActionsEnabled = !isPrivateFieldProvider() && controller.captureInput().text.isNotBlank()
    }

    private fun refreshCustomTones() {
        customTones = store.customTones()
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
        toneActionsEnabled = false
        refreshCustomTones()
        uiState = AiUiState(
            quota = store.quota(),
            authEmail = store.email().orEmpty()
        )
    }

    private fun attachCustomToneStore() {
        refreshCustomTones()
        val listener = { customTones = store.customTones() }
        customToneChangeListener = listener
        store.registerCustomToneChangeListener(listener)
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
            prepareForAiAction: () -> Unit,
            onTextReplaced: () -> Unit,
            openAuth: () -> Unit,
            openDashboard: () -> Unit,
            openCustomTone: () -> Unit
        ): AiKeyboardFeature {
            val appContext = context.applicationContext
            val store = AiPreferences(appContext)
            store.setPhraseCompletionsEnabled(false)
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
            val featureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val feature = AiKeyboardFeature(
                controller = controller,
                store = store,
                strings = strings,
                isPrivateFieldProvider = isPrivateFieldProvider,
                prepareForAiAction = prepareForAiAction,
                onTextReplaced = onTextReplaced,
                openAuth = openAuth,
                openDashboard = openDashboard,
                openCustomTone = openCustomTone,
                scope = featureScope
            )
            feature.attachCustomToneStore()
            return feature
        }
    }
}
