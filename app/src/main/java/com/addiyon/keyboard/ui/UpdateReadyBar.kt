package com.addiyon.keyboard.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.addiyon.keyboard.ui.i18n.LocalAppStrings

/**
 * The restart nudge for a downloaded flexible in-app update.
 *
 * Play supplies the "update available" dialog itself, but nothing for the step
 * after the background download finishes — the app has to ask for the restart
 * that installs it. Indefinite so it waits for the user rather than expiring
 * unnoticed, with an explicit dismiss so it stays declinable, matching the
 * non-forced flexible flow.
 */
@Composable
fun UpdateReadyBar(
    visible: Boolean,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val hostState = remember { SnackbarHostState() }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val result = hostState.showSnackbar(
            message = strings.updateDownloaded,
            actionLabel = strings.updateRestart,
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite
        )
        if (result == SnackbarResult.ActionPerformed) onInstall()
    }

    SnackbarHost(hostState, modifier = modifier.navigationBarsPadding())
}
