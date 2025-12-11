package com.cuppa.iot.data.remote

import android.util.Log
import com.cuppa.iot.domain.models.UserProfile
import com.cuppa.iot.domain.repositories.ProfileRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Firebase-backed implementation of ProfileRepository.
 * Handles fetching and updating the user's profile and settings stored in Firebase Realtime Database.
 * Also monitors the coffee temperature for display purposes.
 */
class FirebaseProfileDataSource(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ProfileRepository {

    /**
     * Gets the unique ID of the current Firebase user. Throws an exception if no user is logged in.
     */
    private fun uid(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User not logged in")
    }

    private fun userRef(): DatabaseReference = database.reference.child("users").child(uid()) // Reference to the current user's root node in the Realtime Database
    private fun profileRef(): DatabaseReference = userRef().child("profile") // Reference to the user's profile data node
    private fun settingsRef(): DatabaseReference = userRef().child("settings") // Reference to the user's settings data node

    /**
     * Fetches the UserProfile and settings from Firebase Realtime Database and combines them.
     * Ensures default values are set in the database if the profile/settings nodes don't exist yet.
     */
    // ---------------- PROFILE FETCH ----------------
    override suspend fun getProfile(): Result<UserProfile> {
        return try {
            // Fetch profile and settings in one go
            val profileSnapshot = profileRef().get().await()
            val settingsSnapshot = settingsRef().get().await()

            val profileMap = profileSnapshot.value as? Map<String, Any> ?: emptyMap()
            val settingsMap = settingsSnapshot.value as? Map<String, Any> ?: emptyMap()

            // Extract values safely, providing defaults if data is missing
            val idNumber = profileMap["idNumber"] as? String ?: uid()
            val username = profileMap["username"] as? String ?: "New User"
            val email = profileMap["email"] as? String ?: auth.currentUser?.email.orEmpty()
            val autoStart = settingsMap["brewScheduler"] as? Boolean ?: false
            val tempAlert = settingsMap["temperatureAlert"] as? Boolean ?: false
            val brewTime = profileMap["brewDefaultTime"] as? String ?: "7:00 AM"
            val profilePic = profileMap["profilePic"] as? String ?: ""

            // Ensure defaults exist if node missing
            val updates = mutableMapOf<String, Any>()
            if (profileSnapshot.value == null) {
                updates["profile/idNumber"] = idNumber
                updates["profile/username"] = username
                updates["profile/email"] = email
                updates["profile/brewDefaultTime"] = brewTime
                updates["profile/profilePic"] = profilePic
                updates["profile/autoStart"] = autoStart
                updates["profile/tempAlert"] = tempAlert
            }
            // Apply all default updates in a single batch operation
            if (settingsSnapshot.value == null) {
                updates["settings/brewScheduler"] = autoStart
                updates["settings/temperatureAlert"] = tempAlert
            }
            if (updates.isNotEmpty()) userRef().updateChildren(updates).await()

            // Construct the final UserProfile domain model
            val profile = UserProfile(
                uid = uid(),
                username = username,
                email = email,
                idNumber = idNumber,
                autoStart = autoStart,
                tempAlert = tempAlert,
                brewDefaultTime = brewTime,
                profilePic = profilePic
            )

            Log.d("FirebaseProfileDataSource", "Fetched profile: $profile")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("FirebaseProfileDataSource", "Error fetching profile", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the user's username and ID number in the database profile.
     * Note: Email is kept as the existing Firebase user email.
     */
    // ---------------- UPDATE PROFILE ----------------
    override suspend fun updateProfile(username: String, email: String, idNumber: String): Result<Unit> {
        return try {
            val snapshot = profileRef().get().await()
            val current = snapshot.value as? Map<String, Any> ?: emptyMap()

            // Read existing values to avoid violating validation rules
            val currentAutoStart = (current["autoStart"] as? Boolean) ?: false
            val currentTempAlert = (current["tempAlert"] as? Boolean) ?: false
            val currentBrewTime = (current["brewDefaultTime"] as? String) ?: "7:00 AM"
            val currentProfilePic = (current["profilePic"] as? String) ?: ""

            val newProfile = mapOf(
                "username" to username,
                // Always keep the Firebase Auth email authoritative
                "email" to (auth.currentUser?.email ?: email),
                "idNumber" to idNumber,
                "autoStart" to currentAutoStart,
                "tempAlert" to currentTempAlert,
                "brewDefaultTime" to currentBrewTime,
                "profilePic" to currentProfilePic
            )

            // Replace the profile node atomically to satisfy rules
            profileRef().setValue(newProfile).await()
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("FirebaseProfileDataSource", "Error updating profile", e)
            Result.failure(e)
        }
    }


    /**
     * Updates the 'autoStart' (brew scheduler) setting in both the profile and settings nodes.
     */
    // ---------------- AUTO-START ----------------
    override suspend fun updateAutoStart(enabled: Boolean): Result<Unit> {
        return try {
            profileRef().child("autoStart").setValue(enabled).await()
            settingsRef().child("brewScheduler").setValue(enabled).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates the 'tempAlert' setting in both the profile and settings nodes.
     */
    // ---------------- TEMP ALERT ----------------
    override suspend fun updateTempAlert(enabled: Boolean): Result<Unit> {
        return try {
            profileRef().child("tempAlert").setValue(enabled).await() // Update the profile node
            settingsRef().child("temperatureAlert").setValue(enabled).await() // Update the settings node
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates the user's default brew time after validating and formatting the input.
     */
    // ---------------- BREW TIME ----------------
    override suspend fun updateBrewTime(time: String): Result<Unit> {
        return try {
            val sdf12 = SimpleDateFormat("h:mm a", Locale.getDefault())

            // Attempt to parse the time string for validation
            val parsed = try {
                sdf12.parse(time)
            } catch (e: Exception) {
                null
            }

            if (parsed == null) {
                return Result.failure(IllegalArgumentException("Invalid time format"))
            }

            // Re-format the time string to ensure consistent storage (e.g., uppercase AM/PM)
            val formattedTime = sdf12.format(parsed).uppercase(Locale.getDefault())

            // Update the brew default time in the profile node
            profileRef().child("brewDefaultTime").setValue(formattedTime).await()

            Log.d("FirebaseProfileDataSource", "Updated brew time to $formattedTime")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("FirebaseProfileDataSource", "Error updating brew time", e)
            Result.failure(e)
        }
    }

    /**
     * Returns a Flow that continuously listens for and emits the current coffee temperature.
     */
    // ---------------- TEMPERATURE MONITOR ----------------
    override fun observeTemperature(): Flow<Double> = callbackFlow {
        val ref = database.reference.child("coffee").child("temperature")
        // ValueEventListener to track temperature changes
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Emits the temperature double to the Flow
                snapshot.getValue(Double::class.java)?.let { trySend(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        // Cleanup: remove the listener when the Flow collector stops
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Signs out the current user from Firebase Auth.
     */
    // ---------------- LOGOUT ----------------
    override suspend fun logout(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
