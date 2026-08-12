package com.addiyon.keyboard.ui.keys

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.addiyon.keyboard.TestKeyboardHost
import com.addiyon.keyboard.ui.KeyboardTestTags
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeleteKeyUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun completedTapDispatchesExactlyOneDelete() {
        val clicks = AtomicInteger()
        setDeleteKey(clicks)

        compose.onNodeWithTag(KeyboardTestTags.KEY_DELETE).performClick()
        compose.runOnIdle { assertEquals(1, clicks.get()) }
    }

    @Test
    fun heldDeleteRepeatsOnlyUntilRelease() {
        val clicks = AtomicInteger()
        val starts = AtomicInteger()
        val ends = AtomicInteger()
        setDeleteKey(clicks, starts, ends)
        compose.mainClock.autoAdvance = false

        val delete = compose.onNodeWithTag(KeyboardTestTags.KEY_DELETE)
        delete.performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(0, clicks.get()) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertTrue(clicks.get() > 1)
            assertEquals(1, starts.get())
            assertEquals(0, ends.get())
        }
        delete.performTouchInput { up() }
        val countAtRelease = clicks.get()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(countAtRelease, clicks.get())
            assertEquals(1, starts.get())
            assertEquals(1, ends.get())
        }
    }

    @Test
    fun cancelledDeleteGestureDispatchesNothing() {
        val clicks = AtomicInteger()
        setDeleteKey(clicks)
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag(KeyboardTestTags.KEY_DELETE).performTouchInput {
            down(center)
            cancel()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(0, clicks.get()) }
    }

    @Test
    fun continuousHoldKeepsRepeatingUntilRelease() {
        val clicks = AtomicInteger()
        val starts = AtomicInteger()
        val ends = AtomicInteger()
        setDeleteKey(clicks, starts, ends)
        compose.mainClock.autoAdvance = false

        val delete = compose.onNodeWithTag(KeyboardTestTags.KEY_DELETE)
        delete.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(10_000)
        compose.runOnIdle {
            assertTrue(clicks.get() > 80)
            assertEquals(1, starts.get())
            assertEquals(0, ends.get())
        }
        delete.performTouchInput { up() }
        val countAtRelease = clicks.get()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(countAtRelease, clicks.get())
            assertEquals(1, ends.get())
        }
    }

    private fun setDeleteKey(
        clicks: AtomicInteger,
        starts: AtomicInteger = AtomicInteger(),
        ends: AtomicInteger = AtomicInteger()
    ) {
        compose.setContent {
            TestKeyboardHost {
                Row {
                    DeleteKey(
                        width = 48.dp,
                        height = 48.dp,
                        vibrateOnKeypress = false,
                        soundOnKeypress = false,
                        onRepeatStart = starts::incrementAndGet,
                        onRepeatEnd = ends::incrementAndGet,
                        onClick = clicks::incrementAndGet
                    )
                }
            }
        }
    }
}
