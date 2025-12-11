package com.cuppa.iot.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class BrewSchedulerWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString("uid") ?: return Result.failure()
        val brewPath = inputData.getString("brewPath") ?: "/coffee/command/brewNow"
        val repeat = inputData.getString("repeat") ?: "One-time"
        val timeString = inputData.getString("time") ?: return Result.failure()

        val today = Calendar.getInstance().getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())

        // ✅ Check if today matches one of the repeat days or is Daily
        val shouldRunToday = repeat.equals("Daily", ignoreCase = true) ||
                repeat.split(",").any { it.trim().equals(today, ignoreCase = true) }

        if (!shouldRunToday) {
            Log.i("BrewSchedulerWorker", "⏸ Skipping brew for $userId (today not in $repeat)")
            scheduleNextOccurrence(userId, brewPath, repeat, timeString)
            return Result.success()
        }

        // ☕ Trigger Brew Command
        try {
            val dbRef = FirebaseDatabase.getInstance().getReference(brewPath)
            dbRef.setValue(true).await()
            Log.i("BrewSchedulerWorker", "☕ Brew triggered for user $userId")

            delay(30_000)
            dbRef.setValue(false).await()

            // Re-schedule the next valid day
            scheduleNextOccurrence(userId, brewPath, repeat, timeString)

            return Result.success()
        } catch (e: Exception) {
            Log.e("BrewSchedulerWorker", "❌ Brew trigger failed: ${e.message}")
            return Result.retry()
        }
    }

    /** Schedule next valid brew time based on repeat days */
    private fun scheduleNextOccurrence(userId: String, brewPath: String, repeat: String, timeString: String) {
        val now = Calendar.getInstance()
        val daysMap = mapOf(
            "Sun" to Calendar.SUNDAY,
            "Mon" to Calendar.MONDAY,
            "Tue" to Calendar.TUESDAY,
            "Wed" to Calendar.WEDNESDAY,
            "Thu" to Calendar.THURSDAY,
            "Fri" to Calendar.FRIDAY,
            "Sat" to Calendar.SATURDAY
        )

        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        val targetTime = sdf.parse(timeString) ?: return

        val next = Calendar.getInstance().apply {
            time = targetTime
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }

        // Find next day that matches repeat
        if (!repeat.equals("Daily", true)) {
            val repeatDays = repeat.split(",").mapNotNull { day -> daysMap[day.trim()] }
            for (i in 1..7) {
                next.add(Calendar.DAY_OF_MONTH, 1)
                if (repeatDays.contains(next.get(Calendar.DAY_OF_WEEK))) break
            }
        }

        val delayMillis = next.timeInMillis - now.timeInMillis
        val delayHours = delayMillis / 3600000L

        val nextWork = OneTimeWorkRequestBuilder<BrewSchedulerWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "uid" to userId,
                    "brewPath" to brewPath,
                    "repeat" to repeat,
                    "time" to timeString
                )
            )
            .build()

        WorkManager.getInstance(appContext).enqueue(nextWork)
        Log.i("BrewSchedulerWorker", "🔁 Next brew scheduled in $delayHours hour(s) ($timeString, $repeat)")
    }
}
