package com.evatar.app.sync

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.evatar.app.EvatarApp
import com.evatar.app.MainActivity
import com.evatar.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncService : Service() {

    companion object {
        private const val TAG = "SyncService"
        private const val NOTIFICATION_ID = 1001
        private const val SYNC_INTERVAL_MS = 60_000L  // check every 60s

        fun start(context: Context) {
            val intent = Intent(context, SyncService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        syncManager = SyncManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("正在同步..."))

        scope.launch {
            while (isActive) {
                try {
                    if (syncManager.apiClient.checkHealth()) {
                        val result = syncManager.runSync { synced, failed, total ->
                            // Update notification with progress
                            val text = if (total > 0) {
                                "已同步 $synced/$total${if (failed > 0) " ($failed 失败)" else ""}"
                            } else {
                                "无新截图"
                            }
                            updateNotification(text)
                        }
                        updateNotification(
                            if (result.total > 0) "同步完成: ${result.success}/${result.total}"
                            else "等待新截图..."
                        )
                    } else {
                        updateNotification("等待服务端连接...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync loop error", e)
                    updateNotification("同步异常")
                }
                delay(SYNC_INTERVAL_MS)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, EvatarApp.CHANNEL_SYNC)
            .setContentTitle("Evatar")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        startForeground(NOTIFICATION_ID, buildNotification(text))
    }
}
