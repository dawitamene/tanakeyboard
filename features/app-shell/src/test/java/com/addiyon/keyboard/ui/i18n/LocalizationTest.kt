package com.addiyon.keyboard.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalizationTest {
    @Test
    fun appLanguageEntriesDefineExpectedCodesAndLabels() {
        assertEquals("en", AppLanguage.ENGLISH.code)
        assertEquals("English", AppLanguage.ENGLISH.label)
        assertEquals("am", AppLanguage.AMHARIC.code)
        assertEquals("አማርኛ", AppLanguage.AMHARIC.label)
    }

    @Test
    fun controllerTogglesBetweenEnglishAndAmharic() {
        var currentLang = AppLanguage.ENGLISH
        val controller = object : AppLanguageController {
            override val current: AppLanguage get() = currentLang
            override fun set(language: AppLanguage) { currentLang = language }
            override fun toggle() {
                set(if (current == AppLanguage.ENGLISH) AppLanguage.AMHARIC else AppLanguage.ENGLISH)
            }
        }
        assertEquals(AppLanguage.ENGLISH, controller.current)
        controller.toggle()
        assertEquals(AppLanguage.AMHARIC, controller.current)
        controller.toggle()
        assertEquals(AppLanguage.ENGLISH, controller.current)
    }
}
