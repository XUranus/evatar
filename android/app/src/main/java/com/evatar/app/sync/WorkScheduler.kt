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

    fun cancelSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        Log.i(TAG, "Periodic sync cancelled")
    }

    /**
     * Non-blocking check: uses a simple SharedPreferences flag set by SyncWorker.
     * Actual WorkManager state requires async query; this is a fast approximation.
     */
    fun isScheduled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("evatar_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("sync_scheduled", false)
    }

    fun setScheduled(context: Context, scheduled: Boolean) {
        context.getSharedPreferences("evatar_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("sync_scheduled", scheduled).apply()
    }
}
