package com.cuppa.iot.presentation.presenters

import android.util.Log
import com.cuppa.iot.domain.models.BrewStatus
import com.cuppa.iot.domain.usecases.BrewUseCase
import com.cuppa.iot.domain.usecases.UserProfileUseCase
import com.cuppa.iot.presentation.state.BrewStateViewModel
import kotlinx.coroutines.*

/**
 * Presenter for HomeFragment in MVP pattern.
 * Handles UI logic for brewing, ESP32 status, and user greeting.
 * Responsible for mediating between Use Cases (business logic) and the View (UI display).
 */
class HomePresenter(
    private val view: HomeView,
    private val brewUseCase: BrewUseCase,
    private val userProfileUseCase: UserProfileUseCase,
    private val brewStateViewModel: BrewStateViewModel // Shared state to track brewing status across the app
) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main) // Coroutine scope tied to the presenter lifecycle for safe asynchronous operations

    /**
     * Starts observing the user's greeting (name + time of day) for real-time updates.
     */
    fun loadUserProfile() {
        coroutineScope.launch {
            try {
                // Collects real-time changes to the greeting from the UseCase
                userProfileUseCase.observeUserGreeting().collect { greeting ->
                    view.showGreeting(greeting)
                }
            } catch (e: Exception) {
                view.showGreeting("Hello, User")
            }
        }
    }

    /**
     * Observes the ESP32’s reported brewing status from the data source.
     * Updates the main status text, button label, and icon accordingly.
     */
    fun observeEsp32Status() {
        coroutineScope.launch {
            try {
                // Collects BrewStatus updates from the UseCase
                brewUseCase.observeBrewStatus().collect { brewStatus ->
                    when (brewStatus.status) {
                        "brewing", "done", "cold", "idle" -> handleOnlineStatus(brewStatus)
                    }
                }
            } catch (e: Exception) {
                view.showError("Failed to observe ESP32 status: ${e.message}")
            }
        }
    }

    /**
     * Handles UI updates when the device is reporting an online status ("brewing", "done", "cold", "idle").
     */
    private fun handleOnlineStatus(brewStatus: BrewStatus) {
        when (brewStatus.status) {
            "brewing" -> {
                view.updateStatus("Brewing in progress…")
                view.updateButton("BREWING")
                view.setBrewingIcon()
                if (brewStateViewModel.isBrewing.value != true) {
                    brewStateViewModel.startBrewing()
                }
            }
            "done" -> {
                view.updateStatus("Brew complete! ✅")
                view.updateButton("BREW COFFEE")
                view.setCompleteIcon()
                view.showPopOutDialog("Brew Complete", "Your coffee is ready ☕")
                brewStateViewModel.resetBrewing()
                // Ensures the Firebase command flag is reset after brewing completes
                resetBrewCommandWithRetry()
            }
            "cold" -> {
                view.updateStatus("Coffee cold ❄️")
                view.updateButton("BREW COFFEE")
                view.setCompleteIcon()
                view.showPopOutDialog("Coffee Cold", "Your coffee has gone cold... :<")
                brewStateViewModel.resetBrewing()
                // Ensures the Firebase command flag is reset after the cold state is recognized
                resetBrewCommandWithRetry()
            }
            "idle" -> {
                view.updateStatus("Coffee machine idle... Zzz...")
                view.updateButton("BREW COFFEE")
                view.setCompleteIcon()
                brewStateViewModel.resetBrewing()
            }
        }
    }

    /**
     * Observes temperature changes reported by ESP32 in real-time and updates the UI display.
     */
    fun observeCoffeeTemperature() {
        coroutineScope.launch {
            try {
                brewUseCase.observeTemperature().collect { temp ->
                    temp?.let {
                        view.updateTemperature("${it.toInt()} °C")
                    }
                }
            } catch (e: Exception) {
                view.showError("Failed to observe temperature: ${e.message}")
            }
        }
    }

    /**
     * Handles Brew button click — determines whether to start or stop brewing based on current state.
     */
    fun onBrewButtonClicked() {
        val currentlyBrewing = brewStateViewModel.isBrewing.value ?: false // Reads the current brewing state from the shared ViewModel

        coroutineScope.launch {
            view.showLoading()

            val result = try {
                if (!currentlyBrewing) brewUseCase.startBrewing() // Calls the appropriate UseCase method (start or stop)
                else brewUseCase.stopBrewing()
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                view.hideLoading()
            }

            // Handles success or failure of the command send
            if (result.isSuccess) {
                if (!currentlyBrewing) {
                    // Update UI and state for start
                    view.updateButton("BREWING")
                    view.setBrewingIcon()
                    brewStateViewModel.startBrewing()
                    view.updateStatus("Brewing started… ☕")
                } else {
                    // Update UI and state for stop/cancel
                    view.updateStatus("Brewing cancelled ❌")
                    view.updateButton("BREW COFFEE")
                    view.setCompleteIcon()
                    brewStateViewModel.resetBrewing()
                }
            } else {
                // Show error if the command failed to send (e.g., network error)
                view.showPopOutDialog(
                    "Error",
                    result.exceptionOrNull()?.message ?: "Failed to send brew command."
                )
            }
        }
    }

    /**
     * Safely resets brew command on Firebase after brewing ends with exponential backoff retry logic.
     * This ensures the `brewNow` flag is reset to false, ready for the next command.
     */
    private fun resetBrewCommandWithRetry(maxRetries: Int = 3, attempt: Int = 1) {
        coroutineScope.launch {
            val success = withContext(Dispatchers.IO) { brewUseCase.resetBrewCommand().isSuccess }
            if (!success && attempt < maxRetries) {
                delay(2000)
                resetBrewCommandWithRetry(maxRetries, attempt + 1)
            }
        }
    }

    /**
     * Cancels all coroutines when the View is destroyed to prevent memory leaks.
     */
    fun onDestroy() {
        coroutineScope.cancel()
    }
}

/**
 * View contract for Home screen.
 * Defines the methods the Presenter can call to manipulate the UI.
 */
interface HomeView {
    fun updateStatus(status: String)
    fun updateButton(label: String)
    fun updateStats(temp: Int, timer: String)
    fun showGreeting(message: String)
    fun setBrewingIcon()
    fun setCompleteIcon()
    fun updateTemperature(temp: String)
    fun showPopOutDialog(title: String, message: String)
    fun showLoading()
    fun hideLoading()
    fun showError(message: String)
    fun showMessage(message: String)
}
