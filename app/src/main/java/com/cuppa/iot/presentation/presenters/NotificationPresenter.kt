package com.cuppa.iot.presentation.presenters

import com.cuppa.iot.domain.models.Notification
import com.cuppa.iot.domain.usecases.NotificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Presenter for the Notification screen.
 * Responsible for fetching and organizing user notifications, and listening to coffee events
 * to generate and save new notifications.
 */
class NotificationPresenter(
    private val view: NotificationView,
    private val useCase: NotificationUseCase
) {

    private val scope = CoroutineScope(Dispatchers.Main) // Coroutine scope operating on the Main dispatcher for UI updates
    private val jobs = mutableListOf<Job>() // keep track of listeners

    // Constants for time comparison to group notifications
    private val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private val WEEK_MILLIS = 7 * DAY_MILLIS

    /**
     * Starts listening to all required real-time streams: user notifications, coffee status, and temperature.
     */
    fun startListening() {
        // 1. Listen to stored user notifications for the list display
        jobs.add(scope.launch {
            useCase.listenToUserNotifications().collectLatest { notifications ->
                val now = System.currentTimeMillis()

                // Filter and group notifications into "Today" (less than 24h old)
                val today = notifications.filter {
                    val timeDiff = now - it.timestamp
                    timeDiff < DAY_MILLIS
                }

                // Filter and group notifications into "This Week" (24h to 7 days old)
                val week = notifications.filter {
                    val timeDiff = now - it.timestamp
                    timeDiff in DAY_MILLIS until WEEK_MILLIS
                }

                // Update the View with the categorized lists
                view.showTodayNotifications(today)
                view.showWeekNotifications(week)
            }
        })

        // 2. Listen to the coffee status path for event generation
        jobs.add(scope.launch { useCase.listenToCoffeeStatus().collectLatest { handleCoffeeEvent(it) } })

        // 3. Listen to the temperature path for event generation (e.g., 'coffee_cold')
        jobs.add(scope.launch { useCase.listenToPlateTemperature().collectLatest { handleCoffeeEvent(it) } })
    }

    // ✅ Clear all ongoing listeners
    fun clearAllListeners() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    /**
     * Processes a real-time event string from the coffee machine (status or temperature-based)
     * and decides whether to save a persistent notification and/or show an instant alert.
     */
    private fun handleCoffeeEvent(eventType: String) {
        // Map the event string to a user-friendly title and message
        val (title, message) = when (eventType) {
            // Mapping Firebase/DataSource events to Notification data
            "done" -> "Your Coffee Is Ready!" to "Enjoy your fresh brew ☕"
            "coffee_cold" -> "Coffee Gone Cold" to "Get your coffee before it gets cold! :3"
            "started" -> "Brewing Started" to "Your scheduled brew is now running."
            "paused" -> "Brewing Paused" to "The brewing process has been paused."
            "resumed" -> "Brewing Resumed" to "The brewing process has resumed."
            "cancelled" -> "Brewing Cancelled" to "The brew was cancelled."
            "offline" -> "Device Offline" to "The coffee maker is offline."
            else -> return // Ignore unmapped events
        }

        // Trigger instant visual feedback in the View (e.g., a Toast or Snackbar)
        view.showInstantNotification(eventType)

        // Only save 'done' and 'coffee_cold' for persistent history (as per original logic)
        if (eventType == "done" || eventType == "coffee_cold") {
            val notif = Notification(
                title = title,
                message = message,
                timestamp = System.currentTimeMillis(),
                category = "Today"
            )

            // Asynchronously add the new notification to the database
            scope.launch {
                useCase.addNotification(notif)
            }
        }
    }

    /**
     * Handles clicking on a notification, which marks it as read/deleted in the database.
     */
    fun onNotificationClicked(notification: Notification) {
        scope.launch {
            useCase.markAsRead(notification.id)
        }
    }

    /**
     * Deletes a single notification (alias for markAsRead).
     */
    fun deleteNotification(notification: Notification) {
        scope.launch {
            useCase.markAsRead(notification.id)
        }
    }

    /**
     * Iterates through a list of notifications and deletes them all.
     */
    fun clearAllNotifications(allNotifications: List<Notification>) {
        scope.launch {
            allNotifications.forEach { notification ->
                useCase.markAsRead(notification.id)
            }
        }
    }

}

/**
 * View contract for the Notification screen.
 * Defines methods the Presenter uses to update the UI list and show instant feedback.
 */
interface NotificationView {
    fun showTodayNotifications(list: List<Notification>)
    fun showWeekNotifications(list: List<Notification>)
    fun showError(message: String)
    fun showInstantNotification(eventType: String)
}
