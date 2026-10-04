package com.example.betterweather.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Keeps the cache warm in the background so opening the app / tile / complication is instant. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ok = WeatherRepository.get(applicationContext).refresh(force = false)
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(online).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("bw_periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Used by the tile and complications when they notice stale data. */
        fun refreshSoon(context: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(online).build()
            WorkManager.getInstance(context).enqueueUniqueWork("bw_now", ExistingWorkPolicy.KEEP, req)
        }
    }
}
