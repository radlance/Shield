package com.github.radlance.shield.di

import com.github.radlance.shield.diagnostics.DiagnosticLog
import com.github.radlance.shield.home.presentation.HomeViewModel
import com.github.radlance.shield.subscription.data.AndroidSecretCipher
import com.github.radlance.shield.subscription.data.LocalSubscriptionRepository
import com.github.radlance.shield.subscription.data.SecretCipher
import com.github.radlance.shield.subscription.data.VlessProfileParser
import com.github.radlance.shield.subscription.domain.ProfileParser
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import com.github.radlance.shield.vpn.data.AndroidVpnController
import com.github.radlance.shield.vpn.data.SingBoxConfigGenerator
import com.github.radlance.shield.vpn.data.DataStoreVpnStateStore
import com.github.radlance.shield.vpn.data.VpnStateStore
import com.github.radlance.shield.vpn.domain.VpnController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val vpnModule = module {
    single<ProfileParser> { VlessProfileParser() }
    single<SecretCipher> { AndroidSecretCipher() }
    single<SubscriptionRepository> {
        LocalSubscriptionRepository(
            context = androidContext(),
            parser = get(),
            cipher = get()
        )
    }
    single { SingBoxConfigGenerator() }
    single { DiagnosticLog() }
    single<VpnStateStore> {
        DataStoreVpnStateStore(dataStore = get(named("vpn_state")))
    }
    single<VpnController> { AndroidVpnController(androidContext()) }
    viewModelOf(::HomeViewModel)
}
