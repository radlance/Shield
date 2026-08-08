package com.github.radlance.shield.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import com.github.radlance.shield.R
import com.github.radlance.shield.uikit.theme.core.ThemeViewModel
import com.github.radlance.shield.uikit.tokens.spacing
import com.github.radlance.shield.settings.appearance.components.AmoledThemeSelector
import com.github.radlance.shield.settings.appearance.components.FontSelector
import com.github.radlance.shield.settings.appearance.components.ThemeColorSelector
import com.github.radlance.shield.settings.appearance.components.ThemeModeSelector

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceScreen(
    themeViewModel: ThemeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(stringResource(R.string.appearance))
                        Text(stringResource(R.string.appearance_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        val navigationPadding = WindowInsets.navigationBars.asPaddingValues()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = scaffoldPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = MaterialTheme.spacing.m + scaffoldPadding.calculateStartPadding(layoutDirection),
                end = MaterialTheme.spacing.m + scaffoldPadding.calculateEndPadding(layoutDirection),
                bottom = navigationPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ThemeModeSelector(themeViewModel) }
            item { AmoledThemeSelector(themeViewModel) }
            item { FontSelector(themeViewModel) }
            item { ThemeColorSelector(themeViewModel) }
        }
    }
}
