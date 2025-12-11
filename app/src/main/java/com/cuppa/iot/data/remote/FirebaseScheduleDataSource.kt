package com.cuppa.iot.data.remote

import android.util.Log
import android.content.Context
import android.preference.PreferenceManager
import com.cuppa.iot.domain.models.Schedule
import com.cuppa.iot.domain.repositories.ScheduleRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Firebase-backed implementation of ScheduleRepository.
 * Handles CRUD operations for brewing schedules in the Realtime Database and integrates
 * with Android WorkManager to execute the schedules at the appointed time.
 */
class FirebaseScheduleDataSource(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val context: Context
) : ScheduleRepository {

    // Helper to get the current user's UID or throw an exception if not logged in
    private fun uid(): String = auth.currentUser?.uid
        ?: throw IllegalStateException("User not logged in")

    // Database reference to the current user's schedules node: /users/{uid}/schedules
    private fun schedulesRef(): DatabaseReference =
        database.reference.child("users").child(uid()).child("schedules")

    // Database reference to the brew command path for the IoT device: /coffee/command/brewNow
    private val commandRef: DatabaseReference =
        database.reference.child("coffee").child("command").child("brewNow")

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault()) // Date formatter for handling schedule time strings (e.g., "7:00 AM")
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context) // Shared Preferences for local data (though not strictly used in the methods shown)

    /**
     * Helper function to simplify the display text for repeat days.
     * If all 7 days are selected, it returns "Daily". Otherwise, it returns a comma-separated list.
     */
    private fun normalizeRepeatDays(repeat: String): String {
        val days = repeat.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (days.size >= 7) "Daily" else days.joinToString(", ")
    }

    /**
     * Fetches all brewing schedules for the current user from Firebase.
     * If no schedules exist, it creates a default "7:00 AM Daily" schedule only once.
     */
    override suspend fun getSchedules(): Result<List<Schedule>> = try {
        val userRef = database.reference.child("users").child(uid())
        val schedulesRef = userRef.child("schedules")

        // Fetch all existing schedules
        val schedulesSnapshot = schedulesRef.get().await()
        val scheduleList = schedulesSnapshot.children.mapNotNull { it.getValue(Schedule::class.java) }

        // Check if the default schedule has already been created
        val defaultCreatedSnapshot = userRef.child("default_schedule_created").get().await()
        val defaultCreated = defaultCreatedSnapshot.getValue(Boolean::class.java) ?: false

        // If no schedules exist AND the default hasn't been created, create it
        if (scheduleList.isEmpty() && !defaultCreated) {
            val defaultSchedule = Schedule(
                time = "7:00 AM",
                repeat = "Daily",
                enabled = true
            )

            saveSchedule(defaultSchedule) // Saves the new default schedule to Firebase
            userRef.child("default_schedule_created").setValue(true).await() // Marks a flag in Firebase to prevent future creation of the default schedule
            Log.i("FirebaseSchedule", "✅ Default 7:00 AM schedule created for ${uid()}")
        }

        Result.success(scheduleList)
    } catch (e: Exception) {
        Log.e("FirebaseScheduleDataSource", "❌ Error fetching schedules", e)
        Result.failure(e)
    }

    /**
     * Saves a new schedule or updates an existing one in Firebase Realtime Database.
     */
    override suspend fun saveSchedule(schedule: Schedule): Result<Unit> = try {
        val id = schedule.id ?: schedulesRef().push().key ?: UUID.randomUUID().toString() // Generates a new ID if the schedule object doesn't have one (for new schedules)
        val formattedTime = schedule.time
        val repeatText = normalizeRepeatDays(schedule.repeat)

        // Data map for the Firebase node
        val data = mapOf(
            "id" to id,
            "time" to formattedTime,
            "repeat" to repeatText,
            "enabled" to schedule.enabled
        )

        schedulesRef().child(id).setValue(data).await() // Writes the schedule data to the specific node using the ID

        // Ensure WorkManager is aligned with the latest schedule state.
        // Cancel any existing work for this schedule ID (covers updates).
        cancelWorkManager(id)

        // If the schedule is enabled, queue the worker for the selected time.
        if (schedule.enabled) {
            scheduleWithWorkManager(id, formattedTime, repeatText)
        }

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseScheduleDataSource", "Error saving schedule", e)
        Result.failure(e)
    }

    /**
     * Deletes a schedule from Firebase Realtime Database using its ID.
     */
    override suspend fun deleteSchedule(scheduleId: String): Result<Unit> = try {
        schedulesRef().child(scheduleId).removeValue().await() // Removes the schedule node
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseScheduleDataSource", "Error deleting schedule", e)
        Result.failure(e)
    }

    /**
     * Updates the 'enabled' state of a schedule in Firebase and manages the corresponding WorkManager job.
     */
    override suspend fun updateScheduleEnabled(scheduleId: String, isEnabled: Boolean): Result<Unit> = try {
        schedulesRef().child(scheduleId).child("enabled").setValue(isEnabled).await() // Update the 'enabled' field in Firebase

        val snapshot = schedulesRef().child(scheduleId).get().await() // Fetch the entire schedule object to get time and repeat info for WorkManager
        val schedule = snapshot.getValue(Schedule::class.java)

        schedule?.let { scheduleItem ->
            scheduleItem.id?.let { id ->
                if (isEnabled) {
                    scheduleWithWorkManager(id, scheduleItem.time, scheduleItem.repeat) // Schedule a new job with WorkManager
                } else {
                    cancelWorkManager(id) // Cancel the existing WorkManager job
                }
            }
        }

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseScheduleDataSource", "Error updating enabled state", e)
        Result.failure(e)
    }

    /**
     * NEW: Trigger an immediate brew by toggling the firebase brew command path.
     * This is a suspend function so callers can await success/failure.
     */
    override suspend fun triggerInstantBrew(): Result<Unit> = try {
        // Set true so ESP32 detects the command
        commandRef.setValue(true).await()
        Log.i("FirebaseSchedule", "☕ Instant brew set to TRUE")

        // Pause execution for 30 seconds to give the device time to register the command
        delay(30_000L)

        // Reset the command flag so the device is ready for the next instruction
        commandRef.setValue(false).await()
        Log.i("FirebaseSchedule", "☕ Instant brew reset to FALSE")

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseSchedule", "❌ triggerInstantBrew failed: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Calculates the required delay and schedules a OneTimeWorkRequest with WorkManager.
     * This job will run the BrewSchedulerWorker at the specified schedule time.
     */
    private fun scheduleWithWorkManager(scheduleId: String, timeString: String, repeat: String = "One-time") {
        try {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val brewTime = sdf.parse(timeString) ?: return
            val now = Calendar.getInstance()
            // Calculate the target time: today, or tomorrow if the time has already passed
            val target = Calendar.getInstance().apply {
                time = brewTime
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.MONTH, now.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
                // If the target time is in the past, schedule it for the next day
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }

            // Calculate the initial delay until the target time
            val delayMillis = target.timeInMillis - now.timeInMillis

            Log.i("FirebaseSchedule", "⏳ Scheduling WorkManager for $timeString (${delayMillis / 60000L} min from now)")

            // Build the WorkRequest with the calculated delay and required data
            val workRequest = OneTimeWorkRequestBuilder<com.cuppa.iot.workers.BrewSchedulerWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        "uid" to uid(),
                        "brewPath" to "coffee/command/brewNow",
                        "repeat" to repeat,
                        "time" to timeString
                    )
                )
                // Use the schedule ID as the unique tag for later cancellation
                .addTag(scheduleId)
                .build()

            // Enqueue the request
            WorkManager.getInstance(context).enqueue(workRequest)
        } catch (e: Exception) {
            Log.e("FirebaseSchedule", "❌ Failed to schedule WorkManager: ${e.message}")
        }
    }

    /**
     * Cancels any pending WorkManager jobs associated with the given schedule ID tag.
     */
    private fun cancelWorkManager(scheduleId: String) {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(scheduleId) // Cancels all work that was tagged with the schedule ID
            Log.i("FirebaseSchedule", "✅ Canceled WorkManager for schedule ID: $scheduleId")
        } catch (e: Exception) {
            Log.e("FirebaseSchedule", "❌ Failed to cancel WorkManager: ${e.message}")
        }
    }
}
