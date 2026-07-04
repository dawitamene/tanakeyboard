// ui/keys/RepeatingClickable.kt
package com.addiyon.tanakeyboard.ui.keys

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Like [androidx.compose.foundation.clickable], but for keys that should
 * keep firing [onClick] repeatedly for as long as they're held down --
 * e.g. Backspace, matching the press-and-hold-to-delete behavior every
 * other keyboard (Gboard, iOS, physical) has.
 *
 * Plain `clickable` only ever fires once per tap, on release -- fine for
 * letter keys, but it's why holding Delete previously removed just one
 * character no matter how long you held it.
 *
 * Behavior: fires once immediately on press-down (so a quick tap still
 * deletes exactly one character, same as before), then waits
 * [initialDelayMillis] before repeating every [repeatDelayMillis] until
 * the finger/pointer lifts or the gesture is cancelled.
 */
internal fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelayMillis: Long = 400,
    repeatDelayMillis: Long = 50,
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick = rememberUpdatedState(onClick)

    if (!enabled) {
        this
    } else {
        pointerInput(Unit) {
            coroutineScope {
                while (isActive) {
                    awaitPointerEventScope {
                        awaitFirstDown(requireUnconsumed = false)
                    }

                    val repeatJob = launch {
                        currentOnClick.value()
                        delay(initialDelayMillis)
                        while (isActive) {
                            currentOnClick.value()
                            delay(repeatDelayMillis)
                        }
                    }

                    awaitPointerEventScope {
                        waitForUpOrCancellation()
                    }
                    repeatJob.cancel()
                }
            }
        }
    }
}