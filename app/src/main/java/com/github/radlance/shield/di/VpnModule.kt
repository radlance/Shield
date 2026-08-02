package com.github.radlance.shield.di

import com.github.radlance.shield.diagnostics.DiagnosticLog
import com.github.radlance.shield.home.presentation.HomeViewModel
import com.github.radlance.shield.subscription.data.AndroidSecretCipher
import com.github.radlance.shield.subscription.data.AndroidSubscriptionDownloader
import com.github.radlance.shield.subscription.data.LocalSubscriptionRepository
import com.github.radlance.shield.subscription.data.SecretCipher
import com.github.radlance.shield.subscription.data.SubscriptionDownloader
import com.github.radlance.shield.subscription.data.VlessProfileParser
import com.github.radlance.shield.subscription.domain.ProfileParser
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.vpn.data.AndroidServerLatencyTester
import com.github.radlance.shield.vpn.data.AndroidVpnController
import com.github.radlance.shield.vpn.data.DataStoreVpnStateStore
import com.github.radlance.shield.vpn.data.PhysicalNetworkMonitor
import com.github.radlance.shield.vpn.data.SingBoxConfigGenerator
import com.github.radlance.shield.vpn.data.VpnStateStore
import com.github.radlance.shield.vpn.domain.ServerLatencyTester
import com.github.radlance.shield.vpn.domain.VpnController
import com.github.radlance.shield.vpn.routing.DataStoreRoutingSettingsRepository
import com.github.radlance.shield.vpn.routing.RoutingRuleSetProvider
import com.github.radlance.shield.vpn.routing.RoutingSettingsRepository
import com.github.radlance.shield.settings.RoutingSettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val vpnModule = module {
    single<ProfileParser> { VlessProfileParser() }
    single<SecretCipher> { AndroidSecretCipher() }
    single<SubscriptionDownloader> { AndroidSubscriptionDownloader(androidContext()) }
    single<SubscriptionRepository> {
        LocalSubscriptionRepository(
            context = androidContext(),
            parser = get(),
            cipher = get(),
            downloader = get()
        )
    }
    single { SingBoxConfigGenerator() }
    single { DiagnosticLog() }
    single<VpnStateStore> {
        DataStoreVpnStateStore(dataStore = get(named("vpn_state")))
    }
    single<VpnController> { AndroidVpnController(androidContext()) }
    single { PhysicalNetworkMonitor(androidContext()) }
    single<ServerLatencyTester> {
        AndroidServerLatencyTester(
            context = androidContext(),
            physicalNetworkMonitor = get(),
            vpnController = get()
        )
    }
    single { RoutingRuleSetProvider(androidContext()) }
    single<RoutingSettingsRepository> {
        DataStoreRoutingSettingsRepository(get(named("routing")))
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::RoutingSettingsViewModel)
}
