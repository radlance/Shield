package com.github.radlance.shield.vpn

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.radlance.shield.R
import com.github.radlance.shield.core.MainActivity
import com.github.radlance.shield.diagnostics.DiagnosticLog
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.vpn.data.InterfaceAddressFormatter
import com.github.radlance.shield.vpn.data.SingBoxConfigGenerator
import com.github.radlance.shield.vpn.data.VpnRoutingConfig
import com.github.radlance.shield.vpn.data.VpnStateStore
import com.github.radlance.shield.vpn.domain.VpnConnectionState
import com.github.radlance.shield.vpn.routing.RoutingRuleSetProvider
import com.github.radlance.shield.vpn.routing.RoutingSettingsRepository
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.net.InetAddress
import java.net.NetworkInterface
import android.app.Notification as AndroidNotification
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

class ShieldVpnService :
    VpnService(),
    PlatformInterface,
    CommandServerHandler {

    private val repository by inject<SubscriptionRepository>()
    private val configGenerator by inject<SingBoxConfigGenerator>()
    private val diagnosticLog by inject<DiagnosticLog>()
    private val vpnStateStore by inject<VpnStateStore>()
    private val routingSettingsRepository by inject<RoutingSettingsRepository>()
    private val routingRuleSetProvider by inject<RoutingRuleSetProvider>()
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var commandServer: CommandServer? = null
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var activeProfileId: String? = null
    private var connectionJob: Job? = null
    private var interfaceListener: InterfaceUpdateListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var underlyingNetwork: Network? = null
    private val localDnsTransport by lazy {
        AndroidLocalDnsTransport(applicationContext) { underlyingNetwork }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> disconnect()
            ACTION_RELOAD -> serviceReload()
            ACTION_SWITCH_PROFILE -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId != null) switchProfile(profileId)
            }
            ACTION_CONNECT -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId != null) connect(profileId)
            }
            else -> {
                scope.launch {
                    runCatching { vpnStateStore.getActiveProfileId() }
                        .onSuccess { profileId ->
                            profileId?.let(::connect) ?: stopSelf(startId)
                        }
                        .onFailure { error ->
                            diagnosticLog.record(
                                "Unable to restore VPN state: ${error.message.orEmpty()}"
                            )
                            stopSelf(startId)
                        }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onRevoke() {
        diagnosticLog.record("VPN permission was revoked")
        disconnect()
    }

    override fun onDestroy() {
        closeCore()
        scope.cancel()
        super.onDestroy()
    }

    private fun connect(
        profileId: String,
        force: Boolean = false,
        persistSelection: Boolean = false
    ) {
        if (prepare(this) != null) {
            _connectionState.value = VpnConnectionState.PermissionRequired
            stopSelf()
            return
        }
        if (
            !force &&
            activeProfileId == profileId &&
            _connectionState.value is VpnConnectionState.Connected
        ) return
        if (persistSelection) {
            _connectionState.value = VpnConnectionState.Disconnecting
        }
        val previousJob = connectionJob
        previousJob?.cancel()
        if (persistSelection) {
            closeCore()
            activeProfileId = null
        }
        startForeground(NOTIFICATION_ID, buildServiceNotification(getString(R.string.notification_connecting)))
        connectionJob = scope.launch {
            var startingServer: CommandServer? = null
            try {
                val profile = repository.getProfile(profileId)
                if (profile == null) {
                    fail("Selected profile no longer exists")
                    return@launch
                }
                if (persistSelection) {
                    repository.selectProfile(profile.id)
                }
                previousJob?.join()
                closeCore()
                activeProfileId = null
                _connectionState.value = VpnConnectionState.Connecting(profile.name)
                val routingSettings = routingSettingsRepository.settings.first()
                val ruleSetPaths = if (routingSettings.smartRussianRouting) {
                    runCatching { routingRuleSetProvider.prepareRuleSets() }
                        .onFailure { error ->
                            diagnosticLog.record(
                                "Smart routing disabled: ${error.message.orEmpty()}"
                            )
                        }
                        .getOrNull()
                } else {
                    null
                }
                underlyingNetwork = findUnderlyingNetwork()
                val server = CommandServer(this@ShieldVpnService, this@ShieldVpnService)
                startingServer = server
                commandServer = server
                currentCoroutineContext().ensureActive()
                server.start()
                server.startOrReloadService(
                    configGenerator.generate(
                        profile = profile,
                        routing = VpnRoutingConfig(
                            ruleSetPaths = ruleSetPaths,
                            forceDirectDomains = routingSettings.forceDirectDomains,
                            forceProxyDomains = routingSettings.forceProxyDomains
                        )
                    ),
                    OverrideOptions()
                )
                currentCoroutineContext().ensureActive()
                activeProfileId = profile.id
                runCatching { vpnStateStore.setActiveProfileId(profile.id) }
                    .onFailure { error ->
                        diagnosticLog.record(
                            "Unable to persist VPN state: ${error.message.orEmpty()}"
                        )
                    }
                _connectionState.value = VpnConnectionState.Connected(
                    profileId = profile.id,
                    profileName = profile.name,
                    connectedAtElapsedRealtime = SystemClock.elapsedRealtime()
                )
                diagnosticLog.record("Connected through ${profile.name}")
                if (ruleSetPaths != null) {
                    scope.launch {
                        runCatching { routingRuleSetProvider.refreshRuleSets() }
                            .onSuccess { result ->
                                if (result.updated) {
                                    diagnosticLog.record(
                                        "Routing databases updated; they will be used on next reconnect"
                                    )
                                }
                                if (result.failedDownloads > 0) {
                                    diagnosticLog.record(
                                        "Unable to update ${result.failedDownloads} routing databases; " +
                                            "using the last working copies"
                                    )
                                }
                            }
                            .onFailure { error ->
                                diagnosticLog.record(
                                    "Routing database update failed: ${error.message.orEmpty()}"
                                )
                            }
                    }
                }
                notifyConnected(profile.name)
            } catch (error: CancellationException) {
                if (commandServer === startingServer) {
                    closeCore()
                }
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (startingServer == null || commandServer === startingServer) {
                    closeCore()
                }
                fail(error.message ?: "Unable to start sing-box")
            }
        }
    }

    private fun switchProfile(profileId: String) {
        if (
            activeProfileId == profileId &&
            _connectionState.value is VpnConnectionState.Connected
        ) return
        connect(profileId, force = true, persistSelection = true)
    }

    private fun disconnect() {
        _connectionState.value = VpnConnectionState.Disconnecting
        val previousJob = connectionJob
        previousJob?.cancel()
        activeProfileId = null
        connectionJob = scope.launch {
            previousJob?.join()
            closeCore()
            clearPersistedProfile()
            _connectionState.value = VpnConnectionState.Disconnected
            diagnosticLog.record("VPN disconnected")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun closeCore() {
        unregisterNetworkCallback()
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
    }

    private suspend fun fail(message: String) {
        diagnosticLog.record("VPN error: $message")
        _connectionState.value = VpnConnectionState.Error(message)
        clearPersistedProfile()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun clearPersistedProfile() {
        runCatching { vpnStateStore.setActiveProfileId(null) }
            .onFailure { error ->
                diagnosticLog.record(
                    "Unable to clear VPN state: ${error.message.orEmpty()}"
                )
            }
    }

    private fun buildServiceNotification(content: String): AndroidNotification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ShieldVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.shield_24px)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.disconnect), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    getString(R.string.notification_channel_vpn),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun notifyConnected(profileName: String) {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildServiceNotification(getString(R.string.notification_connected, profileName))
            )
        }
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        check(protect(fd)) { "Unable to protect sing-box socket from VPN routing" }
    }

    override fun clearDNSCache() = Unit

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        if (interfaceListener === listener) {
            interfaceListener = null
            unregisterNetworkCallback()
        }
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String?,
        sourcePort: Int,
        destinationAddress: String?,
        destinationPort: Int
    ): ConnectionOwner = throw UnsupportedOperationException("Process routing is not enabled")

    override fun getInterfaces(): NetworkInterfaceIterator {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList().map { networkInterface ->
            LibboxNetworkInterface().apply {
                index = networkInterface.index
                name = networkInterface.name
                mtu = runCatching { networkInterface.mtu }.getOrDefault(1500)
                addresses = StringList(
                    networkInterface.interfaceAddresses.map { address ->
                        InterfaceAddressFormatter.format(
                            hostAddress = address.address.hostAddress.orEmpty(),
                            prefixLength = address.networkPrefixLength.toInt()
                        )
                    }
                )
                flags = (if (networkInterface.isUp) OsConstants.IFF_UP else 0) or
                    (if (networkInterface.isLoopback) OsConstants.IFF_LOOPBACK else 0) or
                    (if (networkInterface.isPointToPoint) OsConstants.IFF_POINTOPOINT else 0) or
                    (if (networkInterface.supportsMulticast()) OsConstants.IFF_MULTICAST else 0)
                type = interfaceType(networkInterface.name)
                dnsServer = StringList(emptyList())
                metered = false
            }
        }
        return NetworkInterfaceList(interfaces)
    }

    override fun includeAllNetworks(): Boolean = false
    override fun localDNSTransport(): LocalDNSTransport = localDnsTransport

    override fun openTun(options: TunOptions): Int {
        check(prepare(this) == null) { "VPN permission is missing" }
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(options.mtu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val hasInet4Address = addAddresses(builder, options.inet4Address)
        val hasInet6Address = addAddresses(builder, options.inet6Address)
        if (options.autoRoute) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!addRoutes(builder, options.inet4RouteAddress) && hasInet4Address) {
                    builder.addRoute("0.0.0.0", 0)
                }
                if (!addRoutes(builder, options.inet6RouteAddress) && hasInet6Address) {
                    builder.addRoute("::", 0)
                }
                addExcludedRoutes(builder, options.inet4RouteExcludeAddress)
                addExcludedRoutes(builder, options.inet6RouteExcludeAddress)
            } else {
                if (!addRoutes(builder, options.inet4RouteRange) && hasInet4Address) {
                    builder.addRoute("0.0.0.0", 0)
                }
                if (!addRoutes(builder, options.inet6RouteRange) && hasInet6Address) {
                    builder.addRoute("::", 0)
                }
            }
            options.dnsServerAddress?.value?.takeIf(String::isNotBlank)?.let(builder::addDnsServer)
        }
        val descriptor = builder.establish() ?: error("Android refused to establish the VPN interface")
        tunDescriptor = descriptor
        return descriptor.fd
    }

    override fun readWIFIState(): WIFIState? = null

    override fun sendNotification(notification: Notification?) {
        notification?.body?.takeIf(String::isNotBlank)?.let(diagnosticLog::record)
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        if (listener == null) return
        interfaceListener = listener
        updateDefaultInterface(
            underlyingNetwork?.takeIf(::isUnderlyingNetwork) ?: findUnderlyingNetwork()
        )
        if (networkCallback == null) {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (isUnderlyingNetwork(network)) updateDefaultInterface(network)
                }

                override fun onLost(network: Network) {
                    if (network == underlyingNetwork) updateDefaultInterface(findUnderlyingNetwork())
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (network == underlyingNetwork && isUnderlyingNetwork(network)) {
                        updateDefaultInterface(network)
                    }
                }
            }
            networkCallback = callback
            registerUnderlyingNetworkCallback(callback)
        }
    }

    override fun systemCertificates(): StringIterator? = null
    override fun underNetworkExtension(): Boolean = false
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun useProcFS(): Boolean = false

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().apply {
        available = false
        enabled = false
    }

    override fun serviceReload() {
        activeProfileId?.let { profileId -> connect(profileId, force = true) }
    }

    override fun serviceStop() {
        disconnect()
    }

    override fun setSystemProxyEnabled(enabled: Boolean) = Unit
    override fun writeDebugMessage(message: String?) {
        message?.let(diagnosticLog::record)
    }

    private fun updateDefaultInterface(network: Network?) {
        underlyingNetwork = network
        val listener = interfaceListener ?: return
        val name = network
            ?.let(connectivity::getLinkProperties)
            ?.interfaceName
            .orEmpty()
        val index = name.takeIf(String::isNotBlank)
            ?.let { runCatching { NetworkInterface.getByName(it).index }.getOrDefault(-1) }
            ?: -1
        listener.updateDefaultInterface(name, index, false, false)
        commandServer?.resetNetwork()
        val connected = _connectionState.value as? VpnConnectionState.Connected
        if (connected != null) {
            _connectionState.value = VpnConnectionState.Reconnecting(connected.profileName)
            _connectionState.value = connected
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun registerUnderlyingNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val handler = Handler(Looper.getMainLooper())
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                connectivity.registerBestMatchingNetworkCallback(request, callback, handler)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                connectivity.requestNetwork(request, callback, handler)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                connectivity.registerDefaultNetworkCallback(callback, handler)
            else ->
                connectivity.registerDefaultNetworkCallback(callback)
        }
    }

    private fun findUnderlyingNetwork(): Network? {
        underlyingNetwork?.takeIf(::isUnderlyingNetwork)?.let { return it }
        val active = connectivity.activeNetwork
        if (active != null && isUnderlyingNetwork(active)) return active
        return null
    }

    private fun isUnderlyingNetwork(network: Network): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun addAddresses(
        builder: Builder,
        iterator: RoutePrefixIterator
    ): Boolean {
        var added = false
        while (iterator.hasNext()) {
            val prefix = iterator.next()
            builder.addAddress(prefix.address(), prefix.prefix())
            added = true
        }
        return added
    }

    private fun addRoutes(
        builder: Builder,
        iterator: RoutePrefixIterator
    ): Boolean {
        var added = false
        while (iterator.hasNext()) {
            val prefix = iterator.next()
            builder.addRoute(prefix.address(), prefix.prefix())
            added = true
        }
        return added
    }

    private fun addExcludedRoutes(builder: Builder, iterator: RoutePrefixIterator) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        while (iterator.hasNext()) {
            val prefix = iterator.next()
            builder.excludeRoute(
                IpPrefix(InetAddress.getByName(prefix.address()), prefix.prefix())
            )
        }
    }

    private fun interfaceType(name: String): Int = when {
        name.startsWith("wlan") || name.startsWith("wifi") -> Libbox.InterfaceTypeWIFI
        name.startsWith("rmnet") || name.startsWith("ccmni") -> Libbox.InterfaceTypeCellular
        name.startsWith("eth") -> Libbox.InterfaceTypeEthernet
        else -> Libbox.InterfaceTypeOther
    }

    private class StringList(private val values: List<String>) : StringIterator {
        private val iterator = values.iterator()
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun len(): Int = values.size
        override fun next(): String = iterator.next()
    }

    private class NetworkInterfaceList(
        interfaces: List<LibboxNetworkInterface>
    ) : NetworkInterfaceIterator {
        private val iterator = interfaces.iterator()
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    companion object {
        const val ACTION_CONNECT = "com.github.radlance.shield.action.CONNECT"
        const val ACTION_DISCONNECT = "com.github.radlance.shield.action.DISCONNECT"
        const val ACTION_RELOAD = "com.github.radlance.shield.action.RELOAD"
        const val ACTION_SWITCH_PROFILE = "com.github.radlance.shield.action.SWITCH_PROFILE"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val NOTIFICATION_CHANNEL = "shield_vpn"
        private const val NOTIFICATION_ID = 10

        private val _connectionState = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()
    }
}
