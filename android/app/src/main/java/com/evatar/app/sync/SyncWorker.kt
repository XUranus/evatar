package com.evatar.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "evatar_periodic_sync"

        fun enqueue(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "Periodic sync work enqueued")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Periodic sync work cancelled")
        }

        fun isScheduled(context: Context): Boolean {
            val info = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME).get()
            return info.any { !it.state.isFinished }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val syncManager = SyncManager(applicationContext)
            val connected = syncManager.apiClient.checkHealth()
            if (!connected) {
                Log.w(TAG, "Server not connected, will retry")
                return Result.retry()
            }

            val result = syncManager.runSync()
            Log.i(TAG, "Sync completed: ${result.success}/${result.total} (${result.failed} failed)")

            if (result.failed > 0 && result.success == 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }
}
