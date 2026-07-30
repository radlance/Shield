package com.github.radlance.shield.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.diagnostics.DiagnosticLog
import com.github.radlance.shield.home.presentation.HomeViewModel
import com.github.radlance.shield.uikit.tokens.spacing
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val log = koinInject<DiagnosticLog>()
    val routingViewModel = koinViewModel<RoutingSettingsViewModel>()
    val lines by log.lines.collectAsStateWithLifecycle()
    val routingState by routingViewModel.uiState.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.share_logs)
    var editingDirectDomains by rememberSaveable { mutableStateOf<Boolean?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.m),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.m)
    ) {
        Button(
            onClick = homeViewModel::refreshAll,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Text(
                text = stringResource(R.string.refresh_subscriptions),
                modifier = Modifier.padding(start = MaterialTheme.spacing.s)
            )
        }

        Text(
            text = stringResource(R.string.routing),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    routingViewModel.setSmartRussianRouting(
                        !routingState.smartRussianRouting
                    )
                }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.smart_russian_routing),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.smart_russian_routing_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = routingState.smartRussianRouting,
                onCheckedChange = routingViewModel::setSmartRussianRouting
            )
        }
        OutlinedButton(
            onClick = { editingDirectDomains = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(
                    R.string.always_direct_domains_count,
                    routingState.forceDirectDomains.size
                )
            )
        }
        OutlinedButton(
            onClick = { editingDirectDomains = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(
                    R.string.always_proxy_domains_count,
                    routingState.forceProxyDomains.size
                )
            )
        }
        Text(
            text = stringResource(R.string.routing_reconnect_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.diagnostics),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = lines.takeLast(100).joinToString("\n")
                .ifBlank { stringResource(R.string.no_diagnostics) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            enabled = lines.isNotEmpty(),
            onClick = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Shield diagnostics")
                            putExtra(Intent.EXTRA_TEXT, log.export())
                        },
                        shareTitle
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Text(
                text = stringResource(R.string.share_logs),
                modifier = Modifier.padding(start = MaterialTheme.spacing.s)
            )
        }

        Text(
            text = stringResource(R.string.about_shield),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "sing-box 1.13.12 · GPL-3.0-or-later",
            style = MaterialTheme.typography.labelMedium
        )
    }

    editingDirectDomains?.let { direct ->
        DomainOverridesDialog(
            direct = direct,
            initialDomains = if (direct) {
                routingState.forceDirectDomains
            } else {
                routingState.forceProxyDomains
            },
            onSave = { domains ->
                if (direct) {
                    routingViewModel.setForceDirectDomains(domains)
                } else {
                    routingViewModel.setForceProxyDomains(domains)
                }
                editingDirectDomains = null
            },
            onDismiss = { editingDirectDomains = null }
        )
    }
}

@Composable
private fun DomainOverridesDialog(
    direct: Boolean,
    initialDomains: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable(direct, initialDomains) {
        mutableStateOf(initialDomains.sorted().joinToString("\n"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (direct) R.string.always_direct_domains
                    else R.string.always_proxy_domains
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)) {
                Text(
                    text = stringResource(R.string.domain_overrides_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.domains)) },
                    minLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        value.split('\n', ',', ';')
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .toSet()
                    )
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
