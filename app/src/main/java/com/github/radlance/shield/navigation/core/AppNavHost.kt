package com.github.radlance.shield.navigation.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.github.radlance.shield.home.presentation.HomeViewModel
import com.github.radlance.shield.navigation.destination.Main
import com.github.radlance.shield.navigation.destination.dashGraph

@Composable
fun AppNavHost(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Main,
        enterTransition = { defaultEnterTransition() },
        exitTransition = { defaultExitTransition() },
        popEnterTransition = { defaultPopEnterTransition() },
        popExitTransition = { defaultPopExitTransition() },
        modifier = modifier
    ) {
        dashGraph(
            context = context,
            homeViewModel = homeViewModel
        )
    }
}
