package com.evatar.app.keepalive

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.evatar.app.EvatarApp
import com.evatar.app.R
import com.evatar.app.MainActivity
import com.evatar.app.network.ApiClient
import kotlinx.coroutines.*

class KeepAliveService : LifecycleService() {

    companion object {
        private const val TAG = "KeepAliveService"
        private const val NOTIFICATION_ID = 1002
        private const val CHECK_INTERVAL = 30_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, KeepAliveService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }

    private var overlayWindow: OverlayWindow? = null
    private var statusJob: Job? = null
    private lateinit var apiClient: ApiClient

    override fun onCreate() {
        super.onCreate()
        apiClient = ApiClient.getInstance(this)
        overlayWindow = OverlayWindow(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.keepalive_running)))

        try { overlayWindow?.show() } catch (e: Exception) { Log.e(TAG, "Overlay show failed", e) }

        // Cancel previous loop
        statusJob?.cancel()
        statusJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val connected = apiClient.checkHealth()
                    // Post UI update to main thread
                    withContext(Dispatchers.Main) {
                        overlayWindow?.updateStatus(if (connected) getString(R.string.keepalive_connected) else getString(R.string.keepalive_disconnected))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { overlayWindow?.updateStatus(getString(R.string.keepalive_error)) }
                }
                delay(CHECK_INTERVAL)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        statusJob?.cancel()
        overlayWindow?.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): android.os.IBinder? {
        super.onBind(intent)
        return null
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, EvatarApp.CHANNEL_KEEPALIVE)
            .setContentTitle("Evatar").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi).setOngoing(true).build()
    }
}
