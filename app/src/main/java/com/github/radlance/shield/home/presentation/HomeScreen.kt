package com.github.radlance.shield.home.presentation

import android.app.Activity
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.radlance.shield.R
import com.github.radlance.shield.alerts.domain.AlertsRepository
import com.github.radlance.shield.qr.QrScannerActivity
import com.github.radlance.shield.subscription.domain.SubscriptionAccessStatus
import com.github.radlance.shield.subscription.domain.ProxyProfile
import com.github.radlance.shield.subscription.domain.ProxyProtocol
import com.github.radlance.shield.subscription.domain.VlessSecurity
import com.github.radlance.shield.subscription.domain.VlessTransport
import com.github.radlance.shield.subscription.domain.accessStatus
import com.github.radlance.shield.subscription.presentation.ImportIntentBus
import com.github.radlance.shield.uikit.tokens.spacing
import com.github.radlance.shield.vpn.domain.VpnConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    fabMenuExpanded: Boolean = false,
    fabMenuCanExpand: Boolean = true,
    onFabMenuExpandedChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val alerts = koinInject<AlertsRepository>()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    var showImportDialog by remember { mutableStateOf(false) }
    var importInitialValue by remember { mutableStateOf("") }
    var subscriptionPendingDeletion by remember {
        mutableStateOf<SubscriptionPendingDeletion?>(null)
    }
    val permissionDeniedMessage = stringResource(R.string.vpn_permission_denied)
    val onPasteFromClipboard: () -> Unit = {
        alerts.onFocusChanged()
        clipboardText(context)?.let { viewModel.import("", it) }
    }

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
    val qrScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE)
                ?.let { viewModel.import("", it) }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            onFabMenuExpandedChange(false)
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(Unit) {
        ImportIntentBus.values.collect { value ->
            val it = value ?: return@collect
            onFabMenuExpandedChange(false)
            importInitialValue = it
            showImportDialog = true
            ImportIntentBus.consume()
        }
    }

    val nowEpochSeconds = rememberCurrentEpochSeconds()
    val groups = state.groups.map { group ->
        ServerGroup(
            id = group.subscription.id,
            title = group.subscription.name,
            metadata = group.subscription.metadata,
            accessStatus = group.subscription.metadata.accessStatus(nowEpochSeconds),
            items = group.profiles.map { profile ->
                ServerItem(
                    id = profile.id,
                    leadingIcon = profileIcon(profile),
                    title = profile.name,
                    description = profileDescription(profile),
                    latency = state.serverLatencies[profile.id] ?: ServerLatency.Idle
                )
            },
            onRefresh = group.subscription.sourceUrl?.let {
                {
                    alerts.onFocusChanged()
                    viewModel.refresh(group.subscription.id)
                }
            },
            onPing = {
                alerts.onFocusChanged()
                viewModel.pingSubscription(group.subscription.id)
            },
            onDelete = {
                alerts.onFocusChanged()
                subscriptionPendingDeletion = SubscriptionPendingDeletion(
                    id = group.subscription.id,
                    name = group.subscription.name
                )
            },
            isRefreshing = group.subscription.id in state.busySubscriptionIds,
            isPinging = group.subscription.id in state.pingingSubscriptionIds,
            error = group.subscription.lastError
        )
    }

    val connectedState = state.connectionState as? VpnConnectionState.Connected
    val isConnected = connectedState != null
    val isTransitioning = state.connectionState is VpnConnectionState.Connecting ||
        state.connectionState is VpnConnectionState.Reconnecting ||
        state.connectionState is VpnConnectionState.Disconnecting
    val selectedSubscriptionAvailable = groups
        .firstOrNull { group -> group.items.any { it.id == state.selectedProfileId } }
        ?.accessStatus == SubscriptionAccessStatus.AVAILABLE
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .fillMaxSize()
                    .hideFromAccessibilityIf(fabMenuExpanded)
            ) {
                ShieldControlBar(
                    isWorking = isConnected,
                    enabled = !isTransitioning && (
                        isConnected ||
                            state.selectedProfileId != null && selectedSubscriptionAvailable
                        ),
                    connectedAtElapsedRealtime = connectedState?.connectedAtElapsedRealtime,
                    onStartStop = {
                        alerts.onFocusChanged()
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

                ConnectionStatusText(connectionState = state.connectionState)

                ServerList(
                    groups = groups,
                    isLoading = state.isImporting,
                    selectedId = state.selectedProfileId,
                    onServerSelected = { profileId ->
                        alerts.onFocusChanged()
                        viewModel.selectProfile(profileId)
                    },
                    onPasteFromClipboard = onPasteFromClipboard,
                    onQrCodeClick = {
                        alerts.onFocusChanged()
                        qrScannerLauncher.launch(QrScannerActivity.createIntent(context))
                    },
                    scrollState = scrollState,
                    modifier = Modifier.weight(1f)
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = MaterialTheme.spacing.xxl)
                    .hideFromAccessibilityIf(fabMenuExpanded)
            )

            FabMenuScrim(
                visible = fabMenuExpanded,
                onDismiss = { onFabMenuExpandedChange(false) },
                modifier = Modifier.fillMaxSize(),
                dismissLabel = stringResource(R.string.close_add_menu)
            )

            AddMenu(
                scrollState = scrollState,
                expanded = fabMenuExpanded,
                canExpand = fabMenuCanExpand,
                onExpandedChange = onFabMenuExpandedChange,
                onAddSubscription = {
                    alerts.onFocusChanged()
                    importInitialValue = ""
                    showImportDialog = true
                },
                onPasteFromClipboard = onPasteFromClipboard,
                onQrCode = {
                    alerts.onFocusChanged()
                    qrScannerLauncher.launch(QrScannerActivity.createIntent(context))
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }

    if (showImportDialog) {
        ImportDialog(
            initialValue = importInitialValue,
            importing = state.isImporting,
            onDismiss = { showImportDialog = false },
            onImport = { name, value ->
                alerts.onFocusChanged()
                viewModel.import(name, value)
                showImportDialog = false
            }
        )
    }

    subscriptionPendingDeletion?.let { subscription ->
        DeleteSubscriptionDialog(
            subscriptionName = subscription.name,
            onConfirm = {
                alerts.onFocusChanged()
                subscriptionPendingDeletion = null
                viewModel.delete(subscription.id)
            },
            onDismiss = { subscriptionPendingDeletion = null }
        )
    }
}

private data class SubscriptionPendingDeletion(
    val id: String,
    val name: String
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteSubscriptionDialog(
    subscriptionName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_subscription)) },
        text = {
            Text(
                stringResource(
                    R.string.delete_subscription_confirmation,
                    subscriptionName
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                onClick = { onImport(name, value) },
                shapes = ButtonDefaults.shapes()
            ) {
                if (importing) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.add))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ConnectionStatusText(connectionState: VpnConnectionState) {
    val targetStatus = ConnectionStatus(
        text = connectionLabel(connectionState),
        isError = connectionState is VpnConnectionState.Error
    )
    var displayedStatus by remember { mutableStateOf(targetStatus) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(targetStatus) {
        if (targetStatus != displayedStatus) {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 120,
                    easing = FastOutLinearInEasing
                )
            )
            displayedStatus = targetStatus
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 200,
                    easing = LinearOutSlowInEasing
                )
            )
        }
    }

    Text(
        text = displayedStatus.text,
        style = MaterialTheme.typography.labelLarge,
        color = if (displayedStatus.isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .alpha(alpha.value)
            .padding(bottom = MaterialTheme.spacing.s)
    )
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

private data class ConnectionStatus(
    val text: String,
    val isError: Boolean
)

private fun profileIcon(profile: ProxyProfile): String = when (profile.protocol) {
    ProxyProtocol.VLESS -> when (profile.transport) {
        VlessTransport.TCP -> "V"
        VlessTransport.WEBSOCKET -> "W"
        VlessTransport.GRPC -> "G"
    }
    ProxyProtocol.VMESS -> "M"
    ProxyProtocol.TROJAN -> "T"
    ProxyProtocol.SHADOWSOCKS -> "S"
    ProxyProtocol.HYSTERIA2 -> "H"
    ProxyProtocol.TUIC -> "U"
}

private fun profileDescription(profile: ProxyProfile): String =
    buildString {
        append(profile.server)
        append(':')
        append(profile.port)
        append(" · ")
        append(profile.protocol.name)
        if (profile.protocol == ProxyProtocol.VLESS) {
            append(" · ")
            append(profile.transport.name)
            if (profile.security != VlessSecurity.NONE) {
                append(" · ")
                append(profile.security.name)
            }
        }
    }

private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}

@Composable
private fun rememberCurrentEpochSeconds(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1_000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000.milliseconds)
            now = System.currentTimeMillis() / 1_000
        }
    }
    return now
}
