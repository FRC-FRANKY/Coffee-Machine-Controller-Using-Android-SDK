package com.cuppa.iot.domain.repositories

import com.cuppa.iot.domain.models.BrewCommand
import com.cuppa.iot.domain.models.BrewStatus
import com.cuppa.iot.domain.models.Notification
import com.cuppa.iot.domain.models.Schedule
import com.cuppa.iot.domain.models.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for authentication operations.
 * Domain layer abstraction for auth functionality.
 */
interface AuthRepository {
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(username: String, email: String, password: String): Result<Unit>
    suspend fun sendEmailVerification(): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun firebaseAuthWithGoogle(idToken: String): Result<Unit>
    suspend fun saveCredentials(email: String, password: String)
    suspend fun clearSavedCredentials()
    suspend fun isEmailLinkedToGoogle(email: String): Result<Boolean>
    suspend fun linkGoogleToEmailAccount(idToken: String): Result<Unit>
    suspend fun linkEmailToGoogleAccount(email: String, password: String): Result<Unit>
}

/**
 * Repository interface for brewing operations.
 * Domain layer abstraction for brew functionality.
 */
interface BrewRepository {
    suspend fun sendBrewCommand(command: BrewCommand): Result<Unit>
    suspend fun resetBrewCommand(): Result<Unit>

    // --- Observers ---
    fun observeBrewStatus(): Flow<BrewStatus>
    fun observeCoffeeTemperature(): Flow<Double?>
}

/**
 * Repository interface for user profile operations.
 * Domain layer abstraction for profile functionality.
 */
interface UserProfileRepository {
    suspend fun getUserProfile(): Result<UserProfile>
    fun observeUserGreeting(): Flow<String>
    suspend fun updateUserProfile(profile: UserProfile): Result<Unit>
    fun getUserGreeting(): Flow<String>
    fun observeCoffeeStatus(): Flow<String?>
    fun observeCoffeeTemperature(): Flow<Double?>
}

interface ScheduleRepository {
    suspend fun getSchedules(): Result<List<Schedule>>
    suspend fun saveSchedule(schedule: Schedule): Result<Unit>
    suspend fun deleteSchedule(scheduleId: String): Result<Unit>
    suspend fun updateScheduleEnabled(scheduleId: String, isEnabled: Boolean): Result<Unit>
    suspend fun triggerInstantBrew(): Result<Unit>
}

interface NotificationRepository {
    fun listenToUserNotifications(): Flow<List<Notification>>
    fun listenToCoffeeStatus(): Flow<String>
    fun listenToPlateTemperature(): Flow<String>
    fun clearListeners()
    suspend fun addNotification(notification: Notification): Result<Unit>
    suspend fun markAsRead(notificationId: String): Result<Unit>
}

interface ProfileRepository {
    suspend fun getProfile(): Result<UserProfile>
    suspend fun updateProfile(username: String, email: String, idNumber: String): Result<Unit>
    suspend fun updateAutoStart(enabled: Boolean): Result<Unit>
    suspend fun updateTempAlert(enabled: Boolean): Result<Unit>
    suspend fun updateBrewTime(time: String): Result<Unit>
    fun observeTemperature(): Flow<Double>
    suspend fun logout(): Result<Unit>
}