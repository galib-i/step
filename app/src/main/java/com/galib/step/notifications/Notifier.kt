package com.galib.step.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.galib.step.R

class Notifier(private val context: Context) {

    companion object {
        const val CHANNEL_TRACKING = "step_tracking"
    }

    fun ensureChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                context.getString(R.string.notif_channel_tracking),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
    }
}
