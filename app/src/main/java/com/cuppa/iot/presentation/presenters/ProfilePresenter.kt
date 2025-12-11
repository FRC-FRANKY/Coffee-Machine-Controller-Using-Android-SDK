package com.cuppa.iot.presentation.presenters

import com.cuppa.iot.domain.models.Notification
import com.cuppa.iot.domain.models.UserProfile
import com.cuppa.iot.domain.usecases.NotificationUseCase
import com.cuppa.iot.domain.usecases.ScheduleUseCase
import com.cuppa.iot.domain.usecases.ProfileUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/**
 * Presenter for the Profile Activity/Fragment.
 * Manages fetching, displaying, and updating user profile settings and state toggles.
 */
class ProfilePresenter(
    private val profileUseCase: ProfileUseCase,
    private val notificationUseCase: NotificationUseCase,
    private val scheduleUseCase: ScheduleUseCase
) {

    private var view: ProfileView? = null
    private val scope = CoroutineScope(Dispatchers.Main) // Coroutine scope tied to the lifecycle of the presenter

    fun attachView(view: ProfileView) { this.view = view } // Binds the View interface implementation
    fun detachView() { this.view = null } // Clears the view reference to prevent memory leaks

    /**
     * Fetches the user's profile and settings from the database and updates the View.
     * Also handles generating and saving a missing 8-digit ID if needed.
     */
    fun loadProfile() {
        view?.showLoading()
        scope.launch {
            val result = profileUseCase.getProfile()
            view?.hideLoading()
            result.onSuccess { userProfile ->
                // ✅ Business logic: Auto-generate missing 8-digit ID if blank
                if (userProfile.idNumber.isBlank()) {
                    val newId = (10000000..99999999).random().toString()
                    // Update the ID in the database
                    profileUseCase.updateProfile(userProfile.username, userProfile.email, newId)
                    val updatedProfile = userProfile.copy(idNumber = newId)
                    view?.displayProfile(updatedProfile)
                } else {
                    view?.displayProfile(userProfile)
                }
            }.onFailure {
                view?.showError("Failed to load profile: ${it.message}")
            }
        }
    }

    /**
     * Saves changes to the user's mutable profile fields.
     */
    fun saveProfile(username: String, email: String, idNumber: String) {
        if (username.isBlank() || email.isBlank()) {
            view?.showError("Fields cannot be empty.")
            return
        }

        view?.showLoading()
        scope.launch {
            val result = profileUseCase.updateProfile(username, email, idNumber)
            view?.hideLoading()
            result.onSuccess {
                view?.showSuccess("Profile updated successfully.")
                // Reload to get authoritative values
                loadProfile()
            }.onFailure {
                view?.showError("Failed to update: ${it.message}")
            }
        }
    }

    /**
     * Helper function to find the "Daily" schedule (the default auto-brew schedule)
     * and toggle its 'enabled' state in the database.
     */
    private suspend fun toggleDefaultSchedule(enabled: Boolean) {
        // 1. Fetch all schedules
        val schedulesResult = scheduleUseCase.getSchedules()
        if (schedulesResult.isFailure) return

        val schedules = schedulesResult.getOrNull() ?: return

        // 2. Identify the default schedule (logic assumes 'Daily' repeat is the default)
        val defaultSchedule = schedules.firstOrNull { schedule ->
            schedule.repeat.equals("Daily", true)
        } ?: return

        // 3. Toggle the actual schedule's enabled state using the ScheduleUseCase
        defaultSchedule.id?.let { scheduleId ->
            scheduleUseCase.updateScheduleEnabled(scheduleId, enabled)
        }
    }

    /**
     * Handles toggling the Auto-Start setting (which controls the default brew schedule).
     */
    fun toggleAutoStart(enabled: Boolean) {
        scope.launch {
            // 1. Update the stored preference/flag in the database
            val result = profileUseCase.updateAutoStart(enabled)

            if (result.isSuccess) {
                // 2. Business logic: Update the actual schedule entry controlled by this flag
                toggleDefaultSchedule(enabled)

                view?.showSuccess("Auto-start ${if (enabled) "enabled" else "disabled"}.")
                loadProfile()

            } else {
                view?.showError("Failed to update Auto-start.")
            }
        }
    }

    /**
     * Handles toggling the Temperature Alert setting.
     */
    fun toggleTempAlert(enabled: Boolean) {
        scope.launch {
            val result = profileUseCase.updateTempAlert(enabled)
            result.onSuccess {
                view?.showSuccess("Temperature alert ${if (enabled) "enabled" else "disabled"}.")
                loadProfile() // refresh state
            }.onFailure {
                view?.showError("Failed to update temperature alert: ${it.message}")
            }
        }
    }

    /**
     * Updates the user's default brew time setting.
     */
    fun updateBrewTime(time: String) {
        scope.launch {
            view?.updateBrewTimeDisplay(time) // Provide immediate visual feedback to the user

            val result = profileUseCase.updateBrewTime(time) // Call UseCase to update time in the database

            result.onSuccess {
                view?.showSuccess("Brew time updated to $time.")
                loadProfile()
            }.onFailure {
                view?.showError("Failed to update brew time: ${it.message}")
            }
        }
    }


    /**
     * Starts observing the live coffee temperature and updates the display.
     */
    fun observeTemperature() {
        scope.launch {
            profileUseCase.observeTemperature().collectLatest {
                view?.updateTemperatureDisplay("${it.toInt()} °C")
            }
        }
    }

    /**
     * Listens to coffee status updates specifically to generate persistent notifications
     * for events like "done" (brew complete) or "cold".
     */
    fun observeCoffeeStatus() {
        scope.launch {
            notificationUseCase.listenToCoffeeStatus().collectLatest { status ->
                when (status) {
                    "done" -> {
                        val notif = Notification(
                            title = "Your Coffee is Ready! ☕",
                            message = "Brewing complete. Enjoy your cup!",
                            category = "Today"
                        )
                        notificationUseCase.addNotification(notif)
                    }
                    "cold" -> {
                        val notif = Notification(
                            title = "Coffee Cooling Down",
                            message = "Your coffee has gone cold. Time for a fresh brew?",
                            category = "Today"
                        )
                        notificationUseCase.addNotification(notif)
                    }
                }
            }
        }
    }

    /**
     * Signs the user out and navigates back to the Login screen on success.
     */
    fun logout() {
        view?.let { v ->
            scope.launch {
                notificationUseCase.clearAllListeners()

                val result = profileUseCase.logout() // This calls FirebaseAuth.signOut()

                if (result.isSuccess) {
                    v.navigateToLogin()
                } else {
                    // If sign-out itself fails, show an error.
                    v.showError("Logout failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    /**
     * Clears resources when the View is destroyed.
     */
    fun onDestroy() {
        scope.cancel()
        view = null
    }

}

/**
 * View contract for the Profile screen.
 * Defines methods the Presenter uses to display profile data and control UI elements.
 */
interface ProfileView {
    fun displayProfile(user: UserProfile)
    fun updateAutoStartSwitch(enabled: Boolean)
    fun updateTempAlertSwitch(enabled: Boolean)
    fun updateBrewTimeDisplay(time: String)
    fun showLoading()
    fun hideLoading()
    fun showSuccess(message: String)
    fun showError(message: String)
    fun updateTemperatureDisplay(temp: String)
    fun navigateToLogin()
}
