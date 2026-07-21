package com.github.radlance.shield.di

import com.github.radlance.shield.uikit.theme.core.ThemeViewModel
import com.github.radlance.shield.uikit.theme.data.BaseThemeDataStore
import com.github.radlance.shield.uikit.theme.data.LocalThemeRepository
import com.github.radlance.shield.uikit.theme.data.ThemeDataStore
import com.github.radlance.shield.uikit.theme.domain.ThemeRepository
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val themeModule = module {
    single<ThemeDataStore> {
        BaseThemeDataStore(
            dataStore = get(named("theme"))
        )
    }
    singleOf(::LocalThemeRepository).bind<ThemeRepository>()
    viewModelOf(::ThemeViewModel)
}