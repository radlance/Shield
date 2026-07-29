package com.github.radlance.shield.navigation.destination

import android.app.Activity
import android.content.Context
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.github.radlance.shield.core.MainScreen
import com.github.radlance.shield.home.presentation.HomeViewModel

fun NavGraphBuilder.dashGraph(
    navController: NavHostController,
    context: Context,
    homeViewModel: HomeViewModel
) {
    composable<Main> {
        MainScreen(
            homeViewModel = homeViewModel,
            onExit = { (context as? Activity)?.finish() }
        )
    }
}
