package com.github.radlance.shield.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.diagnostics.DiagnosticLog
import com.github.radlance.shield.home.presentation.HomeViewModel
import com.github.radlance.shield.uikit.tokens.spacing
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val log = koinInject<DiagnosticLog>()
    val lines by log.lines.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.share_logs)

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
}
