package com.cuppa.iot.data.remote

import android.util.Log
import com.cuppa.iot.domain.models.BrewCommand
import com.cuppa.iot.domain.models.BrewStatus
import com.cuppa.iot.domain.repositories.BrewRepository
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed implementation of BrewRepository.
 * Manages reading the current brew status and temperature, and sending brew commands
 * to the connected IoT device via Firebase Realtime Database.
 */
class FirebaseBrewDataSource: BrewRepository {

    // Database reference for the current brewing status ("idle", "brewing", etc.)
    private val statusRef: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child("coffee").child("status")

    // Database reference for the current coffee temperature
    private val tempRef: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child("coffee").child("temperature")

    // Database reference for the "brewNow" command flag (true to start/resume, false to stop/pause)
    private val brewNowRef: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child("coffee").child("command").child("brewNow")

    /**
     * Returns a Flow that emits the current BrewStatus from Firebase.
     * Also implements an offline detection mechanism based on the last update time.
     */
    // --- Observe Brew Status ---
    override fun observeBrewStatus(): Flow<BrewStatus> = callbackFlow {
        // ValueEventListener to track status changes in Firebase
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: "idle"
                // Send the new status to the Flow
                trySend(BrewStatus(status, null, status == "brewing"))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseBrewDataSource", "observeBrewStatus cancelled: ${error.message}")
                close(error.toException())
            }
        }

        statusRef.addValueEventListener(listener)
        // Cleanup: remove the listener and cancel the offline detection job when the Flow collector stops
        awaitClose { statusRef.removeEventListener(listener) }
    }

    /**
     * Returns a Flow that emits the current coffee temperature from Firebase.
     */
    // --- Observe Temperature ---
    override fun observeCoffeeTemperature(): Flow<Double?> = callbackFlow {
        // ValueEventListener to track temperature changes in Firebase
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = snapshot.getValue(Double::class.java)
                // Send the new temperature to the Flow
                trySend(temp)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseBrewDataSource", "observeCoffeeTemperature cancelled: ${error.message}")
                close(error.toException())
            }
        }

        tempRef.addValueEventListener(listener)
        // Cleanup: remove the listener when the Flow collector stops
        awaitClose { tempRef.removeEventListener(listener) }
    }

    /**
     * Sends a command to the device to start or stop the brewing process.
     */
    // --- Send Brew Command (start/stop) ---
    override suspend fun sendBrewCommand(command: BrewCommand): Result<Unit> = try {
        brewNowRef.setValue(command.shouldStart).await() // Sets the "brewNow" flag (true to start/resume, false to stop)
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseBrewDataSource", "sendBrewCommand error: ${e.message}")
        Result.failure(e)
    }

    /**
     * Explicitly sets the "brewNow" command to false to stop brewing.
     */
    override suspend fun resetBrewCommand(): Result<Unit> = try {
        brewNowRef.setValue(false).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("FirebaseBrewDataSource", "resetBrewCommand error: ${e.message}")
        Result.failure(e)
    }

}
