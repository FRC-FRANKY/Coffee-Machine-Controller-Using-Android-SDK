package com.cuppa.iot.ui.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cuppa.iot.R
import com.google.firebase.auth.FirebaseAuth

/**
 * Activity displayed after successful registration, waiting for the user to verify their email.
 * It periodically checks Firebase for the updated email verification status.
 */
class VerificationWatcherActivity : AppCompatActivity() {

    private lateinit var tvMessage: TextView
    private lateinit var tvTips: TextView
    private lateinit var btnResend: Button

    private val auth = FirebaseAuth.getInstance()
    private var resendHandler: Handler? = null
    private var resendCountdown = 60 // 1 minute cooldown

    private val checkHandler = Handler(Looper.getMainLooper())
    private val checkInterval = 5000L // Check every 5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_watcher)

        tvMessage = findViewById(R.id.tvVerificationMessage)
        tvTips = findViewById(R.id.tvTips)
        btnResend = findViewById(R.id.btnResendEmail)

        // Initial UI
        tvMessage.text = getString(R.string.email_verification_title)
        tvTips.text = getString(R.string.verification_tips)

        btnResend.setOnClickListener {
            sendVerificationEmail()
        }

        // Start countdown immediately
        startResendLockCountdown()

        // Start periodic verification check
        startVerificationCheckLoop()
    }

    /**
     * Attempts to resend the verification email using the Firebase API.
     */
    private fun sendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, getString(R.string.toast_resend_success), Toast.LENGTH_SHORT).show()
                startResendLockCountdown() // Restart the cooldown period
            } else {
                Toast.makeText(this, getString(R.string.toast_resend_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Starts a countdown timer (60 seconds) that disables the resend button.
     * When the countdown ends, the button is re-enabled with an animation.
     */
    private fun startResendLockCountdown() {
        btnResend.isEnabled = false
        resendCountdown = 60

        resendHandler?.removeCallbacksAndMessages(null)
        resendHandler = Handler(Looper.getMainLooper())
        resendHandler?.post(object : Runnable {
            override fun run() {
                // Update button text with remaining time
                if (resendCountdown > 0) {
                    btnResend.text = "Resend Verification Email (${resendCountdown}s)"
                    resendCountdown--
                    resendHandler?.postDelayed(this, 1000)
                } else {
                    // Re-enable button and reset text
                    btnResend.text = getString(R.string.resend_verification_email)
                    btnResend.isEnabled = true

                    // Add a subtle animation to draw attention to the re-enabled button
                    btnResend.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(200)
                        .withEndAction {
                            btnResend.animate().scaleX(1f).scaleY(1f).duration = 200
                        }
                        .start()
                }
            }
        })
    }

    /**
     * Starts a loop that periodically reloads the user's Firebase profile
     * to check if the email verification flag has been set to true.
     */
    private fun startVerificationCheckLoop() {
        checkHandler.post(object : Runnable {
            override fun run() {
                // Force Firebase to reload user data from the server
                auth.currentUser?.reload()?.addOnCompleteListener {
                    if (auth.currentUser?.isEmailVerified == true) {
                        navigateToHome() // Verification successful
                    } else {
                        checkHandler.postDelayed(this, checkInterval) // Reschedule the check after the interval
                    }
                }
            }
        })
    }

    /**
     * Navigates the user to the MainActivity and finishes the current activity.
     */
    private fun navigateToHome() {
        Toast.makeText(this, getString(R.string.toast_verified_welcome), Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop all running handlers to prevent memory leaks
        resendHandler?.removeCallbacksAndMessages(null)
        checkHandler.removeCallbacksAndMessages(null)
    }
}
