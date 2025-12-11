package com.cuppa.iot.data.remote

import android.util.Log
import com.cuppa.iot.domain.models.Notification
import com.cuppa.iot.domain.repositories.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed implementation of NotificationRepository.
 * Handles fetching user notifications and monitoring coffee status/temperature to generate
 * or process specific notifications.
 */
class FirebaseNotificationDataSource(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : NotificationRepository {

    private val activeListeners = mutableMapOf<DatabaseReference, ValueEventListener>()

    private val coldThresholdCelsius = 24.0 // Threshold temperature (in Celsius) below which a "coffee cold" notification is triggered
    private var isColdNotifSent = false // Flag to ensure the "coffee cold" notification is only sent once per cold cycle

    /**
     * Gets the database reference for the current user's notifications.
     * Returns null if no user is logged in.
     */
    private fun getUserNotificationsRef(): DatabaseReference? {
        val uid = auth.currentUser?.uid ?: return null
        return database.getReference("users/$uid/notifications")
    }

    /**
     * Returns a Flow that continuously listens for and emits the list of a user's notifications.
     */
    override fun listenToUserNotifications(): Flow<List<Notification>> = callbackFlow {
        val ref = getUserNotificationsRef()
        // If no user is logged in, send an empty list and close the flow immediately
        if (ref == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // ValueEventListener to fetch and map notifications to the domain model
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Maps the children snapshots to a list of Notification objects, reversing the order (newest first)
                val list = snapshot.children.reversed().mapNotNull { child ->
                    child.getValue(Notification::class.java)?.copy(id = child.key ?: "")
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                // ✅ FIX: Handle expected PERMISSION_DENIED error during logout gracefully
                if (error.code == DatabaseError.PERMISSION_DENIED) {
                    // Log or handle the expected cancellation without crashing
                    Log.d("FirebaseNotif", "Listener for notifications cancelled gracefully on logout.")
                    close() // Close the channel without throwing the exception
                } else {
                    // For any other, unexpected error, propagate the exception
                    close(error.toException())
                }
            }
        }

        ref.addValueEventListener(listener)
        // ✅ FIX 2: Add listener to the tracking map
        activeListeners[ref] = listener

        awaitClose {
            ref.removeEventListener(listener)
            // ✅ Optional: Remove from tracking map on flow closure
            activeListeners.remove(ref, listener)
        }
    }

    /**
     * Returns a Flow that continuously listens for and emits the current coffee machine status (e.g., "idle", "brewing").
     */
    override fun listenToCoffeeStatus(): Flow<String> = callbackFlow {
        val ref = database.getReference("coffee/status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Emits the status string if it's not blank
                snapshot.getValue(String::class.java)?.let { status ->
                    if (status.isNotBlank()) trySend(status)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)
        // ✅ FIX 2: Add listener to the tracking map
        activeListeners[ref] = listener

        awaitClose {
            ref.removeEventListener(listener)
            activeListeners.remove(ref, listener)
        }
    }

    /**
     * Returns a Flow that listens for and emits a special "coffee_cold" status if the temperature
     * drops below the cold threshold.
     */
    override fun listenToPlateTemperature(): Flow<String> = callbackFlow {
        val ref = database.getReference("coffee/temperature")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = snapshot.getValue(Double::class.java) ?: return
                // Check if the temperature is below the threshold and the cold notification hasn't been sent yet
                if (temp < coldThresholdCelsius && !isColdNotifSent) {
                    trySend("coffee_cold") // Emit the trigger signal
                    isColdNotifSent = true // Set flag to prevent duplicates
                } else if (temp >= coldThresholdCelsius) {
                    isColdNotifSent = false // Reset the flag once the temperature rises above the threshold
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)
        // ✅ FIX 2: Add listener to the tracking map
        activeListeners[ref] = listener

        awaitClose {
            ref.removeEventListener(listener)
            activeListeners.remove(ref, listener)
        }
    }

    /**
     * Pushes a new notification object to the current user's list in the Realtime Database.
     */
    override suspend fun addNotification(notification: Notification): Result<Unit> {
        return try {
            val ref = getUserNotificationsRef()
                ?: return Result.failure(Exception("User not logged in"))

            // Pushes a new unique key and sets the notification value with the current timestamp
            ref.push().setValue(notification.copy(timestamp = System.currentTimeMillis())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks a notification as read (by deleting it from the database).
     */
    override suspend fun markAsRead(notificationId: String): Result<Unit> {
        return try {
            val ref = getUserNotificationsRef()
                ?: return Result.failure(Exception("User not logged in"))

            // Removes the notification node with the specified ID
            ref.child(notificationId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** ✅ Clear all Firebase listeners */
    override fun clearListeners() {
        activeListeners.forEach { (ref, listener) ->
            ref.removeEventListener(listener)
        }
        activeListeners.clear()
    }
}
