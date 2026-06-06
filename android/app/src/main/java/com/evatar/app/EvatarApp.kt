package com.evatar.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class EvatarApp : Application() {

    companion object {
        const val CHANNEL_SYNC = "evatar_sync"
        const val CHANNEL_KEEPALIVE = "evatar_keepalive"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        com.evatar.app.sync.WorkScheduler.schedulePeriodicSync(this)
    }

    private fun createNotificationChannels() {
        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            getString(R.string.notification_sync_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_sync_channel)
        }

        val keepaliveChannel = NotificationChannel(
            CHANNEL_KEEPALIVE,
            getString(R.string.notification_keepalive_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_keepalive_channel)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(syncChannel)
        manager.createNotificationChannel(keepaliveChannel)
    }
}
