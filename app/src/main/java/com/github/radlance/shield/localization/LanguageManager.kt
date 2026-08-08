package com.github.radlance.shield.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.content.edit
import java.util.Locale

class LanguageManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val selectedLanguage: AppLanguage
        get() = preferences.getString(LANGUAGE_KEY, null)
            ?.let { value -> runCatching { AppLanguage.valueOf(value) }.getOrNull() }
            ?: AppLanguage.SYSTEM

    fun setLanguage(language: AppLanguage) {
        preferences.edit { putString(LANGUAGE_KEY, language.name) }
    }

    fun localizedContext(base: Context): Context {
        val locale = resolveLocale()
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun resolveLocale(): Locale {
        val systemLocale = Resources.getSystem().configuration.locales[0]
        return resolveLocale(systemLocale, selectedLanguage)
    }

    private companion object {
        const val PREFERENCES_NAME = "language"
        const val LANGUAGE_KEY = "selected_language"
    }
}

internal fun resolveLocale(systemLocale: Locale, language: AppLanguage): Locale = when (language) {
    AppLanguage.SYSTEM -> if (systemLocale.language == "ru") {
        Locale.forLanguageTag("ru")
    } else {
        Locale.ENGLISH
    }
    AppLanguage.ENGLISH -> Locale.ENGLISH
    AppLanguage.RUSSIAN -> Locale.forLanguageTag("ru")
}
