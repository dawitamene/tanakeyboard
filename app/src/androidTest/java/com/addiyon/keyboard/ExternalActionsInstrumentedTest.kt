package com.addiyon.keyboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.addiyon.keyboard.ui.feedback.FEEDBACK_EMAIL
import com.addiyon.keyboard.ui.feedback.feedbackTelegramDeepLink
import com.addiyon.keyboard.ui.feedback.feedbackTelegramWebLink
import com.addiyon.keyboard.ui.feedback.openFeedbackTelegram
import com.addiyon.keyboard.ui.feedback.sendFeedbackEmail
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalActionsInstrumentedTest {
    @Test
    fun privacyPolicyUsesBrowsableViewIntentWithoutResolverPreflight() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)

        assertTrue(
            ExternalActions.openPrivacyPolicy(
                context,
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )

        assertNotNull(context.startedIntent)
        val intent = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(ExternalActions.PRIVACY_POLICY_URL, intent.dataString)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun feedbackEmailLaunchesDirectlyWithRecipientAndSubject() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val subject = "Addiyon Keyboard feedback"

        sendFeedbackEmail(
            context,
            subject,
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        assertNotNull(context.startedIntent)
        val intent = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:$FEEDBACK_EMAIL", intent.dataString)
        assertArrayEquals(
            arrayOf(FEEDBACK_EMAIL),
            intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
        )
        assertEquals(subject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun telegramLaunchesDeepLinkWithoutResolverPreflight() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)

        openFeedbackTelegram(context, Intent.FLAG_ACTIVITY_NEW_TASK)

        assertNotNull(context.startedIntent)
        val intent = requireNotNull(context.startedIntent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(feedbackTelegramDeepLink(), intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun telegramFallsBackDirectlyToWebWhenAppLaunchFails() {
        val context = TelegramFallbackContext(
            InstrumentationRegistry.getInstrumentation().targetContext
        )

        openFeedbackTelegram(context, Intent.FLAG_ACTIVITY_NEW_TASK)

        assertEquals(
            listOf(feedbackTelegramDeepLink(), feedbackTelegramWebLink()),
            context.startedIntents.map { it.dataString }
        )
        assertTrue(
            context.startedIntents.all {
                it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0
            }
        )
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null

        override fun getPackageManager(): PackageManager =
            throw AssertionError("External actions must launch without resolver preflight")

        override fun startActivity(intent: Intent) {
            startedIntent = intent
        }
    }

    private class TelegramFallbackContext(base: Context) : ContextWrapper(base) {
        val startedIntents = mutableListOf<Intent>()

        override fun getPackageManager(): PackageManager =
            throw AssertionError("External actions must launch without resolver preflight")

        override fun startActivity(intent: Intent) {
            startedIntents += intent
            if (intent.data?.scheme == "tg") {
                throw ActivityNotFoundException()
            }
        }
    }
}
