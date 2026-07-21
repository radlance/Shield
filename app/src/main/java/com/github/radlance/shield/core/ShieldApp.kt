package com.github.radlance.shield.core

import android.app.Application
import com.github.radlance.shield.di.alertModule
import com.github.radlance.shield.di.dataStoreModule
import com.github.radlance.shield.di.themeModule
import com.github.radlance.shield.di.timerModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@ShieldApp)
            modules(
                dataStoreModule,
                themeModule,
                timerModule,
                alertModule
            )
        }
    }
}