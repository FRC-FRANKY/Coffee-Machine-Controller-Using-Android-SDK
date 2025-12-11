package com.cuppa.iot

import android.app.Application
import android.util.Log
import com.cuppa.iot.utils.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CuppaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        syncSchedulesFromFirebase()
    }

    private fun syncSchedulesFromFirebase() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val db = FirebaseDatabase.getInstance()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (user == null) {
                    Log.w("CuppaApp", "User not logged in — skipping schedule sync.")
                    return@launch
                }

                val ref = db.getReference("users").child(user.uid).child("schedules")
                val snapshot = ref.get().await()

                // ✅ Logs number of active schedules
                val enabled = snapshot.children.count {
                    it.child("enabled").getValue(Boolean::class.java) == true
                }
                Log.i("CuppaApp", "Synced $enabled active schedules for FCM handling.")

            } catch (e: Exception) {
                Log.e("CuppaApp", "Failed to sync schedules", e)
            }
        }
    }
}
