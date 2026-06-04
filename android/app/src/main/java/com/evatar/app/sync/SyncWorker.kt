package com.evatar.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
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
