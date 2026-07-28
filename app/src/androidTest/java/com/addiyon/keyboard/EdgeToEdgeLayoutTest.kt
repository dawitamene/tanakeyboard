package com.addiyon.keyboard

import android.app.Activity
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeToEdgeLayoutTest {

    @Test
    fun entryActivityActionsStayInsideSafeDrawingInsets() {
        assertActionsInsideSafeBounds<MainActivity>()
        assertActionsInsideSafeBounds<ThemesActivity>()
        assertActionsInsideSafeBounds<ManualActivity>()
        assertActionsInsideSafeBounds<FeedbackActivity>()
    }

    private inline fun <reified T : Activity> assertActionsInsideSafeBounds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(T::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            val safeBounds = Rect()
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val rootInsets = ViewCompat.getRootWindowInsets(decor)
                assertNotNull("${T::class.java.simpleName} has no root window insets", rootInsets)
                val safeInsets = requireNotNull(rootInsets).getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
                )
                val location = IntArray(2)
                decor.getLocationOnScreen(location)
                safeBounds.set(
                    location[0] + safeInsets.left,
                    location[1] + safeInsets.top,
                    location[0] + decor.width - safeInsets.right,
                    location[1] + decor.height - safeInsets.bottom
                )
                assertTrue(
                    "${T::class.java.simpleName} did not receive system bar insets",
                    safeInsets.top > 0 || safeInsets.bottom > 0
                )
            }

            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@use
            val packageName = instrumentation.targetContext.packageName
            val outside = ArrayList<Rect>()
            collectClickableBoundsOutside(root, packageName, safeBounds, outside)
            assertTrue(
                "${T::class.java.simpleName} has actions outside safe drawing bounds: " +
                    outside.joinToString(),
                outside.isEmpty()
            )
        }
    }

    private fun collectClickableBoundsOutside(
        node: AccessibilityNodeInfo,
        packageName: String,
        safeBounds: Rect,
        outside: MutableList<Rect>
    ) {
        if (node.packageName?.toString() == packageName &&
            node.isVisibleToUser &&
            node.isClickable
        ) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty && !safeBounds.contains(bounds)) {
                outside += bounds
            }
        }
        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child ->
                collectClickableBoundsOutside(child, packageName, safeBounds, outside)
            }
        }
    }
}
