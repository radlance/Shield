package com.github.radlance.shield.core

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.radlance.shield.home.presentation.FabMenuScrim
import com.github.radlance.shield.home.presentation.HomeScreen
import com.github.radlance.shield.home.presentation.HomeViewModel
import com.github.radlance.shield.home.presentation.hideFromAccessibilityIf
import com.github.radlance.shield.navigation.bottom.BottomNavBar
import com.github.radlance.shield.navigation.destination.Home
import com.github.radlance.shield.navigation.destination.Settings
import com.github.radlance.shield.settings.SettingsScreen
import kotlinx.coroutines.launch

private val bottomNavItems = listOf(Home, Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    homeViewModel: HomeViewModel,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var fabMenuExpanded by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) fabMenuExpanded = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val homeIndex = bottomNavItems.indexOf(Home)

    val pagerState = rememberPagerState(
        initialPage = homeIndex,
        pageCount = { bottomNavItems.size }
    )

    val currentScreen by remember {
        derivedStateOf { bottomNavItems[pagerState.targetPage] }
    }
    val homePageSettled by remember {
        derivedStateOf {
            pagerState.settledPage == homeIndex && !pagerState.isScrollInProgress
        }
    }

    LaunchedEffect(homePageSettled) {
        if (!homePageSettled) fabMenuExpanded = false
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
            Box {
                TopAppBar(
                    currentDestination = currentScreen,
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.hideFromAccessibilityIf(fabMenuExpanded)
                )
                FabMenuScrim(
                    visible = fabMenuExpanded,
                    onDismiss = { fabMenuExpanded = false },
                    modifier = Modifier.matchParentSize()
                )
            }
        },
        bottomBar = {
            Box {
                BottomNavBar(
                    items = bottomNavItems,
                    currentScreen = currentScreen,
                    onSelected = { screen ->
                        scope.launch {
                            pagerState.animateScrollToPage(bottomNavItems.indexOf(screen))
                        }
                    },
                    modifier = Modifier.hideFromAccessibilityIf(fabMenuExpanded)
                )
                FabMenuScrim(
                    visible = fabMenuExpanded,
                    onDismiss = { fabMenuExpanded = false },
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !fabMenuExpanded
        ) { page ->
            when (bottomNavItems[page]) {
                Home -> HomeScreen(
                    viewModel = homeViewModel,
                    fabMenuExpanded = fabMenuExpanded,
                    fabMenuCanExpand = homePageSettled,
                    onFabMenuExpandedChange = { fabMenuExpanded = it }
                )

                Settings -> SettingsScreen(
                    homeViewModel = homeViewModel,
                    modifier = Modifier.hideFromAccessibilityIf(fabMenuExpanded)
                )
            }
        }
    }
}
