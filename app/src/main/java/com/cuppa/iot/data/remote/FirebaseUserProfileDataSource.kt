package com.cuppa.iot.data.remote

import com.cuppa.iot.domain.models.UserProfile
import com.cuppa.iot.domain.repositories.UserProfileRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Firebase implementation of UserProfileRepository.
 * Handles user profile data Read/Update of Home,
 * live updates for greeting, coffee status, and temperature using Firebase Realtime Database.
 */
class FirebaseUserProfileDataSource(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : UserProfileRepository {

    private val db = database.reference // Reference to the root of the Firebase Realtime Database

    /**
     * Fetches the complete UserProfile data once from Firebase Realtime Database.
     */
    override suspend fun getUserProfile(): Result<UserProfile> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("User not logged in"))

            val snapshot = db.child("users").child(user.uid).child("profile").get().await() // Get the snapshot of the user's profile node
            if (!snapshot.exists()) return Result.failure(Exception("Profile not found"))

            val profile = snapshot.getValue(UserProfile::class.java)?.copy(uid = user.uid) // Map the data snapshot to the UserProfile data class
                ?: return Result.failure(Exception("Invalid profile data"))

            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates specific fields of the user's profile in Firebase Realtime Database.
     */
    override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            if (profile.uid.isBlank()) return Result.failure(Exception("Missing UID"))

            // Map of fields to update
            val updates = mapOf(
                "username" to profile.username,
                "email" to profile.email,
                "idNumber" to profile.idNumber
            )

            db.child("users").child(profile.uid).child("profile").updateChildren(updates).await() // Performs a multi-path update on the profile node
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns a cold Flow that fetches the user's username once and emits a time-based greeting.
     */
    override fun getUserGreeting(): Flow<String> = flow {
        val user = auth.currentUser
        if (user == null) {
            emit("Hello, User")
            return@flow
        }

        try {
            val snapshot = db.child("users").child(user.uid).child("profile").child("username").get().await() // Fetches the username once using a Task
            val username = snapshot.getValue(String::class.java) ?: "User"
            emit(generateGreeting(username)) // Emits the time-based greeting
        } catch (_: Exception) {
            emit("Hello, User")
        }
    }

    /**
     * Returns a Flow that observes the user's username in real-time and emits a time-based greeting on every change.
     */
    override fun observeUserGreeting(): Flow<String> = callbackFlow {
        val userId = auth.currentUser?.uid ?: run {
            close(Exception("User not logged in"))
            return@callbackFlow
        }

        val ref = db.child("users").child(userId).child("profile").child("username")

        // ValueEventListener to listen for live changes to the username field
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.getValue(String::class.java) ?: "User"
                trySend(generateGreeting(name)) // Sends the updated, time-based greeting to the Flow
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        // Cleanup: removes the listener when the Flow collector stops
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Returns a Flow that observes the IoT device's coffee status in real-time.
     */
    override fun observeCoffeeStatus(): Flow<String?> = callbackFlow {
        val ref = db.child("coffee").child("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(String::class.java)) // Emits the current status string (e.g., "brewing", "idle")
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Returns a Flow that observes the IoT device's coffee temperature in real-time.
     */
    override fun observeCoffeeTemperature(): Flow<Double?> = callbackFlow {
        val ref = db.child("coffee").child("temperature")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Double::class.java)) // Emits the current temperature value
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Generates a greeting ("Good morning", "Good afternoon", "Good evening") based on the current time of day.
     */
    private fun generateGreeting(name: String): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        return "$greeting, $name!"
    }

}
