package com.cuppa.iot.presentation.presenters

import com.cuppa.iot.domain.models.Schedule
import com.cuppa.iot.domain.usecases.ScheduleUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Presenter for the Schedule management screen.
 * Handles loading, CRUD (Create, Read, Update, Delete) operations for brewing schedules,
 * and triggering immediate brews.
 */
class SchedulePresenter(
    private val view: ScheduleView,
    private val scheduleUseCase: ScheduleUseCase
) {

    private val scope = CoroutineScope(Dispatchers.Main) // Coroutine scope operating on the Main dispatcher for UI interaction

    /** Loads all schedules from Firebase and updates the View. */
    fun loadSchedules() {
        view.showLoading()
        scope.launch {
            val result = scheduleUseCase.getSchedules() // Fetch schedules via the UseCase
            view.hideLoading()
            result.onSuccess { schedules ->
                view.displaySchedules(schedules) // Update the list in the View
            }.onFailure { e ->
                view.showError("Failed to load schedules: ${e.message}")
            }
        }
    }

    /** Saves a new schedule or updates an existing one. */
    fun saveSchedule(schedule: Schedule) {
        scope.launch {
            val result = scheduleUseCase.saveSchedule(schedule)
            result.onSuccess {
                // Show appropriate success message based on whether it's a new schedule (id == null) or an update
                view.showSuccess(
                    if (schedule.id == null)
                        "Schedule created successfully."
                    else
                        "Schedule updated successfully."
                )
                // Refresh the list after a successful save
                loadSchedules()
            }.onFailure { e ->
                val action = if (schedule.id == null) "create" else "edit"
                view.showError("Failed to $action schedule: ${e.message}")
            }
        }
    }

    /** Deletes a schedule by ID */
    fun deleteSchedule(scheduleId: String) {
        scope.launch {
            val result = scheduleUseCase.deleteSchedule(scheduleId)
            result.onSuccess {
                view.showSuccess("Schedule deleted.")
                loadSchedules() // Refresh the list after deletion
            }.onFailure { e ->
                view.showError("Failed to delete schedule: ${e.message}")
            }
        }
    }

    /**
     * Toggles a schedule’s active state and, if enabled, triggers an immediate brew command.
     */
    fun toggleScheduleEnabled(scheduleId: String, isEnabled: Boolean, schedule: Schedule) {
        scope.launch {
            // Update the enabled flag in the database (which also manages WorkManager job)
            val result = scheduleUseCase.updateScheduleEnabled(scheduleId, isEnabled)
            result.onSuccess {

                if (isEnabled) {
                    // NEW: If enabled, trigger an instant brew command (intended for a "brew now" feature)
                    scheduleUseCase.triggerInstantBrew()
                    view.showSuccess("Schedule activated — brewing started")
                } else {
                    view.showSuccess("Schedule deactivated")
                }

                loadSchedules() // Refresh the list
            }.onFailure { e ->
                view.showError("Failed to update schedule: ${e.message}")
            }
        }
    }


    /** Cleans up resources by cancelling the coroutine scope. */
    fun onDestroy() {
        scope.cancel()
    }
}

/** View contract for Schedule screen */
interface ScheduleView {
    fun showLoading()
    fun hideLoading()
    fun displaySchedules(schedules: List<Schedule>)
    fun showSuccess(message: String)
    fun showError(message: String)
}
