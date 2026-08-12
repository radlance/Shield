package com.github.radlance.shield.subscription.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class DebugSubscriptionSeederReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                seedDebugSubscriptions(GlobalContext.get().get<SubscriptionRepository>())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
