package com.cuppa.iot.utils

import android.view.View
import androidx.core.content.ContextCompat
import com.cuppa.iot.R
import com.google.android.material.snackbar.Snackbar
import android.view.ViewGroup

class InAppNotificationHelper(private val rootView: View) {

    fun showBrewDone() {
        showSnackbar(
            message = "☕ Coffee is ready!",
            backgroundColor = R.color.primary_brown, // Changed from coffee1
            duration = Snackbar.LENGTH_LONG
        )
    }

    fun showCoffeeGoneCold() {
        showSnackbar(
            message = "❄️ Coffee has gone cold",
            backgroundColor = R.color.light_brown, // Changed from caramel1
            duration = Snackbar.LENGTH_LONG
        )
    }

    fun showOffline() {
        showSnackbar(
            message = "⚠️ ESP32 is offline",
            backgroundColor = R.color.red, // This one's fine
            duration = Snackbar.LENGTH_LONG
        )
    }

    fun showBrewStarted() {
        showSnackbar(
            message = "☕ Brewing started...",
            backgroundColor = R.color.primary_brown, // Changed
            duration = Snackbar.LENGTH_SHORT
        )
    }

    fun showBrewPaused() {
        showSnackbar(
            message = "⏸️ Brewing paused",
            backgroundColor = R.color.text_secondary, // Changed
            duration = Snackbar.LENGTH_SHORT
        )
    }

    fun showBrewResumed() {
        showSnackbar(
            message = "▶️ Brewing resumed",
            backgroundColor = R.color.primary_brown, // Changed
            duration = Snackbar.LENGTH_SHORT
        )
    }

    fun showBrewCancelled() {
        showSnackbar(
            message = "❌ Brewing cancelled",
            backgroundColor = R.color.red, // This one's fine
            duration = Snackbar.LENGTH_SHORT
        )
    }

    private fun showSnackbar(message: String, backgroundColor: Int, duration: Int) {
        val snackbar = Snackbar.make(rootView, message, duration)

        // Add margin to lift it above navbar
        val snackbarView = snackbar.view
        val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
        params.setMargins(0, 0, 0, 115) //(adjust as needed)
        snackbarView.layoutParams = params

        snackbar.view.setBackgroundColor(
            ContextCompat.getColor(rootView.context, backgroundColor)
        )

        snackbar.setTextColor(
            ContextCompat.getColor(rootView.context, R.color.white)
        )

        snackbar.show()
    }
}