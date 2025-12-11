package com.cuppa.iot.presentation.state

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * ViewModel for managing the local brewing state, timer, and UI updates.
 * This class persists the brewing state across app restarts and handles the local countdown.
 */
class BrewStateViewModel(application: Application) : AndroidViewModel(application) {

    // --- LiveData for UI binding ---
    val timerText = MutableLiveData("01:00") // Timer display text
    val isBrewing = MutableLiveData(false)   // Brewing state: true = brewing
    val brewStatus = MutableLiveData("Ready to brew") // Status text
    val buttonLabel = MutableLiveData("BREW COFFEE") // Brew button text
    val iconState = MutableLiveData(BrewIconState.IDLE) // Brew icon state

    // --- Internal state ---
    private val prefs = getApplication<Application>().getSharedPreferences("brew_prefs", Context.MODE_PRIVATE)
    private var startTime: Long = 0
    private var countdownJob: Job? = null
    private var brewDurationMs: Long = DEFAULT_BREW_DURATION_MS

    init {
        // Restore saved duration
        brewDurationMs = prefs.getLong(KEY_DURATION_MS, DEFAULT_BREW_DURATION_MS)
        timerText.value = formatDuration(brewDurationMs)
        pushDurationToFirebase(brewDurationMs) // keep Firebase in sync on launch

        // Simple restore
        val brewing = prefs.getBoolean("isBrewing", false)
        val lastStart = prefs.getLong("startTime", 0L)
        if (brewing && lastStart > 0) {
            startTime = lastStart
            isBrewing.value = true
            startCountdown()
        }
    }

    /**
     * Start brewing from the beginning.
     */
    fun startBrewing() {
        if (isBrewing.value == true) return // Already brewing
        // Ensure device gets the latest user-selected duration before we mark brewing
        pushDurationToFirebase(brewDurationMs)
        startTime = SystemClock.elapsedRealtime()
        isBrewing.value = true
        brewStatus.value = "Brewing..."
        buttonLabel.value = "BREWING"
        iconState.value = BrewIconState.BREWING

        // Save state for persistence
        prefs.edit()
            .putBoolean("isBrewing", true)
            .putLong("startTime", startTime)
            .apply()

        startCountdown()
    }

    /**
     * Reset brewing state and timer.
     */
    fun resetBrewing() {
        countdownJob?.cancel()        // Stop any running countdown
        isBrewing.value = false       // Set brewing state false
        timerText.value = formatDuration(brewDurationMs) // Reset timer text based on selected duration
        brewStatus.value = "Ready to brew"
        buttonLabel.value = "BREW COFFEE"
        iconState.value = BrewIconState.IDLE
        startTime = 0
        prefs.edit().clear().apply()  // Clear saved preferences
    }

    /**
     * Countdown timer logic.
     * Runs every second, calculating remaining time based on start/paused timestamps.
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isBrewing.value == true) {
                // Calculate elapsed based on real time
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val remaining = brewDurationMs - elapsed

                if (remaining > 0) {
                    val minutes = (remaining / 1000) / 60
                    val seconds = (remaining / 1000) % 60
                    timerText.value = String.format("%02d:%02d", minutes, seconds)
                    delay(1000)
                } else {
                    timerText.value = "00:00"
                    brewStatus.value = "Brewing complete"
                    buttonLabel.value = "BREW COFFEE"
                    iconState.value = BrewIconState.COMPLETE
                    isBrewing.value = false
                    startTime = 0
                    prefs.edit().clear().apply()
                    break
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }

    /**
     * Allows the UI to update the brew duration (in minutes).
     * Persists the selection and refreshes the timer display when idle.
     */
    fun setBrewDurationMinutes(minutes: Int) {
        val clampedMinutes = minutes.coerceAtLeast(1)
        brewDurationMs = clampedMinutes * 60 * 1000L
        prefs.edit().putLong(KEY_DURATION_MS, brewDurationMs).apply()
        pushDurationToFirebase(brewDurationMs)

        // Only update the visible timer if we're not mid-brew.
        if (isBrewing.value != true) {
            timerText.value = formatDuration(brewDurationMs)
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun pushDurationToFirebase(durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FirebaseDatabase.getInstance()
                    .reference
                    .child("coffee")
                    .child("command")
                    .child("brewDurationMs")
                    .setValue(durationMs)
            } catch (_: Exception) {
                // Ignore network errors; user can retry selecting duration
            }
        }
    }

    // Enum to represent the state of the brewing icon/animation
    enum class BrewIconState { IDLE, BREWING, COMPLETE }

    companion object {
        private const val DEFAULT_BREW_DURATION_MS = 1 * 60 * 1000L // 1 minute in milliseconds
        private const val KEY_DURATION_MS = "brewDurationMs"
    }
}