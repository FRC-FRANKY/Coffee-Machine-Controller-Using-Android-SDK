package com.cuppa.iot.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cuppa.iot.R
import android.Manifest

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "cuppa_brew_channel"
        private const val CHANNEL_NAME = "Coffee Brewing"
        private const val NOTIFICATION_ID_BREW_DONE = 1
        private const val NOTIFICATION_ID_COLD = 2
        private const val NOTIFICATION_ID_OFFLINE = 3
    }

    init {
        createNotificationChannel()
    }

    // 💡 NEW: Permission check function
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API 33)
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission is granted implicitly on older versions
            true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for coffee brewing status"
                enableVibration(true)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun showBrewDoneNotification() {
        if (!hasNotificationPermission()) return // 💡 FIX: Check permission before notifying

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Add your icon
            .setContentTitle("Coffee Ready ☕")
            .setContentText("Your coffee is ready!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BREW_DONE, notification)
    }

    fun showCoffeeGoneColdNotification() {
        if (!hasNotificationPermission()) return // 💡 FIX: Check permission before notifying

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Coffee Cold ❄️")
            .setContentText("Your coffee has gone cold")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COLD, notification)
    }

    fun showOfflineNotification() {
        if (!hasNotificationPermission()) return // 💡 FIX: Check permission before notifying

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ESP32 Offline ⚠️")
            .setContentText("Coffee maker is offline")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_OFFLINE, notification)
    }
}