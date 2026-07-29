package com.github.radlance.shield.core

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.github.radlance.shield.navigation.core.AppNavHost
import com.github.radlance.shield.subscription.presentation.ImportIntentBus
import com.github.radlance.shield.uikit.theme.core.ThemeViewModel
import com.github.radlance.shield.uikit.theme.ui.ShieldTheme
import com.github.radlance.shield.uikit.theme.ui.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleImportIntent(intent)
        val splashScreen = installSplashScreen()
        val animationReady = MutableStateFlow(false)
        lifecycleScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                delay(700.milliseconds)
            }
            animationReady.value = true
        }

        splashScreen.setKeepOnScreenCondition {
            !animationReady.value
        }

        setContent {
            val navController = rememberNavController()
            val themeViewModel = koinViewModel<ThemeViewModel>()
            val themeConfiguration by themeViewModel.themeConfiguration.collectAsStateWithLifecycle()

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
                    AppNavHost(navController = navController)
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
}
