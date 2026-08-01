package com.github.radlance.shield.vpn

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.radlance.shield.R
import com.github.radlance.shield.core.MainActivity
import com.github.radlance.shield.diagnostics.DiagnosticLog
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.subscription.domain.VlessProfile
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.android.inject
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
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
    private var activeConfig: String? = null
    private var connectionJob: Job? = null
    private var reloadJob: Job? = null
    private val reloadMutex = Mutex()
    private val reloadGeneration = AtomicLong()
    private var interfaceListener: InterfaceUpdateListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val networkMonitorGeneration = AtomicLong()
    private val observedNetworks = ConcurrentHashMap.newKeySet<Network>()
    @Volatile
    private var underlyingNetwork: Network? = null
    private var reportedNetwork: Network? = null
    private var reportedInterfaceName: String = ""
    private var reportedInterfaceIndex: Int = -1
    private val localDnsTransport by lazy {
        AndroidLocalDnsTransport(applicationContext) { findUnderlyingNetwork() }
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
        reloadGeneration.incrementAndGet()
        val previousReloadJob = reloadJob
        previousReloadJob?.cancel()
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
                previousReloadJob?.join()
                previousJob?.join()
                closeCore()
                activeProfileId = null
                _connectionState.value = VpnConnectionState.Connecting(profile.name)
                val prepared = prepareConfig(profile)
                underlyingNetwork = findUnderlyingNetwork()
                    ?: error("No usable physical network is available")
                val server = CommandServer(this@ShieldVpnService, this@ShieldVpnService)
                startingServer = server
                commandServer = server
                currentCoroutineContext().ensureActive()
                server.start()
                server.startOrReloadService(
                    prepared.config,
                    OverrideOptions()
                )
                currentCoroutineContext().ensureActive()
                activeConfig = prepared.config
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
                if (prepared.smartRouting) {
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
        reloadGeneration.incrementAndGet()
        val previousReloadJob = reloadJob
        previousReloadJob?.cancel()
        _connectionState.value = VpnConnectionState.Disconnecting
        val previousJob = connectionJob
        previousJob?.cancel()
        activeProfileId = null
        connectionJob = scope.launch {
            previousReloadJob?.join()
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
        publishUnderlyingNetwork(null)
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        activeConfig = null
        closeTun()
    }

    private suspend fun prepareConfig(profile: VlessProfile): PreparedConfig {
        val settings = routingSettingsRepository.settings.first()
        val ruleSetPaths = if (settings.smartRussianRouting) {
            routingRuleSetProvider.prepareRuleSets()
        } else {
            null
        }
        return PreparedConfig(
            config = configGenerator.generate(
                profile = profile,
                routing = VpnRoutingConfig(
                    ruleSetPaths = ruleSetPaths,
                    forceDirectDomains = settings.forceDirectDomains,
                    forceProxyDomains = settings.forceProxyDomains
                )
            ),
            smartRouting = settings.smartRussianRouting
        )
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
            reportedNetwork = null
            reportedInterfaceName = ""
            reportedInterfaceIndex = -1
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
        val network = underlyingNetwork?.takeIf(::isUnderlyingNetwork)
            ?: findUnderlyingNetwork()
            ?: error("No usable physical network is available")
        underlyingNetwork = network
        val descriptor = builder.establish()
            ?: error("Android refused to establish the VPN interface")
        tunDescriptor = descriptor
        publishUnderlyingNetwork(network)
        return descriptor.fd
    }

    override fun readWIFIState(): WIFIState? = null

    override fun sendNotification(notification: Notification?) {
        notification?.body?.takeIf(String::isNotBlank)?.let(diagnosticLog::record)
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        if (listener == null) return
        if (interfaceListener !== listener) {
            reportedNetwork = null
            reportedInterfaceName = ""
            reportedInterfaceIndex = -1
        }
        interfaceListener = listener
        updateDefaultInterface(
            underlyingNetwork?.takeIf(::isUnderlyingNetwork) ?: findUnderlyingNetwork()
        )
        if (networkCallback == null) {
            val generation = networkMonitorGeneration.incrementAndGet()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (generation != networkMonitorGeneration.get()) return
                    if (isUnderlyingNetwork(network)) {
                        observedNetworks.add(network)
                        updateDefaultInterface(selectUnderlyingNetwork())
                    }
                }

                override fun onLost(network: Network) {
                    if (generation != networkMonitorGeneration.get()) return
                    observedNetworks.remove(network)
                    if (network == underlyingNetwork) {
                        underlyingNetwork = null
                        updateDefaultInterface(selectUnderlyingNetwork())
                    }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (generation != networkMonitorGeneration.get()) return
                    if (isUnderlyingNetwork(network)) {
                        observedNetworks.add(network)
                    } else {
                        observedNetworks.remove(network)
                    }
                    if (network == underlyingNetwork || observedNetworks.contains(network)) {
                        updateDefaultInterface(selectUnderlyingNetwork())
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
        val generation = reloadGeneration.incrementAndGet()
        reloadJob?.cancel()
        reloadJob = scope.launch {
            delay(RELOAD_DEBOUNCE_MILLIS.milliseconds)
            reloadMutex.withLock {
                currentCoroutineContext().ensureActive()
                if (generation != reloadGeneration.get()) return@withLock
                val connected = _connectionState.value as? VpnConnectionState.Connected
                    ?: return@withLock
                val profileId = activeProfileId ?: return@withLock
                val profile = repository.getProfile(profileId) ?: return@withLock
                val previousConfig = activeConfig ?: return@withLock

                val prepared = runCatching { prepareConfig(profile) }
                    .getOrElse { error ->
                        diagnosticLog.record(
                            "Routing reload preparation failed: ${error.message.orEmpty()}"
                        )
                        _connectionState.value = connected
                        return@withLock
                    }
                currentCoroutineContext().ensureActive()
                if (generation != reloadGeneration.get()) {
                    _connectionState.value = connected
                    return@withLock
                }
                if (prepared.config == previousConfig) {
                    return@withLock
                }

                _connectionState.value = VpnConnectionState.Reconnecting(connected.profileName)
                diagnosticLog.record(
                    "Reloading routing configuration: smart=${prepared.smartRouting}"
                )
                val server = commandServer ?: run {
                    _connectionState.value = connected
                    return@withLock
                }
                underlyingNetwork = findUnderlyingNetwork()
                    ?: run {
                        diagnosticLog.record("Routing reload postponed: physical network unavailable")
                        _connectionState.value = connected
                        return@withLock
                    }
                publishUnderlyingNetwork(underlyingNetwork)
                val reloadError = runCatching {
                    server.startOrReloadService(prepared.config, OverrideOptions())
                }.exceptionOrNull()
                if (
                    generation != reloadGeneration.get() ||
                    activeProfileId != profileId
                ) return@withLock
                if (reloadError == null) {
                    activeConfig = prepared.config
                    _connectionState.value = connected
                    diagnosticLog.record("Routing configuration applied")
                    return@withLock
                }

                diagnosticLog.record(
                    "Routing reload failed; restoring previous configuration"
                )
                val rollbackError = runCatching {
                    server.startOrReloadService(previousConfig, OverrideOptions())
                }.exceptionOrNull()
                if (rollbackError == null) {
                    activeConfig = previousConfig
                    _connectionState.value = connected
                    diagnosticLog.record("Previous routing configuration restored")
                } else {
                    diagnosticLog.record("Routing rollback failed")
                    closeCore()
                    activeProfileId = null
                    fail(reloadError.message ?: "Unable to reload routing configuration")
                }
            }
        }
    }

    override fun serviceStop() {
        closeTun()
        runCatching { commandServer?.closeService() }
    }

    override fun setSystemProxyEnabled(enabled: Boolean) = Unit
    override fun writeDebugMessage(message: String?) {
        message?.let { debugMessage ->
            diagnosticLog.record(debugMessage)
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                Log.d(DEBUG_LOG_TAG, debugMessage)
            }
        }
    }

    private fun updateDefaultInterface(network: Network?) {
        underlyingNetwork = network
        if (tunDescriptor != null) publishUnderlyingNetwork(network)
        val listener = interfaceListener ?: return
        val name = network
            ?.let(connectivity::getLinkProperties)
            ?.interfaceName
            .orEmpty()
        val index = name.takeIf(String::isNotBlank)
            ?.let { runCatching { NetworkInterface.getByName(it).index }.getOrDefault(-1) }
            ?: -1
        if (
            network == reportedNetwork &&
            name == reportedInterfaceName &&
            index == reportedInterfaceIndex
        ) return
        reportedNetwork = network
        reportedInterfaceName = name
        reportedInterfaceIndex = index
        diagnosticLog.record(
            if (name.isBlank()) "Physical network unavailable" else "Physical network changed: $name"
        )
        listener.updateDefaultInterface(name, index, false, false)
        commandServer?.resetNetwork()
        val connected = _connectionState.value as? VpnConnectionState.Connected
        if (connected != null) {
            _connectionState.value = VpnConnectionState.Reconnecting(connected.profileName)
            _connectionState.value = connected
        }
    }

    private fun unregisterNetworkCallback() {
        networkMonitorGeneration.incrementAndGet()
        networkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        networkCallback = null
        observedNetworks.clear()
    }

    private fun publishUnderlyingNetwork(network: Network?) {
        runCatching { setUnderlyingNetworks(network?.let(::arrayOf)) }
            .onSuccess { published ->
                if (!published && network != null) {
                    diagnosticLog.record("Android rejected the physical network association")
                }
            }
            .onFailure { diagnosticLog.record("Unable to publish underlying network") }
    }

    private fun closeTun() {
        val descriptor = tunDescriptor
        tunDescriptor = null
        runCatching { descriptor?.close() }
    }

    private fun registerUnderlyingNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val handler = Handler(Looper.getMainLooper())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connectivity.registerNetworkCallback(request, callback, handler)
        } else {
            connectivity.registerNetworkCallback(request, callback)
        }
    }

    private fun findUnderlyingNetwork(): Network? {
        underlyingNetwork?.takeIf(::isUnderlyingNetwork)?.let { return it }
        val active = connectivity.activeNetwork
        if (active != null && isUnderlyingNetwork(active)) return active
        return selectUnderlyingNetwork()
    }

    private fun selectUnderlyingNetwork(): Network? {
        val candidates = buildList {
            observedNetworks.forEach { network ->
                if (isUnderlyingNetwork(network)) add(network)
            }
            connectivity.activeNetwork
                ?.takeIf(::isUnderlyingNetwork)
                ?.let { if (!contains(it)) add(it) }
            underlyingNetwork
                ?.takeIf(::isUnderlyingNetwork)
                ?.let { if (!contains(it)) add(it) }
        }
        if (candidates.isEmpty()) return null
        val best = candidates.maxByOrNull(::networkScore) ?: return null
        val current = underlyingNetwork?.takeIf(candidates::contains)
        return if (current != null && networkScore(current) >= networkScore(best)) current else best
    }

    private fun networkScore(network: Network): Int {
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return Int.MIN_VALUE
        return (if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100 else 0) +
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 30
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 20
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 10
                else -> 0
            }
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

    private data class PreparedConfig(
        val config: String,
        val smartRouting: Boolean
    )

    companion object {
        const val ACTION_CONNECT = "com.github.radlance.shield.action.CONNECT"
        const val ACTION_DISCONNECT = "com.github.radlance.shield.action.DISCONNECT"
        const val ACTION_RELOAD = "com.github.radlance.shield.action.RELOAD"
        const val ACTION_SWITCH_PROFILE = "com.github.radlance.shield.action.SWITCH_PROFILE"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val NOTIFICATION_CHANNEL = "shield_vpn"
        private const val NOTIFICATION_ID = 10
        private const val RELOAD_DEBOUNCE_MILLIS = 250L
        private const val DEBUG_LOG_TAG = "ShieldCore"

        private val _connectionState = MutableStateFlow<VpnConnectionState>(VpnConnectionState.Disconnected)
        val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()
    }
}
