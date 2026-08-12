package com.addiyon.keyboard.ui.keys

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val DELETE_HOLD_DELAY_MS = 400L
internal const val DELETE_REPEAT_DELAY_MS = 50L

internal fun Modifier.repeatingClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    initialDelayMillis: Long = DELETE_HOLD_DELAY_MS,
    repeatDelayMillis: Long = DELETE_REPEAT_DELAY_MS,
    onRepeatStart: () -> Unit = {},
    onRepeatEnd: () -> Unit = {},
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick = rememberUpdatedState(onClick)
    val currentOnRepeatStart = rememberUpdatedState(onRepeatStart)
    val currentOnRepeatEnd = rememberUpdatedState(onRepeatEnd)

    if (!enabled) {
        this
    } else {
        pointerInput(
            interactionSource,
            initialDelayMillis,
            repeatDelayMillis
        ) {
            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    var repeatCount = 0
                    var repeatSessionActive = false
                    fun endRepeatSession() {
                        if (repeatSessionActive) {
                            repeatSessionActive = false
                            currentOnRepeatEnd.value()
                        }
                    }
                    val repeatJob = launch {
                        delay(initialDelayMillis)
                        repeatSessionActive = true
                        currentOnRepeatStart.value()
                        while (isActive) {
                            currentOnClick.value()
                            repeatCount++
                            delay(repeatDelayMillis)
                        }
                    }
                    var released = false
                    try {
                        released = waitForUpOrCancellation() != null
                        repeatJob.cancel()
                        if (released && repeatCount == 0) {
                            currentOnClick.value()
                        }
                    } finally {
                        repeatJob.cancel()
                        endRepeatSession()
                        interactionSource.tryEmit(
                            if (released) {
                                PressInteraction.Release(press)
                            } else {
                                PressInteraction.Cancel(press)
                            }
                        )
                    }
                }
            }
        }
    }
}
