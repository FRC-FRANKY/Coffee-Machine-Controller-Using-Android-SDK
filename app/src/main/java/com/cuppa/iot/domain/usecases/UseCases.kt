package com.cuppa.iot.domain.usecases

import com.cuppa.iot.domain.models.BrewCommand
import com.cuppa.iot.domain.models.BrewStatus
import com.cuppa.iot.domain.models.Notification
import com.cuppa.iot.domain.models.Schedule
import com.cuppa.iot.domain.models.UserProfile
import com.cuppa.iot.domain.repositories.AuthRepository
import com.cuppa.iot.domain.repositories.BrewRepository
import com.cuppa.iot.domain.repositories.NotificationRepository
import com.cuppa.iot.domain.repositories.ProfileRepository
import com.cuppa.iot.domain.repositories.ScheduleRepository
import com.cuppa.iot.domain.repositories.UserProfileRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for authentication operations.
 * Encapsulates business logic for user authentication.
 */
class AuthUseCase(
    private val authRepository: AuthRepository
) {
    suspend fun login(email: String, password: String, rememberMe: Boolean): Result<Unit> {
        return try {
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                if (rememberMe) {
                    authRepository.saveCredentials(email, password)
                } else {
                    authRepository.clearSavedCredentials()
                }
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(username: String, email: String, password: String): Result<Unit> {
        return authRepository.register(username, email, password)
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return authRepository.resetPassword(email)
    }

    suspend fun googleSignIn(idToken: String): Result<Unit> {
        return authRepository.firebaseAuthWithGoogle(idToken)
    }

    suspend fun sendEmailVerification(): Result<Unit> {
        return authRepository.sendEmailVerification()
    }

    suspend fun isEmailLinkedToGoogle(email: String): Result<Boolean> {
        return authRepository.isEmailLinkedToGoogle(email)
    }
}

/**
 * Use case for brewing operations.
 * Encapsulates business logic for coffee brewing.
 */
class BrewUseCase(
    private val brewRepository: BrewRepository
) {
    suspend fun startBrewing(): Result<Unit> {
        val command = BrewCommand(shouldStart = true)
        return brewRepository.sendBrewCommand(command)
    }

    suspend fun stopBrewing(): Result<Unit> {
        val command = BrewCommand(shouldStart = false)
        return brewRepository.sendBrewCommand(command)
    }

    suspend fun resetBrewCommand(): Result<Unit> {
        return brewRepository.resetBrewCommand()
    }

    fun observeBrewStatus(): Flow<BrewStatus> = brewRepository.observeBrewStatus()
    fun observeTemperature(): Flow<Double?> = brewRepository.observeCoffeeTemperature()
}

/**
 * Use case for user profile operations.
 * Encapsulates business logic for user profile management for the home live greetings.
 */
class UserProfileUseCase (
    private val userProfileRepository: UserProfileRepository
) {
    fun getUserGreeting(): Flow<String> {
        return userProfileRepository.getUserGreeting()
    }
    fun observeUserGreeting(): Flow<String> {
        return userProfileRepository.observeUserGreeting()
    }
}

/**
 * Use case for schedule operations.
 * Encapsulates business logic for autobrew schedule management.
 */
class ScheduleUseCase(private val repository: ScheduleRepository) {

    suspend fun getSchedules() = repository.getSchedules()
    suspend fun saveSchedule(schedule: Schedule) = repository.saveSchedule(schedule)
    suspend fun deleteSchedule(scheduleId: String) = repository.deleteSchedule(scheduleId)
    suspend fun updateScheduleEnabled(scheduleId: String, isEnabled: Boolean) =
        repository.updateScheduleEnabled(scheduleId, isEnabled)
    suspend fun triggerInstantBrew() = repository.triggerInstantBrew()
}

/**
 * Use case for notification operations.
 * Encapsulates business logic for notifications management.
 */
class NotificationUseCase (
    private val notificationRepository: NotificationRepository
) {
    fun listenToUserNotifications(): Flow<List<Notification>> =
        notificationRepository.listenToUserNotifications()

    fun listenToCoffeeStatus(): Flow<String> =
        notificationRepository.listenToCoffeeStatus()

    fun listenToPlateTemperature(): Flow<String> =
        notificationRepository.listenToPlateTemperature()

    suspend fun addNotification(notification: Notification): Result<Unit> =
        notificationRepository.addNotification(notification)

    suspend fun markAsRead(notificationId: String): Result<Unit> =
        notificationRepository.markAsRead(notificationId)

    fun clearAllListeners() {
        // Calls your repository to remove Firebase observers
        notificationRepository.clearListeners()
    }

}

/**
 * Use case for profile and settings operations.
 * Encapsulates business logic for the profile management.
 */
class ProfileUseCase (
    private val profileRepository: ProfileRepository
) {
    suspend fun getProfile(): Result<UserProfile> = profileRepository.getProfile()

    suspend fun updateProfile(username: String, email: String, idNumber: String): Result<Unit> =
        profileRepository.updateProfile(username, email, idNumber)

    suspend fun updateAutoStart(enabled: Boolean): Result<Unit> =
        profileRepository.updateAutoStart(enabled)

    suspend fun updateTempAlert(enabled: Boolean): Result<Unit> =
        profileRepository.updateTempAlert(enabled)

    suspend fun updateBrewTime(time: String): Result<Unit> =
        profileRepository.updateBrewTime(time)

    fun observeTemperature(): Flow<Double> = profileRepository.observeTemperature()

    suspend fun logout(): Result<Unit> {
        return try {
            profileRepository.logout() // logs out from Firebase
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}