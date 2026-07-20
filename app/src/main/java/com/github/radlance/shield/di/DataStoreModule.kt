package com.github.radlance.shield.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme")

val dataStoreModule = module {
    single<DataStore<Preferences>> {
        androidContext().themeDataStore
    }
}