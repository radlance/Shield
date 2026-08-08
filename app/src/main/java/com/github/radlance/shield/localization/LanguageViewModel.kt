package com.github.radlance.shield.localization

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LanguageViewModel(
    private val languageManager: LanguageManager
) : ViewModel() {
    private val _selectedLanguage = MutableStateFlow(languageManager.selectedLanguage)
    val selectedLanguage: StateFlow<AppLanguage> = _selectedLanguage

    fun setLanguage(language: AppLanguage) {
        languageManager.setLanguage(language)
        _selectedLanguage.value = language
    }
}
