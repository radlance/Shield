package com.github.radlance.shield.home.presentation

import android.app.Activity
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.subscription.domain.VlessSecurity
import com.github.radlance.shield.subscription.domain.VlessTransport
import com.github.radlance.shield.subscription.presentation.ImportIntentBus
import com.github.radlance.shield.uikit.tokens.spacing
import com.github.radlance.shield.vpn.domain.VpnConnectionState
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val menuListState = rememberLazyListState()
    var showImportDialog by remember { mutableStateOf(false) }
    var importInitialValue by remember { mutableStateOf("") }
    val permissionDeniedMessage = stringResource(R.string.vpn_permission_denied)

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connectSelected()
        } else {
            scope.launch { snackbarHostState.showSnackbar(permissionDeniedMessage) }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val permission = VpnService.prepare(context)
        if (permission == null) {
            viewModel.connectSelected()
        } else {
            vpnPermissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(Unit) {
        ImportIntentBus.values.collect { value ->
            val it = value ?: return@collect
            importInitialValue = it
            showImportDialog = true
            ImportIntentBus.consume()
        }
    }

    val groups = state.groups.map { group ->
        ServerGroup(
            id = group.subscription.id,
            title = group.subscription.name,
            items = group.profiles.map { profile ->
                ServerItem(
                    id = profile.id,
                    leadingIcon = transportIcon(profile.transport),
                    title = profile.name,
                    description = profileDescription(profile)
                )
            },
            onRefresh = group.subscription.sourceUrl?.let {
                { viewModel.refresh(group.subscription.id) }
            },
            onDelete = { viewModel.delete(group.subscription.id) },
            isRefreshing = group.subscription.id in state.busySubscriptionIds,
            error = group.subscription.lastError
        )
    }

    val isConnected = state.connectionState is VpnConnectionState.Connected
    val isTransitioning = state.connectionState is VpnConnectionState.Connecting ||
        state.connectionState is VpnConnectionState.Reconnecting ||
        state.connectionState is VpnConnectionState.Disconnecting
    val elapsed = rememberElapsedText(state.connectionState)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.fillMaxSize()
            ) {
                ShieldControlBar(
                    isWorking = isConnected,
                    enabled = state.selectedProfileId != null && !isTransitioning,
                    statusText = elapsed,
                    onStartStop = {
                        if (isConnected) {
                            viewModel.disconnect()
                        } else {
                            val needsNotificationPermission =
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                            if (needsNotificationPermission) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val permission = VpnService.prepare(context)
                                if (permission == null) {
                                    viewModel.connectSelected()
                                } else {
                                    vpnPermissionLauncher.launch(permission)
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(MaterialTheme.spacing.m)
                )

                Text(
                    text = connectionLabel(state.connectionState),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.connectionState is VpnConnectionState.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.s)
                )

                ServerList(
                    groups = groups,
                    isLoading = state.isImporting,
                    selectedId = state.selectedProfileId,
                    onServerSelected = viewModel::selectProfile,
                    onPasteFromClipboard = {
                        clipboardText(context)?.let {
                            importInitialValue = it
                            showImportDialog = true
                        }
                    },
                    onQrCodeClick = { startQrScan(context, viewModel, snackbarHostState, scope) },
                    scrollState = scrollState,
                    modifier = Modifier.weight(1f)
                )
            }

            AddMenu(
                listState = menuListState,
                onAddSubscription = {
                    importInitialValue = ""
                    showImportDialog = true
                },
                onPasteFromClipboard = {
                    importInitialValue = clipboardText(context).orEmpty()
                    showImportDialog = true
                },
                onQrCode = { startQrScan(context, viewModel, snackbarHostState, scope) },
                onManualInput = {
                    importInitialValue = "vless://"
                    showImportDialog = true
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = MaterialTheme.spacing.xxl)
            )
        }
    }

    if (showImportDialog) {
        ImportDialog(
            initialValue = importInitialValue,
            importing = state.isImporting,
            onDismiss = { showImportDialog = false },
            onImport = { name, value ->
                viewModel.import(name, value)
                showImportDialog = false
            }
        )
    }
}

@Composable
private fun ImportDialog(
    initialValue: String,
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_subscription)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.subscription_name)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.subscription_url)) },
                    minLines = 3,
                    maxLines = 7
                )
            }
        },
        confirmButton = {
            Button(
                enabled = value.isNotBlank() && !importing,
                onClick = { onImport(name, value) }
            ) {
                if (importing) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun rememberElapsedText(connectionState: VpnConnectionState): String {
    val connected = connectionState as? VpnConnectionState.Connected ?: return "00:00:00"
    var elapsedSeconds by remember(connected.connectedAtElapsedRealtime) { mutableLongStateOf(0L) }
    LaunchedEffect(connected.connectedAtElapsedRealtime) {
        while (true) {
            elapsedSeconds = (SystemClock.elapsedRealtime() - connected.connectedAtElapsedRealtime) / 1_000
            delay(1_000)
        }
    }
    val hours = elapsedSeconds / 3_600
    val minutes = elapsedSeconds % 3_600 / 60
    val seconds = elapsedSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun connectionLabel(state: VpnConnectionState): String = when (state) {
    VpnConnectionState.Disconnected -> "Disconnected"
    VpnConnectionState.PermissionRequired -> "VPN permission required"
    is VpnConnectionState.Connecting -> "Connecting to ${state.profileName}"
    is VpnConnectionState.Connected -> "Connected to ${state.profileName}"
    is VpnConnectionState.Reconnecting -> "Reconnecting to ${state.profileName}"
    VpnConnectionState.Disconnecting -> "Disconnecting"
    is VpnConnectionState.Error -> state.message
}

private fun transportIcon(transport: VlessTransport): String = when (transport) {
    VlessTransport.TCP -> "T"
    VlessTransport.WEBSOCKET -> "W"
    VlessTransport.GRPC -> "G"
}

private fun profileDescription(profile: com.github.radlance.shield.subscription.domain.VlessProfile): String =
    buildString {
        append(profile.server)
        append(':')
        append(profile.port)
        append(" · ")
        append(profile.transport.name)
        if (profile.security != VlessSecurity.NONE) {
            append(" · ")
            append(profile.security.name)
        }
    }

private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}

private fun startQrScan(
    context: Context,
    viewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            barcode.rawValue?.let { viewModel.import("", it) }
        }
        .addOnFailureListener { error ->
            scope.launch {
                snackbarHostState.showSnackbar(error.message ?: "QR scanner is unavailable")
            }
        }
}
