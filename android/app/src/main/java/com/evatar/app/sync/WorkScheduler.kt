package com.evatar.app.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val TAG = "WorkScheduler"
    private const val UNIQUE_WORK_NAME = "evatar_sync"

    /**
     * Enqueue a periodic sync job that runs every 30 minutes
     * when the device has an active network connection.
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.i(TAG, "Periodic sync scheduled (every 30 min, requires network)")
    }

    /**
     * Cancel the periodic sync job.
     */
    fun cancelSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        Log.i(TAG, "Periodic sync cancelled")
    }

    /**
     * Check whether the periodic sync job is currently scheduled.
     */
    fun isScheduled(context: Context): Boolean {
        val info = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
        return info.any { !it.state.isFinished }
    }
}
