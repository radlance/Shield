package com.github.radlance.shield.navigation.destination

import android.app.Activity
import android.content.Context
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.github.radlance.shield.core.MainScreen
import com.github.radlance.shield.timer.presentation.TimerViewModel
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.dashGraph(
    navController: NavHostController,
    context: Context
) {
    composable<Main> {
        val timerViewModel = koinViewModel<TimerViewModel>()

        MainScreen(
            timerViewModel = timerViewModel,
            onExit = { (context as? Activity)?.finish() }
        )
    }
}