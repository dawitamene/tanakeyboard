package com.addiyon.keyboard

import android.app.Activity
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
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
            scenario.recreate()
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
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
