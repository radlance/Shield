package com.github.radlance.shield.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.alerts.domain.AlertsRepository
import com.github.radlance.shield.home.presentation.HomeViewModel
import com.github.radlance.shield.settings.components.SettingsListItem
import com.github.radlance.shield.settings.components.SettingsSectionHeader
import com.github.radlance.shield.settings.components.SettingsSwitchItem
import com.github.radlance.shield.uikit.tokens.spacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    onAppearance: () -> Unit,
    onDiagnostics: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val routingViewModel = koinViewModel<RoutingSettingsViewModel>()
    val routingState by routingViewModel.uiState.collectAsStateWithLifecycle()
    val alerts = koinInject<AlertsRepository>()
    val alertConfiguration by alerts.alertConfiguration.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editingDirectDomains by rememberSaveable { mutableStateOf<Boolean?>(null) }

    fun feedback() = alerts.onFocusChanged()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MaterialTheme.spacing.m,
            end = MaterialTheme.spacing.m,
            bottom = MaterialTheme.spacing.s
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)
    ) {
        item { SettingsSectionHeader(Icons.Rounded.Refresh, stringResource(R.string.subscriptions)) }
        item {
            SettingsListItem(
                title = stringResource(R.string.refresh_subscriptions),
                subtitle = stringResource(R.string.refresh_subscriptions_description),
                leadingIcon = Icons.Rounded.Refresh,
                index = 0,
                totalCount = 1,
                showChevron = false,
                onClick = { feedback(); homeViewModel.refreshAll() }
            )
        }

        item { SettingsSectionHeader(Icons.Rounded.Route, stringResource(R.string.routing)) }
        item {
            SettingsSwitchItem(
                title = stringResource(R.string.smart_russian_routing),
                subtitle = stringResource(R.string.smart_russian_routing_description),
                checked = routingState.smartRussianRouting,
                index = 0,
                totalCount = 3,
                onCheckedChange = { enabled -> feedback(); routingViewModel.setSmartRussianRouting(enabled) },
                onClick = { feedback(); routingViewModel.setSmartRussianRouting(!routingState.smartRussianRouting) }
            )
        }
        item {
            SettingsListItem(
                title = stringResource(R.string.always_direct_domains),
                subtitle = stringResource(R.string.always_direct_domains_count, routingState.forceDirectDomains.size),
                leadingIcon = Icons.Rounded.Dns,
                index = 1,
                totalCount = 3,
                showChevron = false,
                onClick = { feedback(); editingDirectDomains = true }
            )
        }
        item {
            SettingsListItem(
                title = stringResource(R.string.always_proxy_domains),
                subtitle = stringResource(R.string.always_proxy_domains_count, routingState.forceProxyDomains.size),
                leadingIcon = Icons.Rounded.Block,
                index = 2,
                totalCount = 3,
                showChevron = false,
                onClick = { feedback(); editingDirectDomains = false }
            )
        }
        item {
            Text(stringResource(R.string.routing_reconnect_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = MaterialTheme.spacing.xs, bottom = MaterialTheme.spacing.s))
        }

        item { SettingsSectionHeader(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.audio_haptics)) }
        item {
            SettingsSwitchItem(
                title = stringResource(R.string.haptic_feedback),
                subtitle = stringResource(R.string.haptic_feedback_description),
                checked = alertConfiguration.hapticsEnabled,
                index = 0,
                totalCount = 1,
                onCheckedChange = { enabled ->
                    if (alertConfiguration.hapticsEnabled) feedback()
                    scope.launch { alerts.setHapticsEnabled(enabled) }
                },
                onClick = {
                    val enabled = !alertConfiguration.hapticsEnabled
                    if (alertConfiguration.hapticsEnabled) feedback()
                    scope.launch { alerts.setHapticsEnabled(enabled) }
                }
            )
        }

        item { SettingsSectionHeader(Icons.Rounded.Troubleshoot, stringResource(R.string.diagnostics)) }
        item {
            SettingsListItem(
                title = stringResource(R.string.diagnostics),
                subtitle = stringResource(R.string.open_diagnostics),
                leadingIcon = Icons.Rounded.Troubleshoot,
                index = 0,
                totalCount = 1,
                onClick = { feedback(); onDiagnostics() }
            )
        }

        item { SettingsSectionHeader(Icons.Rounded.Palette, stringResource(R.string.appearance)) }
        item {
            SettingsListItem(
                title = stringResource(R.string.appearance),
                subtitle = stringResource(R.string.open_appearance),
                leadingIcon = Icons.Rounded.Palette,
                index = 0,
                totalCount = 1,
                onClick = { feedback(); onAppearance() }
            )
        }

        item { SettingsSectionHeader(Icons.Rounded.Info, stringResource(R.string.about)) }
        item {
            SettingsListItem(
                title = stringResource(R.string.about_app),
                subtitle = stringResource(R.string.about_shield),
                leadingIcon = Icons.Rounded.Info,
                index = 0,
                totalCount = 1,
                onClick = { feedback(); onAbout() }
            )
        }
    }

    editingDirectDomains?.let { direct ->
        DomainOverridesDialog(
            direct = direct,
            initialDomains = if (direct) routingState.forceDirectDomains else routingState.forceProxyDomains,
            onSave = { domains ->
                feedback()
                if (direct) routingViewModel.setForceDirectDomains(domains) else routingViewModel.setForceProxyDomains(domains)
                editingDirectDomains = null
            },
            onDismiss = { editingDirectDomains = null }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DomainOverridesDialog(
    direct: Boolean,
    initialDomains: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable(direct, initialDomains) { mutableStateOf(initialDomains.sorted().joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (direct) R.string.always_direct_domains else R.string.always_proxy_domains)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)) {
                Text(stringResource(R.string.domain_overrides_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(stringResource(R.string.domains)) }, minLines = 5, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.split('\n', ',', ';').map(String::trim).filter(String::isNotEmpty).toSet()) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } }
    )
}
