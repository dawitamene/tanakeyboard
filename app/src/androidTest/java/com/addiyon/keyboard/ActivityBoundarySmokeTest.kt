package com.addiyon.keyboard

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityBoundarySmokeTest {

    @Test
    fun entryActivitiesSurviveDarkLargeFontRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalFontScale = Settings.System.getFloat(
            context.contentResolver,
            Settings.System.FONT_SCALE,
            1f
        )
        val originalNightMode =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        try {
            shell("cmd uimode night yes")
            shell("settings put system font_scale 1.3")
            SystemClock.sleep(CONFIGURATION_SETTLE_MILLIS)
            instrumentation.waitForIdleSync()

            launchAndRecreate<MainActivity>()
            launchAndRecreate<ThemesActivity>()
            launchAndRecreate<ManualActivity>()
            launchAndRecreate<FeedbackActivity>()
        } finally {
            val nightMode = when (originalNightMode) {
                Configuration.UI_MODE_NIGHT_YES -> "yes"
                Configuration.UI_MODE_NIGHT_NO -> "no"
                else -> "auto"
            }
            shell("cmd uimode night $nightMode")
            shell("settings put system font_scale $originalFontScale")
        }
    }

    private inline fun <reified T : Activity> launchAndRecreate() {
        ActivityScenario.launch(T::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
            assertClickableNodesAreLabeled(T::class.java.simpleName)
            scenario.recreate()
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
            assertClickableNodesAreLabeled(T::class.java.simpleName)
        }
    }

    private fun assertClickableNodesAreLabeled(activityName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return
        val unlabeled = ArrayList<String>()
        collectUnlabeledClickableNodes(root, unlabeled)
        assertTrue(
            "$activityName has unlabeled clickable accessibility nodes: " +
                unlabeled.joinToString(),
            unlabeled.isEmpty()
        )
    }

    private fun collectUnlabeledClickableNodes(
        node: AccessibilityNodeInfo,
        unlabeled: MutableList<String>
    ) {
        if (node.isVisibleToUser && node.isClickable && !hasAccessibleLabel(node)) {
            unlabeled += node.viewIdResourceName ?: node.className?.toString().orEmpty()
        }
        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child ->
                collectUnlabeledClickableNodes(child, unlabeled)
            }
        }
    }

    private fun hasAccessibleLabel(node: AccessibilityNodeInfo): Boolean {
        if (!node.text.isNullOrBlank() ||
            !node.contentDescription.isNullOrBlank() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !node.stateDescription.isNullOrBlank())
        ) {
            return true
        }
        repeat(node.childCount) { index ->
            val child = node.getChild(index) ?: return@repeat
            if (hasAccessibleLabel(child)) return true
        }
        return false
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
            .close()
    }

    private companion object {
        const val CONFIGURATION_SETTLE_MILLIS = 500L
    }
}
