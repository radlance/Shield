package com.github.radlance.shield.core

import android.app.Application
import android.content.Context
import android.os.Build
import com.github.radlance.shield.BuildConfig
import com.github.radlance.shield.di.alertModule
import com.github.radlance.shield.di.dataStoreModule
import com.github.radlance.shield.di.themeModule
import com.github.radlance.shield.di.vpnModule
import com.github.radlance.shield.localization.LanguageManager
import com.github.radlance.shield.subscription.data.SubscriptionRefreshWorker
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.util.Locale
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ShieldApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager(base).localizedContext(base))
    }

    override fun onCreate() {
        super.onCreate()

        initializeSingBox()
        startKoin {
            androidLogger()
            androidContext(this@ShieldApp)
            modules(
                dataStoreModule,
                themeModule,
                alertModule,
                vpnModule
            )
        }
        SubscriptionRefreshWorker.schedule(this)
    }

    private fun initializeSingBox() {
        val workingDirectory = getExternalFilesDir(null) ?: filesDir
        Libbox.setup(
            SetupOptions().apply {
                basePath = filesDir.absolutePath
                workingPath = workingDirectory.absolutePath
                tempPath = cacheDir.absolutePath
                fixAndroidStack = BuildConfig.DEBUG ||
                    Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.N_MR1 ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                logMaxLines = 1_000
                debug = BuildConfig.DEBUG
            }
        )
        Libbox.setLocale(Locale.getDefault().toLanguageTag())
    }
}
