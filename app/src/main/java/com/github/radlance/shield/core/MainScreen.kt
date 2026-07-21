package com.github.radlance.shield.core

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.github.radlance.shield.home.presentation.HomeScreen
import com.github.radlance.shield.navigation.bottom.BottomNavBar
import com.github.radlance.shield.navigation.destination.Home
import com.github.radlance.shield.navigation.destination.Settings
import com.github.radlance.shield.timer.presentation.TimerViewModel
import kotlinx.coroutines.launch

private val bottomNavItems = listOf(Home, Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    timerViewModel: TimerViewModel,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val homeIndex = bottomNavItems.indexOf(Home)

    val pagerState = rememberPagerState(
        initialPage = homeIndex,
        pageCount = { bottomNavItems.size }
    )

    val currentScreen by remember {
        derivedStateOf { bottomNavItems[pagerState.targetPage] }
    }

    BackHandler {
        if (pagerState.currentPage != homeIndex) {
            scope.launch {
                pagerState.animateScrollToPage(homeIndex)
            }
        } else {
            onExit()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                currentDestination = currentScreen,
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            BottomNavBar(
                items = bottomNavItems,
                currentScreen = currentScreen,
                onSelected = { screen ->
                    scope.launch {
                        pagerState.animateScrollToPage(bottomNavItems.indexOf(screen))
                    }
                }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            when (bottomNavItems[page]) {
                Home -> HomeScreen(timerViewModel = timerViewModel)

                Settings -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(count = 100) {
                            Text(it.toString())
                        }
                    }
                }
            }
        }
    }
}