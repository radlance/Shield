package com.github.radlance.shield.subscription.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.radlance.shield.subscription.domain.SubscriptionRepository
import java.util.concurrent.TimeUnit
import org.koin.core.context.GlobalContext

class SubscriptionRefreshWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = GlobalContext.get().get<SubscriptionRepository>()
        val results = repository.refreshAll()
        return if (results.any { it.isFailure }) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "subscription-refresh-daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
