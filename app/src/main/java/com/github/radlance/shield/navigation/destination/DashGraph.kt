package com.github.radlance.shield.navigation.destination

import android.app.Activity
import android.content.Context
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.github.radlance.shield.core.MainScreen
import com.github.radlance.shield.diagnostics.presentation.DiagnosticsScreen
import com.github.radlance.shield.settings.about.AboutScreen
import com.github.radlance.shield.home.presentation.HomeViewModel
import com.github.radlance.shield.settings.appearance.AppearanceScreen
import org.koin.compose.viewmodel.koinViewModel
import com.github.radlance.shield.uikit.theme.core.ThemeViewModel

fun NavGraphBuilder.dashGraph(
    navController: NavHostController,
    context: Context,
    homeViewModel: HomeViewModel
) {
    composable<Main> {
        MainScreen(
            homeViewModel = homeViewModel,
            onAppearance = { navController.navigate(Appearance) },
            onDiagnostics = { navController.navigate(Diagnostics) },
            onAbout = { navController.navigate(About) },
            onExit = { (context as? Activity)?.finish() }
        )
    }

    composable<Appearance> {
        AppearanceScreen(
            themeViewModel = koinViewModel<ThemeViewModel>(),
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable<Diagnostics> {
        DiagnosticsScreen(onBack = { navController.popBackStack() })
    }

    composable<About> {
        AboutScreen(onBack = { navController.popBackStack() })
    }
}
