package com.github.radlance.shield.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageManagerTest {
    @Test
    fun systemRussianSelectsRussian() {
        assertEquals(
            "ru",
            resolveLocale(Locale.forLanguageTag("ru-RU"), AppLanguage.SYSTEM).language
        )
    }

    @Test
    fun unsupportedSystemLanguageFallsBackToEnglish() {
        assertEquals(
            "en",
            resolveLocale(Locale.forLanguageTag("ar"), AppLanguage.SYSTEM).language
        )
    }

    @Test
    fun explicitSelectionOverridesSystemLanguage() {
        assertEquals(
            "en",
            resolveLocale(Locale.forLanguageTag("ru"), AppLanguage.ENGLISH).language
        )
        assertEquals(
            "ru",
            resolveLocale(Locale.ENGLISH, AppLanguage.RUSSIAN).language
        )
    }
}
