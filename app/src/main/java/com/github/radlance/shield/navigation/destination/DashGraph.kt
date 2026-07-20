package com.github.radlance.shield.navigation.destination

import android.app.Activity
import android.content.Context
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.github.radlance.shield.core.MainScreen

fun NavGraphBuilder.dashGraph(
    navController: NavHostController,
    context: Context
) {
    composable<Main> {
        MainScreen(
            onExit = { (context as? Activity)?.finish() }
        )
    }
}