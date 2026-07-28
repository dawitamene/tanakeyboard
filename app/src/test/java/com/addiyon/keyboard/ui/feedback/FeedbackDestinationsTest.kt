package com.addiyon.keyboard.ui.feedback

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackDestinationsTest {

    @Test
    fun emailUriIncludesFeedbackRecipient() {
        assertEquals(
            "mailto:keyboard@addiyon.com",
            feedbackEmailUri()
        )
    }

    @Test
    fun telegramLinksUseSupportAccount() {
        assertEquals("addiyonsupport", TELEGRAM_USERNAME)
        assertEquals(
            "tg://resolve?domain=addiyonsupport",
            feedbackTelegramDeepLink()
        )
        assertEquals(
            "https://t.me/addiyonsupport",
            feedbackTelegramWebLink()
        )
    }
}
