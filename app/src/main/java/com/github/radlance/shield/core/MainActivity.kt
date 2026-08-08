package com.github.radlance.shield.core

import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.github.radlance.shield.home.presentation.HomeViewModel
import com.github.radlance.shield.localization.LanguageManager
import com.github.radlance.shield.navigation.core.AppNavHost
import com.github.radlance.shield.subscription.presentation.ImportIntentBus
import com.github.radlance.shield.uikit.theme.core.ThemeViewModel
import com.github.radlance.shield.uikit.theme.ui.ShieldTheme
import com.github.radlance.shield.uikit.theme.ui.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager(base).localizedContext(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleImportIntent(intent)
        val splashScreen = installSplashScreen()
        val splashStartedAt = SystemClock.elapsedRealtime()
        val startupReady = AtomicBoolean(false)
        lifecycleScope.launch {
            delay(MAX_SPLASH_DURATION_MILLIS.milliseconds)
            startupReady.set(true)
        }

        splashScreen.setKeepOnScreenCondition {
            !startupReady.get()
        }

        setContent {
            val navController = rememberNavController()
            val themeViewModel = koinViewModel<ThemeViewModel>()
            val homeViewModel = koinViewModel<HomeViewModel>()
            val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
            val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val themeConfiguration = themeState.configuration

            LaunchedEffect(themeState.isInitialized, homeState.isInitialized) {
                if (themeState.isInitialized && homeState.isInitialized) {
                    val elapsed = SystemClock.elapsedRealtime() - splashStartedAt
                    val minimumDuration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ANIMATED_SPLASH_DURATION_MILLIS
                    } else {
                        0L
                    }
                    val remaining = minimumDuration - elapsed
                    if (remaining > 0) delay(remaining.milliseconds)
                    startupReady.set(true)
                }
            }

            val darkTheme = when (themeConfiguration.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            enableEdgeToEdge(
                statusBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(scrim = Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(scrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT)
                },
                navigationBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(scrim = Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(scrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT)
                }
            )

            ShieldTheme(themeConfiguration = themeConfiguration) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost(
                        navController = navController,
                        homeViewModel = homeViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: Intent?) {
        val value = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        value?.let(ImportIntentBus::offer)
    }

    private companion object {
        const val ANIMATED_SPLASH_DURATION_MILLIS = 700L
        const val MAX_SPLASH_DURATION_MILLIS = 2_000L
    }
}
